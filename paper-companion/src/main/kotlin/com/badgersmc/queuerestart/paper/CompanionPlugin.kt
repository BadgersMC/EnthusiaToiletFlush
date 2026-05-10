package com.badgersmc.queuerestart.paper

import com.badgersmc.queuerestart.common.schedule.BackendSchedule
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.plugin.EventExecutor
import org.bukkit.plugin.java.JavaPlugin
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.logging.Level

/**
 * Paper companion entrypoint. On `onEnable`:
 *  1. Register incoming + outgoing `qrestart:v1` plugin-message channels.
 *  2. Wire [ProxyMessageListener] over a [BukkitServerControl]-backed
 *     [RestartExecutor].
 *  3. Soft-depend on CheckHacks (REQ-040) — when present, install a typed
 *     reflective listener for `CheckCompletedEvent` and forward results to
 *     the proxy.
 *
 * implementation.md §1, §7.
 */
class CompanionPlugin : JavaPlugin() {

    private lateinit var listener: ProxyMessageListener
    private var restartTimer: RestartTimer? = null
    private var armPoller: ProxyArmPoller? = null
    private val bridge = CheckHacksBridge()

    override fun onEnable() {
        saveDefaultConfig()

        val scheduler = RestartScheduler { delaySeconds, action ->
            // Bukkit ticks at 20 Hz. delay==0 still goes through runTaskLater
            // so the action runs on the main thread regardless of caller.
            server.scheduler.runTaskLater(this, Runnable { action() }, delaySeconds * 20L)
        }
        val executor = RestartExecutor(BukkitServerControl(), scheduler)
        listener = ProxyMessageListener(this, executor)

        server.messenger.registerIncomingPluginChannel(this, ProxyMessageListener.CHANNEL, listener)
        server.messenger.registerOutgoingPluginChannel(this, ProxyMessageListener.CHANNEL)

        installCheckHacksListener()

        startRestartTimer()
        startArmPoller(executor)

        logger.info("queue-restart-companion enabled. checkhacks=${bridge.isCheckHacksAvailable()}")
    }

    /**
     * REQ-022. Start the inverse-SLP poller that pulls pending arms from
     * the proxy. Disabled if `server-id` is missing — the proxy keys its
     * cache by server-id and a misconfigured backend would silently drop
     * any arm. Better to log + skip than fire on the wrong target.
     */
    private fun startArmPoller(executor: RestartExecutor) {
        val serverId = config.getString("server-id").orEmpty().trim()
        if (serverId.isEmpty()) {
            logger.warning("queue-restart: server-id not set in config; arm poller disabled")
            return
        }
        val host = config.getString("proxy-host", "127.0.0.1") ?: "127.0.0.1"
        val port = config.getInt("proxy-port", 25565).coerceIn(1, 65535)
        val every = config.getInt("arm-poll-seconds", 5).coerceAtLeast(1)
        armPoller = ProxyArmPoller(
            plugin = this,
            proxyHost = host,
            proxyPort = port,
            serverId = serverId,
            executor = executor,
            pollIntervalSeconds = every,
        ).also { it.start() }
    }

    override fun onDisable() {
        restartTimer?.stop()
        restartTimer = null
        armPoller?.stop()
        armPoller = null
        server.messenger.unregisterIncomingPluginChannel(this)
        server.messenger.unregisterOutgoingPluginChannel(this)
    }

    /**
     * Read `restart-times` + `time-zone` from config.yml and start the local
     * scheduler. Borrowed from xGinko/ServerRestarts: each backend JVM owns
     * its own restart clock and triggers `Bukkit.shutdown()` directly so
     * panel-hosted servers (no shared filesystem with the proxy, no extra
     * ports) restart on schedule without any cross-process signal.
     */
    private fun startRestartTimer() {
        val zone = parseZone(config.getString("time-zone")) ?: ZoneId.systemDefault().also {
            logger.warning("queue-restart: invalid time-zone in config; falling back to system default $it")
        }
        val raw = config.getStringList("restart-times")
        val times = raw.mapNotNull { parseTime(it) }
        if (raw.size != times.size) {
            logger.warning("queue-restart: ${raw.size - times.size} restart-times entries failed to parse and were ignored")
        }
        val warnMinutes = config.getInt("warn-minutes", 30).coerceAtLeast(0)

        // Always announce the schedule via SLP, even when empty — proxy
        // distinguishes "no times" (no scheduled restart) from "no announce"
        // (cold-start cache miss) by the presence of the marker entry.
        val peerFilter = when (config.getString("schedule-announce-peers")?.lowercase()) {
            "all" -> SchedulePingListener.PeerFilter.ALL
            else -> SchedulePingListener.PeerFilter.PRIVATE_ONLY
        }
        server.pluginManager.registerEvents(
            SchedulePingListener(BackendSchedule(times, zone, warnMinutes), peerFilter),
            this,
        )

        if (times.isEmpty()) {
            logger.info("queue-restart: no restart-times configured; relying on proxy /schedrestart for ad-hoc restarts")
            return
        }
        restartTimer = RestartTimer(
            plugin = this,
            times = times,
            zone = zone,
            shutdown = { server.shutdown() },
        ).also { it.start() }
        logger.info("queue-restart: local restart timer started (${times.size} time(s), zone=$zone, warn=${warnMinutes}m)")
    }

    private fun parseZone(raw: String?): ZoneId? = try {
        raw?.let { ZoneId.of(it) }
    } catch (_: Exception) { null }

    private fun parseTime(raw: String): LocalTime? = try {
        LocalTime.parse(raw.trim())
    } catch (_: DateTimeParseException) {
        logger.warning("queue-restart: cannot parse restart-time '$raw' (expected HH:mm)")
        null
    }

    /**
     * REQ-040. Install a Bukkit listener for `me.branduzzo.checkHacks.api.CheckCompletedEvent`
     * if CheckHacks is present, pulling fields reflectively (the type is not
     * on the compile classpath — see paper-companion/build.gradle.kts).
     * The fork-PR (T-101) replaces this with a typed listener.
     */
    private fun installCheckHacksListener() {
        bridge.installListenerIfAvailable { eventClass ->
            val getPlayerId = eventClass.getMethod("getPlayerId")
            val isClean = runCatching { eventClass.getMethod("isClean") }.getOrNull()
            val isDetected = runCatching { eventClass.getMethod("isDetected") }.getOrNull()
            val isProtected = runCatching { eventClass.getMethod("isProtected") }
                .getOrElse { runCatching { eventClass.getMethod("isProtected_") }.getOrNull() }

            val executor = EventExecutor { _, event ->
                if (!eventClass.isInstance(event)) return@EventExecutor
                try {
                    val pid = getPlayerId.invoke(event) as UUID
                    val clean = (isClean?.invoke(event) as? Boolean) ?: false
                    val detected = (isDetected?.invoke(event) as? Boolean) ?: false
                    val protectedFlag = (isProtected?.invoke(event) as? Boolean) ?: false
                    val msg = bridge.translate(pid, clean, detected, protectedFlag)
                    listener.sendCheckHacksResult(msg)
                } catch (t: Throwable) {
                    logger.log(Level.WARNING, "queue-restart: CheckHacks bridge failure", t)
                }
            }

            @Suppress("UNCHECKED_CAST")
            server.pluginManager.registerEvent(
                eventClass as Class<out org.bukkit.event.Event>,
                EmptyListener,
                EventPriority.MONITOR,
                executor,
                this,
            )
        }
    }

    private object EmptyListener : Listener
}
