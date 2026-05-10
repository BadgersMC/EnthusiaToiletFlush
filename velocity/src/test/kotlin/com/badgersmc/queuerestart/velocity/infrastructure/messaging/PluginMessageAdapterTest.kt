package com.badgersmc.queuerestart.velocity.infrastructure.messaging

import com.badgersmc.queuerestart.common.protocol.CheckHacksResultMessage
import com.badgersmc.queuerestart.common.protocol.CheckOutcome
import com.badgersmc.queuerestart.common.protocol.Codec
import com.badgersmc.queuerestart.common.protocol.DrainAckMessage
import com.badgersmc.queuerestart.common.protocol.DrainRequestMessage
import com.badgersmc.queuerestart.common.protocol.RestartMode
import com.badgersmc.queuerestart.common.protocol.RestartNowMessage
import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * implementation.md §6.
 *
 * Verifies adapter encode/decode parity with [Codec] using a fake transport
 * stub — no Velocity API on the test classpath.
 */
class PluginMessageAdapterTest {

    private val codec = Codec()

    private class FakeTransport : PluginMessageTransport {
        data class SentFrame(val target: ServerId, val payload: ByteArray)
        val sent = mutableListOf<SentFrame>()
        override fun send(target: ServerId, payload: ByteArray) {
            sent += SentFrame(target, payload)
        }
    }

    private val target = ServerId("survival")

    @Test
    fun `sendDrainRequest emits codec-encoded DrainRequest frame`() {
        val transport = FakeTransport()
        val adapter = PluginMessageAdapter(transport)

        adapter.sendDrainRequest(target)

        assertThat(transport.sent).hasSize(1)
        assertThat(transport.sent[0].target).isEqualTo(target)
        assertThat(codec.decode(transport.sent[0].payload)).isEqualTo(DrainRequestMessage)
    }

    @Test
    fun `sendRestartNow emits codec-encoded RestartNow frame`() {
        val transport = FakeTransport()
        val adapter = PluginMessageAdapter(transport)

        adapter.sendRestartNow(target, RestartMode.SHUTDOWN, "stop", delaySeconds = 30)

        val decoded = codec.decode(transport.sent.single().payload)
        assertThat(decoded).isEqualTo(RestartNowMessage(RestartMode.SHUTDOWN, "stop", delaySeconds = 30))
    }

    @Test
    fun `inbound DrainAck dispatches to registered handler`() {
        val adapter = PluginMessageAdapter(FakeTransport())
        val received = mutableListOf<Pair<ServerId, Int>>()
        adapter.onDrainAck { server, count -> received += server to count }

        adapter.handleInbound(target, codec.encode(DrainAckMessage(remainingPlayers = 7)))

        assertThat(received).containsExactly(target to 7)
    }

    @Test
    fun `inbound CheckHacksResult dispatches to registered handler`() {
        val adapter = PluginMessageAdapter(FakeTransport())
        val received = mutableListOf<Pair<PlayerId, CheckOutcome>>()
        adapter.onCheckHacksResult { player, outcome -> received += player to outcome }

        val pid = UUID.randomUUID()
        adapter.handleInbound(
            target,
            codec.encode(CheckHacksResultMessage(pid, CheckOutcome.DETECTED)),
        )

        assertThat(received).containsExactly(PlayerId(pid) to CheckOutcome.DETECTED)
    }

    @Test
    fun `inbound message of wrong direction is ignored without throwing`() {
        val adapter = PluginMessageAdapter(FakeTransport())
        val drainAcks = mutableListOf<Pair<ServerId, Int>>()
        adapter.onDrainAck { s, n -> drainAcks += s to n }

        // proxy→backend frame coming back inbound — must not be dispatched
        adapter.handleInbound(target, codec.encode(DrainRequestMessage))

        assertThat(drainAcks).isEmpty()
    }

    @Test
    fun `multiple handlers all receive the inbound message`() {
        val adapter = PluginMessageAdapter(FakeTransport())
        val a = mutableListOf<Int>()
        val b = mutableListOf<Int>()
        adapter.onDrainAck { _, n -> a += n }
        adapter.onDrainAck { _, n -> b += n }

        adapter.handleInbound(target, codec.encode(DrainAckMessage(3)))

        assertThat(a).containsExactly(3)
        assertThat(b).containsExactly(3)
    }

    @Test
    fun `malformed inbound payload is swallowed (logged elsewhere)`() {
        val adapter = PluginMessageAdapter(FakeTransport())
        adapter.onDrainAck { _, _ -> throw AssertionError("must not fire") }

        // unknown type byte — must not propagate
        adapter.handleInbound(target, byteArrayOf(0x7F))
    }
}
