package com.badgersmc.queuerestart.velocity.infrastructure.velocity

import com.badgersmc.queuerestart.velocity.application.ports.QueuePort
import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId

/**
 * Thin abstraction over CTD's QueueManager. The Velocity-bound impl
 * (`VelocityQueueManagerBackend`, separate file) calls into
 * `com.velocityctd.proxy.queue.QueueManager` — kept untested here so the
 * unit suite stays Velocity-free.
 */
interface QueueManagerBackend {
    fun enqueueWithWeight(server: ServerId, player: PlayerId, weight: Int)
    fun remove(player: PlayerId)
}

/**
 * REQ-032, REQ-033 adapter. Forwards every (server, player, weight) tuple
 * verbatim to the [QueueManagerBackend].
 */
class QueueAdapter(private val backend: QueueManagerBackend) : QueuePort {

    override fun enqueue(serverId: ServerId, playerId: PlayerId, weight: Int) {
        backend.enqueueWithWeight(serverId, playerId, weight)
    }

    override fun remove(playerId: PlayerId) {
        backend.remove(playerId)
    }
}
