package com.badgersmc.queuerestart.velocity.application.drain

import com.badgersmc.queuerestart.common.protocol.CheckOutcome
import com.badgersmc.queuerestart.velocity.application.gate.CheckGate
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
 * REQ-030, REQ-032, REQ-033, REQ-034, REQ-040, REQ-041, REQ-043, REQ-090.
 *
 * Cohort snapshot supplied by RestartCoordinator. RejoinService:
 *  - drops offline members at enqueue time (REQ-034)
 *  - holds non-bypass members at the CheckGate until CheckHacks (REQ-040)
 *  - bypass.checkhacks holders skip the gate (REQ-043)
 *  - DETECTED verdicts are dropped (REQ-041)
 *  - cross-backend source spoofs are rejected (REQ-090 finding B)
 *  - enqueues with rank-resolved weight (REQ-033)
 */
class RejoinServiceTest {

    private val target = ServerId("survival")
    private val hub = ServerId("lobby")

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
        val playerLocations: Map<ServerId, Set<PlayerId>> = emptyMap(),
    ) : ProxyPort {
        override fun isOnline(playerId: PlayerId) = playerId in online
        override fun permissionsOf(playerId: PlayerId) = perms[playerId] ?: emptySet()
        override fun isReachable(serverId: ServerId) = true
        override fun playersOn(serverId: ServerId) = playerLocations[serverId] ?: emptySet()
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

    private fun gate() = CheckGate(timeoutSeconds = 60, releaseOnTimeout = true)

    private fun bypassed(pid: PlayerId, vararg extra: String) =
        setOf("queuerestart.bypass.checkhacks") + extra

    @Test
    fun `bypass holders enqueue immediately with rank weight (REQ-032 REQ-033 REQ-043)`() {
        val cohort = Cohort(setOf(
            CohortMember(pid("owner")),
            CohortMember(pid("mvp")),
            CohortMember(pid("default")),
        ))
        val proxy = FakeProxy(
            online = setOf(pid("owner"), pid("mvp"), pid("default")),
            perms = mapOf(
                pid("owner") to bypassed(pid("owner"), "group.owner"),
                pid("mvp") to bypassed(pid("mvp"), "group.mvp"),
                pid("default") to bypassed(pid("default")),
            ),
        )
        val queue = RecordingQueue()

        RejoinService(proxy, queue, ladder, gate()).enqueueRejoin(target, cohort, nowSeconds = 0)

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
            perms = mapOf(pid("here") to bypassed(pid("here"))),
        )
        val queue = RecordingQueue()

        RejoinService(proxy, queue, ladder, gate())
            .enqueueRejoin(target, cohort, nowSeconds = 0)

        assertThat(queue.entries.map { it.player }).containsExactly(pid("here"))
    }

    @Test
    fun `non-bypass members held at gate until CLEAN verdict (REQ-040)`() {
        val cohort = Cohort(setOf(CohortMember(pid("alice"))))
        val proxy = FakeProxy(
            online = setOf(pid("alice")),
            perms = emptyMap(),
            playerLocations = mapOf(hub to setOf(pid("alice"))),
        )
        val queue = RecordingQueue()
        val svc = RejoinService(proxy, queue, ladder, gate())

        svc.enqueueRejoin(target, cohort, nowSeconds = 0)
        assertThat(queue.entries).isEmpty() // held by gate

        svc.onCheckHacksResult(hub, pid("alice"), CheckOutcome.CLEAN)
        assertThat(queue.entries.map { it.player }).containsExactly(pid("alice"))
    }

    @Test
    fun `DETECTED verdict drops player from rejoin (REQ-041)`() {
        val cohort = Cohort(setOf(CohortMember(pid("cheater"))))
        val proxy = FakeProxy(
            online = setOf(pid("cheater")),
            perms = emptyMap(),
            playerLocations = mapOf(hub to setOf(pid("cheater"))),
        )
        val queue = RecordingQueue()
        val svc = RejoinService(proxy, queue, ladder, gate())

        svc.enqueueRejoin(target, cohort, nowSeconds = 0)
        svc.onCheckHacksResult(hub, pid("cheater"), CheckOutcome.DETECTED)

        assertThat(queue.entries).isEmpty()
    }

    @Test
    fun `cross-backend spoofed verdict is ignored (REQ-090 finding B)`() {
        // Compromised minigame backend forges CLEAN for a player on hub.
        val cohort = Cohort(setOf(CohortMember(pid("alice"))))
        val proxy = FakeProxy(
            online = setOf(pid("alice")),
            perms = emptyMap(),
            playerLocations = mapOf(hub to setOf(pid("alice"))),
        )
        val queue = RecordingQueue()
        val svc = RejoinService(proxy, queue, ladder, gate())

        svc.enqueueRejoin(target, cohort, nowSeconds = 0)
        // Verdict claimed from a backend the player isn't on — must be dropped.
        svc.onCheckHacksResult(ServerId("minigame-x"), pid("alice"), CheckOutcome.CLEAN)

        assertThat(queue.entries).isEmpty()
    }

    @Test
    fun `gate timeout releases pending players when releaseOnTimeout`() {
        val cohort = Cohort(setOf(CohortMember(pid("alice"))))
        val proxy = FakeProxy(
            online = setOf(pid("alice")),
            perms = emptyMap(),
            playerLocations = mapOf(hub to setOf(pid("alice"))),
        )
        val queue = RecordingQueue()
        val svc = RejoinService(proxy, queue, ladder, gate())

        svc.enqueueRejoin(target, cohort, nowSeconds = 0)
        svc.tick(nowSeconds = 70) // > 60s gate timeout

        assertThat(queue.entries.map { it.player }).containsExactly(pid("alice"))
    }
}
