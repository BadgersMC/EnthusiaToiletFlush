package com.badgersmc.queuerestart.paper

import org.bukkit.plugin.Plugin
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.logging.Level

/**
 * Local restart scheduler — fires `Bukkit.shutdown()` (or whatever
 * [shutdown] does in tests) at each configured daily [LocalTime] in
 * [zone].
 *
 * Pattern borrowed from xGinko/ServerRestarts: each backend JVM owns its
 * own clock and shutdown decision. The proxy's countdown + drain runs in
 * parallel using its own mirrored cron — the two sides converge on the
 * same wall time without any cross-process signal.
 *
 * Each fire re-arms the next occurrence (next day) so the schedule loops
 * indefinitely without external nudging.
 *
 * Behaviour validated under T-100; the underlying time math is covered by
 * `NextOccurrenceTest`.
 */
class RestartTimer(
    private val plugin: Plugin,
    private val times: List<LocalTime>,
    private val zone: ZoneId,
    private val shutdown: () -> Unit,
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(zone) },
) {
    private var executor: ScheduledExecutorService? = null

    fun start() {
        if (executor != null) return
        if (times.isEmpty()) {
            plugin.logger.info("queue-restart: no restart-times configured; local timer disabled")
            return
        }
        executor = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "queue-restart-restart-timer").apply { isDaemon = true }
        }
        for (t in times) scheduleNext(t)
    }

    fun stop() {
        executor?.shutdownNow()
        executor = null
    }

    private fun scheduleNext(time: LocalTime) {
        val exec = executor ?: return
        val nextAt = NextOccurrence.compute(now(), time, zone)
        val delayMs = Duration.between(now(), nextAt).toMillis().coerceAtLeast(1)
        plugin.logger.info("queue-restart: next local restart for $time scheduled at $nextAt")
        exec.schedule({
            try {
                plugin.logger.info("queue-restart: local restart trigger firing for $time")
                // Bukkit requires shutdown calls on the main thread.
                plugin.server.scheduler.runTask(plugin, Runnable { shutdown() })
            } catch (t: Throwable) {
                plugin.logger.log(Level.SEVERE, "queue-restart: local restart trigger failed", t)
            } finally {
                scheduleNext(time)
            }
        }, delayMs, TimeUnit.MILLISECONDS)
    }
}
