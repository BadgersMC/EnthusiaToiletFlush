package com.badgersmc.queuerestart.velocity.application.drain

import com.badgersmc.queuerestart.velocity.application.ports.ProxyPort
import com.badgersmc.queuerestart.velocity.application.schedule.CoordinatorRegistry
import com.badgersmc.queuerestart.velocity.domain.coordinator.RestartState
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import java.time.Duration
import java.time.Instant

/**
 * REQ-031, REQ-032.
 *
 * Drives the post-restart half of the state cycle:
 *  - on tick: RESTART_SENT → SERVER_DOWN (one shot)
 *  - while SERVER_DOWN, poll `ProxyPort.isReachable` at
 *    `pingPollSeconds`; on first true transition to REJOIN_RELEASE and
 *    invoke [onReady] (the orchestrator's finishRejoin).
 */
class PingPoller(
    private val registry: CoordinatorRegistry,
    private val proxy: ProxyPort,
    private val onReady: (ServerId) -> Unit,
    private val pingPollSeconds: Int,
) {

    private val lastPingAt = mutableMapOf<ServerId, Instant>()

    fun tick(now: Instant) {
        for ((target, coord) in registry.all()) {
            when (coord.state) {
                RestartState.RESTART_SENT -> {
                    coord.serverDown()
                    lastPingAt[target] = now
                }
                RestartState.SERVER_DOWN -> {
                    val last = lastPingAt[target] ?: now
                    if (Duration.between(last, now).seconds < pingPollSeconds) continue
                    lastPingAt[target] = now
                    if (proxy.isReachable(target)) {
                        coord.serverUp()
                        onReady(target)
                    }
                }
                else -> {}
            }
        }
    }
}
