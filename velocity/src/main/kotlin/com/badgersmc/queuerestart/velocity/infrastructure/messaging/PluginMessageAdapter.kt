package com.badgersmc.queuerestart.velocity.infrastructure.messaging

import com.badgersmc.queuerestart.common.protocol.CheckHacksResultMessage
import com.badgersmc.queuerestart.common.protocol.CheckOutcome
import com.badgersmc.queuerestart.common.protocol.Codec
import com.badgersmc.queuerestart.common.protocol.DrainAckMessage
import com.badgersmc.queuerestart.common.protocol.DrainRequestMessage
import com.badgersmc.queuerestart.common.protocol.RestartMode
import com.badgersmc.queuerestart.common.protocol.RestartNowMessage
import com.badgersmc.queuerestart.velocity.application.ports.MessagingPort
import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId

/**
 * Pure send/receive abstraction. The Velocity-bound implementation
 * (`VelocityChannelTransport`, kept thin so it stays untested here) calls
 * `RegisteredServer.sendPluginMessage(channelIdentifier, payload)`.
 */
fun interface PluginMessageTransport {
    fun send(target: ServerId, payload: ByteArray)
}

/**
 * Adapter for [MessagingPort]. Encodes outbound messages via [Codec] and
 * dispatches inbound bytes to registered handlers. Velocity binding lives
 * in [VelocityChannelTransport] (separate file) — this class is fully
 * testable without the Velocity API on the classpath.
 *
 * implementation.md §6.
 */
class PluginMessageAdapter(
    private val transport: PluginMessageTransport,
    private val codec: Codec = Codec(),
) : MessagingPort {

    private val drainAckHandlers = mutableListOf<(ServerId, Int) -> Unit>()
    private val checkResultHandlers = mutableListOf<(PlayerId, CheckOutcome) -> Unit>()

    override fun sendDrainRequest(target: ServerId) {
        transport.send(target, codec.encode(DrainRequestMessage))
    }

    override fun sendRestartNow(target: ServerId, mode: RestartMode, argument: String, delaySeconds: Int) {
        transport.send(target, codec.encode(RestartNowMessage(mode, argument, delaySeconds)))
    }

    override fun onDrainAck(handler: (ServerId, Int) -> Unit) {
        drainAckHandlers += handler
    }

    override fun onCheckHacksResult(handler: (PlayerId, CheckOutcome) -> Unit) {
        checkResultHandlers += handler
    }

    /**
     * Entry point for inbound bytes from any backend. Malformed frames are
     * swallowed — the Velocity-bound transport is responsible for logging.
     */
    fun handleInbound(source: ServerId, payload: ByteArray) {
        val message = try {
            codec.decode(payload)
        } catch (_: IllegalArgumentException) {
            return
        }
        when (message) {
            is DrainAckMessage -> drainAckHandlers.forEach { it(source, message.remainingPlayers) }
            is CheckHacksResultMessage -> checkResultHandlers.forEach {
                it(PlayerId(message.playerId), message.outcome)
            }
            else -> { /* proxy→backend frames travelling the wrong way — ignore */ }
        }
    }
}
