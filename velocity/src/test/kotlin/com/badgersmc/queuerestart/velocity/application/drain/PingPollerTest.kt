package com.badgersmc.queuerestart.velocity.application.drain

import com.badgersmc.queuerestart.velocity.application.ports.ProxyPort
import com.badgersmc.queuerestart.velocity.application.schedule.CoordinatorRegistry
import com.badgersmc.queuerestart.velocity.domain.cohort.Cohort
import com.badgersmc.queuerestart.velocity.domain.coordinator.RestartState
import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * REQ-031, REQ-032.
 *
 * For each coordinator in RESTART_SENT: transition to SERVER_DOWN once,
 * then poll `ProxyPort.isReachable(target)` at `ping-poll-seconds`. On
 * first success transition to REJOIN_RELEASE and call back into the
 * orchestrator to finish the rejoin enqueue.
 */
class PingPollerTest {

    private val survival = ServerId("survival")

    private class FakeProxy(var reachable: Boolean) : ProxyPort {
        override fun isOnline(playerId: PlayerId) = true
        override fun permissionsOf(playerId: PlayerId) = emptySet<String>()
        override fun isReachable(serverId: ServerId) = reachable
        override fun playersOn(serverId: ServerId) = emptySet<PlayerId>()
        override fun transferPlayer(playerId: PlayerId, target: ServerId) {}
        override fun registeredServerIds(): Set<ServerId> = setOf(ServerId("survival"))
        override fun pingForSchedule(serverId: ServerId): com.badgersmc.queuerestart.common.schedule.BackendSchedule? = null
    }

    private fun primeRestartSent(registry: CoordinatorRegistry) {
        val coord = registry.get(survival)
        coord.arm(Cohort(emptySet()), 60)
        coord.beginCountdown()
        coord.beginDrain()
        coord.restartSent()
    }

    @Test
    fun `RESTART_SENT transitions to SERVER_DOWN on first tick`() {
        val registry = CoordinatorRegistry()
        primeRestartSent(registry)
        val proxy = FakeProxy(reachable = false)
        val finished = mutableListOf<ServerId>()

        val poller = PingPoller(registry, proxy, finished::add, pingPollSeconds = 3)
        poller.tick(Instant.parse("2026-01-01T00:00:00Z"))

        assertThat(registry.get(survival).state).isEqualTo(RestartState.SERVER_DOWN)
    }

    @Test
    fun `SERVER_DOWN with successful ping transitions to REJOIN_RELEASE and finishes rejoin (REQ-032)`() {
        val registry = CoordinatorRegistry()
        primeRestartSent(registry)
        val proxy = FakeProxy(reachable = false)
        val finished = mutableListOf<ServerId>()

        val poller = PingPoller(registry, proxy, finished::add, pingPollSeconds = 3)
        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        poller.tick(t0) // RESTART_SENT → SERVER_DOWN

        proxy.reachable = true
        poller.tick(t0.plusSeconds(3)) // poll interval elapsed → ping ok → REJOIN_RELEASE
        assertThat(finished).containsExactly(survival)
    }

    @Test
    fun `SERVER_DOWN with failed ping stays SERVER_DOWN`() {
        val registry = CoordinatorRegistry()
        primeRestartSent(registry)
        val proxy = FakeProxy(reachable = false)
        val finished = mutableListOf<ServerId>()
        val poller = PingPoller(registry, proxy, finished::add, pingPollSeconds = 3)

        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        poller.tick(t0)
        poller.tick(t0.plusSeconds(3))
        poller.tick(t0.plusSeconds(6))

        assertThat(registry.get(survival).state).isEqualTo(RestartState.SERVER_DOWN)
        assertThat(finished).isEmpty()
    }

    @Test
    fun `ping interval is respected — does not poll faster than configured`() {
        val registry = CoordinatorRegistry()
        primeRestartSent(registry)
        val proxy = FakeProxy(reachable = true)
        val finished = mutableListOf<ServerId>()
        val poller = PingPoller(registry, proxy, finished::add, pingPollSeconds = 5)

        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        poller.tick(t0) // → SERVER_DOWN
        poller.tick(t0.plusSeconds(2)) // before interval — must not promote

        assertThat(registry.get(survival).state).isEqualTo(RestartState.SERVER_DOWN)

        poller.tick(t0.plusSeconds(5)) // interval elapsed — promote
        assertThat(finished).containsExactly(survival)
    }

    @Test
    fun `coordinators in other states are ignored`() {
        val registry = CoordinatorRegistry()
        // IDLE coord
        val proxy = FakeProxy(reachable = true)
        val finished = mutableListOf<ServerId>()
        val poller = PingPoller(registry, proxy, finished::add, pingPollSeconds = 3)

        registry.get(survival) // creates in IDLE
        poller.tick(Instant.parse("2026-01-01T00:00:00Z"))

        assertThat(registry.get(survival).state).isEqualTo(RestartState.IDLE)
        assertThat(finished).isEmpty()
    }
}
