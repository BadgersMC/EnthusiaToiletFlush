package com.badgersmc.queuerestart.velocity.infrastructure.velocity

import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * REQ-032, REQ-033.
 *
 * Verifies the adapter forwards every enqueue (server, player, weight) tuple
 * to the underlying CTD QueueManager and preserves the injected weight 1:1.
 * The Velocity-bound binding to the real QueueManager is a thin shim and is
 * not under test here.
 */
class QueueAdapterTest {

    private class RecordingBackend : QueueManagerBackend {
        data class EnqueueCall(val server: ServerId, val player: PlayerId, val weight: Int)
        val enqueues = mutableListOf<EnqueueCall>()
        val removals = mutableListOf<PlayerId>()
        override fun enqueueWithWeight(server: ServerId, player: PlayerId, weight: Int) {
            enqueues += EnqueueCall(server, player, weight)
        }
        override fun remove(player: PlayerId) {
            removals += player
        }
    }

    private fun pid(name: String) = PlayerId(UUID.nameUUIDFromBytes(name.toByteArray()))
    private val server = ServerId("survival")

    @Test
    fun `enqueue forwards server player and weight verbatim`() {
        val backend = RecordingBackend()
        val adapter = QueueAdapter(backend)

        adapter.enqueue(server, pid("alice"), 500)

        assertThat(backend.enqueues).containsExactly(
            RecordingBackend.EnqueueCall(server, pid("alice"), 500),
        )
    }

    @Test
    fun `enqueue ordering matches injected weights when called in sequence`() {
        val backend = RecordingBackend()
        val adapter = QueueAdapter(backend)

        adapter.enqueue(server, pid("low"), 0)
        adapter.enqueue(server, pid("mid"), 300)
        adapter.enqueue(server, pid("high"), 1000)

        assertThat(backend.enqueues.map { it.weight }).containsExactly(0, 300, 1000)
        assertThat(backend.enqueues.map { it.player })
            .containsExactly(pid("low"), pid("mid"), pid("high"))
    }

    @Test
    fun `remove forwards player to backend`() {
        val backend = RecordingBackend()
        val adapter = QueueAdapter(backend)

        adapter.remove(pid("alice"))

        assertThat(backend.removals).containsExactly(pid("alice"))
    }
}
