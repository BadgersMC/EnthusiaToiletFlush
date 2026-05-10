package com.badgersmc.queuerestart.velocity.application.drain

import com.badgersmc.queuerestart.velocity.application.ports.ProxyPort
import com.badgersmc.queuerestart.velocity.application.ports.QueuePort
import com.badgersmc.queuerestart.velocity.domain.cohort.Cohort
import com.badgersmc.queuerestart.velocity.domain.cohort.CohortMember
import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import com.badgersmc.queuerestart.velocity.domain.rank.RankLadder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * REQ-030, REQ-032, REQ-033, REQ-034.
 *
 * Cohort snapshot supplied by RestartCoordinator. RejoinService:
 *  - drops offline members at enqueue time (REQ-034)
 *  - enqueues each remaining member into the queue for the target server
 *    using the rank-resolved weight (REQ-033)
 */
class RejoinServiceTest {

    private val target = ServerId("survival")

    private fun pid(name: String) = PlayerId(UUID.nameUUIDFromBytes(name.toByteArray()))

    private val ladder = RankLadder(
        entries = linkedMapOf(
            "group.owner" to 1000,
            "group.mvp" to 300,
            "group.vip" to 100,
        ),
        default = 0,
    )

    private class FakeProxy(
        val online: Set<PlayerId>,
        val perms: Map<PlayerId, Set<String>>,
    ) : ProxyPort {
        override fun isOnline(playerId: PlayerId) = playerId in online
        override fun permissionsOf(playerId: PlayerId) = perms[playerId] ?: emptySet()
        override fun isReachable(serverId: ServerId) = true
        override fun playersOn(serverId: ServerId) = emptySet<PlayerId>()
        override fun transferPlayer(playerId: PlayerId, target: ServerId) {}
        override fun registeredServerIds(): Set<ServerId> = emptySet()
        override fun pingForSchedule(serverId: ServerId): com.badgersmc.queuerestart.common.schedule.BackendSchedule? = null
    }

    private class RecordingQueue : QueuePort {
        data class Entry(val server: ServerId, val player: PlayerId, val weight: Int)
        val entries = mutableListOf<Entry>()
        override fun enqueue(serverId: ServerId, playerId: PlayerId, weight: Int) {
            entries += Entry(serverId, playerId, weight)
        }
        override fun remove(playerId: PlayerId) {}
    }

    @Test
    fun `enqueues every online cohort member with rank weight (REQ-032 REQ-033)`() {
        val cohort = Cohort(setOf(
            CohortMember(pid("owner")),
            CohortMember(pid("mvp")),
            CohortMember(pid("default")),
        ))
        val proxy = FakeProxy(
            online = setOf(pid("owner"), pid("mvp"), pid("default")),
            perms = mapOf(
                pid("owner") to setOf("group.owner"),
                pid("mvp") to setOf("group.mvp"),
                pid("default") to emptySet(),
            ),
        )
        val queue = RecordingQueue()

        RejoinService(proxy, queue, ladder).enqueueRejoin(target, cohort)

        assertThat(queue.entries).containsExactlyInAnyOrder(
            RecordingQueue.Entry(target, pid("owner"), 1000),
            RecordingQueue.Entry(target, pid("mvp"), 300),
            RecordingQueue.Entry(target, pid("default"), 0),
        )
    }

    @Test
    fun `offline cohort members are not enqueued (REQ-034)`() {
        val cohort = Cohort(setOf(
            CohortMember(pid("here")),
            CohortMember(pid("gone")),
        ))
        val proxy = FakeProxy(
            online = setOf(pid("here")),
            perms = emptyMap(),
        )
        val queue = RecordingQueue()

        RejoinService(proxy, queue, ladder).enqueueRejoin(target, cohort)

        assertThat(queue.entries.map { it.player }).containsExactly(pid("here"))
    }

    @Test
    fun `empty cohort enqueues nothing`() {
        val queue = RecordingQueue()
        RejoinService(FakeProxy(emptySet(), emptyMap()), queue, ladder)
            .enqueueRejoin(target, Cohort(emptySet()))
        assertThat(queue.entries).isEmpty()
    }

    @Test
    fun `default weight applied when player has no matching permission`() {
        val cohort = Cohort(setOf(CohortMember(pid("nobody"))))
        val proxy = FakeProxy(
            online = setOf(pid("nobody")),
            perms = mapOf(pid("nobody") to setOf("unrelated.perm")),
        )
        val queue = RecordingQueue()

        RejoinService(proxy, queue, ladder).enqueueRejoin(target, cohort)

        assertThat(queue.entries).hasSize(1)
        assertThat(queue.entries[0].weight).isEqualTo(0)
    }
}
