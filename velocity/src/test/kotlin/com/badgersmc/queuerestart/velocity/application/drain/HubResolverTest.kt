package com.badgersmc.queuerestart.velocity.application.drain

import com.badgersmc.queuerestart.velocity.application.ports.ProxyPort
import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * REQ-013. Iterates `fallback-hubs` in order; first reachable wins; null
 * when nothing is reachable.
 */
class HubResolverTest {

    private class FakeProxy(private val reachable: Set<ServerId>) : ProxyPort {
        override fun isOnline(playerId: PlayerId) = false
        override fun permissionsOf(playerId: PlayerId) = emptySet<String>()
        override fun isReachable(serverId: ServerId) = serverId in reachable
        override fun playersOn(serverId: ServerId) = emptySet<PlayerId>()
        override fun transferPlayer(playerId: PlayerId, target: ServerId) {}
        override fun registeredServerIds(): Set<ServerId> = reachable
        override fun pingForSchedule(serverId: ServerId): com.badgersmc.queuerestart.common.schedule.BackendSchedule? = null
    }

    private val primary = ServerId("lobby")
    private val fb1 = ServerId("lobby2")
    private val fb2 = ServerId("lobby3")

    @Test
    fun `primary returned when reachable`() {
        val resolver = HubResolver(FakeProxy(setOf(primary, fb1, fb2)))
        assertThat(resolver.resolve(primary, listOf(fb1, fb2))).isEqualTo(primary)
    }

    @Test
    fun `first fallback returned when primary unreachable`() {
        val resolver = HubResolver(FakeProxy(setOf(fb1, fb2)))
        assertThat(resolver.resolve(primary, listOf(fb1, fb2))).isEqualTo(fb1)
    }

    @Test
    fun `iterates fallbacks in declared order`() {
        val resolver = HubResolver(FakeProxy(setOf(fb2)))
        assertThat(resolver.resolve(primary, listOf(fb1, fb2))).isEqualTo(fb2)
    }

    @Test
    fun `null when nothing is reachable`() {
        val resolver = HubResolver(FakeProxy(emptySet()))
        assertThat(resolver.resolve(primary, listOf(fb1, fb2))).isNull()
    }

    @Test
    fun `no fallbacks and primary reachable returns primary`() {
        val resolver = HubResolver(FakeProxy(setOf(primary)))
        assertThat(resolver.resolve(primary, emptyList())).isEqualTo(primary)
    }

    @Test
    fun `no fallbacks and primary unreachable returns null`() {
        val resolver = HubResolver(FakeProxy(emptySet()))
        assertThat(resolver.resolve(primary, emptyList())).isNull()
    }
}
