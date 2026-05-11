package com.badgersmc.queuerestart.paper

import com.badgersmc.queuerestart.common.protocol.CheckHacksResultMessage
import com.badgersmc.queuerestart.common.protocol.Codec
import com.badgersmc.queuerestart.common.protocol.DrainAckMessage
import com.badgersmc.queuerestart.common.protocol.RestartCancelMessage
import com.badgersmc.queuerestart.common.protocol.RestartNowMessage
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.messaging.PluginMessageListener
import java.util.logging.Level

/**
 * REQ-021, implementation.md §6.
 *
 * Inbound: receives `qrestart:v1` plugin messages from the proxy, decodes
 * via [Codec], dispatches [RestartNowMessage] to [RestartExecutor].
 * Other proxy→backend frames (just `DrainRequest` today) are advisory and
 * ignored — the proxy already drains by moving players via the queue API.
 *
 * Outbound: companion-side senders use [sendDrainAck] / [sendCheckHacksResult]
 * which round-trip through any online player (Bukkit requires a Player to
 * pin the channel). On an empty server the call is dropped with a warning.
 */
class ProxyMessageListener(
    private val plugin: Plugin,
    private val executor: RestartExecutor,
    private val codec: Codec = Codec(),
) : PluginMessageListener {

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (channel != CHANNEL) return
        val frame = try {
            codec.decode(message)
        } catch (t: Throwable) {
            plugin.logger.log(Level.WARNING, "queue-restart: malformed inbound frame", t)
            return
        }
        when (frame) {
            is RestartNowMessage -> {
                try {
                    plugin.logger.info(
                        "queue-restart: RestartNow received (mode=${frame.mode}, delaySeconds=${frame.delaySeconds})"
                    )
                    executor.execute(frame.mode, frame.argument, frame.delaySeconds)
                } catch (t: Throwable) {
                    plugin.logger.log(Level.SEVERE, "queue-restart: RestartNow execution failed", t)
                }
            }
            is RestartCancelMessage -> {
                val cancelled = executor.abort()
                plugin.logger.info(
                    if (cancelled) "queue-restart: RestartCancel received — pending shutdown aborted"
                    else "queue-restart: RestartCancel received — no pending shutdown to abort"
                )
            }
            else -> {
                // DrainRequest is advisory; CheckHacksResult/DrainAck are
                // backend→proxy and should never appear on this side.
            }
        }
    }

    fun sendDrainAck(remainingPlayers: Int) {
        send(codec.encode(DrainAckMessage(remainingPlayers)))
    }

    fun sendCheckHacksResult(message: CheckHacksResultMessage) {
        send(codec.encode(message))
    }

    private fun send(payload: ByteArray) {
        val any = plugin.server.onlinePlayers.firstOrNull()
        if (any == null) {
            plugin.logger.warning("queue-restart: cannot send to proxy (no online players)")
            return
        }
        any.sendPluginMessage(plugin, CHANNEL, payload)
    }

    companion object {
        const val CHANNEL: String = "qrestart:v1"
    }
}
