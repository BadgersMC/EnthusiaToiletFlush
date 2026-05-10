package com.badgersmc.queuerestart.velocity.application.ports

import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId

/**
 * Outbound port — VelocityCTD queue manager. Adapter lives at
 * `infrastructure/velocity/QueueAdapter`.
 */
interface QueuePort {
    /** Enqueue [playerId] for [serverId] with the supplied [weight]. */
    fun enqueue(serverId: ServerId, playerId: PlayerId, weight: Int)

    /** Remove [playerId] from any queue they're currently in. */
    fun remove(playerId: PlayerId)
}
