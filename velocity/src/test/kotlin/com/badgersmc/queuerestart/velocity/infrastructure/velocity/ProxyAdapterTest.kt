package com.badgersmc.queuerestart.velocity.infrastructure.velocity

import com.badgersmc.queuerestart.common.schedule.BackendSchedule
import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * REQ-014, REQ-031, REQ-033, REQ-034, REQ-043.
 *
 * Verifies the adapter forwards every read query and transfer call verbatim
 * to the underlying [VelocityProxyBackend]. The Velocity-bound binding to
 * the real ProxyServer / Player / RegisteredServer API is a thin shim and
 * is not under test here — covered by the e2e runbook.
 */
class ProxyAdapterTest {

    private class RecordingBackend(
        val online: MutableSet<PlayerId> = mutableSetOf(),
        val perms: MutableMap<PlayerId, Set<String>> = mutableMapOf(),
        val reachable: MutableSet<ServerId> = mutableSetOf(),
        val rosters: MutableMap<ServerId, Set<PlayerId>> = mutableMapOf(),
    ) : VelocityProxyBackend {
        data class TransferCall(val player: PlayerId, val target: ServerId)
        val transfers = mutableListOf<TransferCall>()

        override fun isOnline(playerId: PlayerId) = playerId in online
        override fun permissionsOf(playerId: PlayerId) = perms[playerId] ?: emptySet()
        override fun isReachable(serverId: ServerId) = serverId in reachable
        override fun playersOn(serverId: ServerId) = rosters[serverId] ?: emptySet()
        override fun transferPlayer(playerId: PlayerId, target: ServerId) {
            transfers += TransferCall(playerId, target)
        }
        override fun registeredServerIds(): Set<ServerId> = rosters.keys.toSet()
        override fun pingForSchedule(serverId: ServerId): BackendSchedule? = null
    }

    private fun pid(name: String) = PlayerId(UUID.nameUUIDFromBytes(name.toByteArray()))
    private val survival = ServerId("survival")
    private val hub = ServerId("hub")

    @Test
    fun `isOnline forwards to backend`() {
        val backend = RecordingBackend(online = mutableSetOf(pid("alice")))
        val adapter = ProxyAdapter(backend)

        assertThat(adapter.isOnline(pid("alice"))).isTrue()
        assertThat(adapter.isOnline(pid("bob"))).isFalse()
    }

    @Test
    fun `permissionsOf returns backend's permission set verbatim`() {
        val backend = RecordingBackend(
            perms = mutableMapOf(
                pid("alice") to setOf("queuerestart.bypass.drain", "rank.devotee"),
            ),
        )
        val adapter = ProxyAdapter(backend)

        assertThat(adapter.permissionsOf(pid("alice")))
            .containsExactlyInAnyOrder("queuerestart.bypass.drain", "rank.devotee")
    }

    @Test
    fun `permissionsOf returns empty set when backend has no perms for player`() {
        val backend = RecordingBackend()
        val adapter = ProxyAdapter(backend)

        assertThat(adapter.permissionsOf(pid("ghost"))).isEmpty()
    }

    @Test
    fun `isReachable forwards to backend`() {
        val backend = RecordingBackend(reachable = mutableSetOf(survival))
        val adapter = ProxyAdapter(backend)

        assertThat(adapter.isReachable(survival)).isTrue()
        assertThat(adapter.isReachable(hub)).isFalse()
    }

    @Test
    fun `playersOn returns backend's roster verbatim`() {
        val roster = setOf(pid("alice"), pid("bob"))
        val backend = RecordingBackend(rosters = mutableMapOf(survival to roster))
        val adapter = ProxyAdapter(backend)

        assertThat(adapter.playersOn(survival)).isEqualTo(roster)
        assertThat(adapter.playersOn(hub)).isEmpty()
    }

    @Test
    fun `transferPlayer forwards player and target verbatim`() {
        val backend = RecordingBackend()
        val adapter = ProxyAdapter(backend)

        adapter.transferPlayer(pid("alice"), hub)
        adapter.transferPlayer(pid("bob"), hub)

        assertThat(backend.transfers).containsExactly(
            RecordingBackend.TransferCall(pid("alice"), hub),
            RecordingBackend.TransferCall(pid("bob"), hub),
        )
    }
}
