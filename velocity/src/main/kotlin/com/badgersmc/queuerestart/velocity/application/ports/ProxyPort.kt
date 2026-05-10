package com.badgersmc.queuerestart.velocity.application.ports

import com.badgersmc.queuerestart.common.schedule.BackendSchedule
import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId

/**
 * Outbound port — read-only proxy state. Implemented by an adapter against
 * the Velocity API in `infrastructure/velocity/`.
 */
interface ProxyPort {
    /** True iff the player is currently connected to any backend. */
    fun isOnline(playerId: PlayerId): Boolean

    /** Permission nodes the player effectively holds (LuckPerms-resolved). */
    fun permissionsOf(playerId: PlayerId): Set<String>

    /** True iff the named backend is registered and reachable (ping). */
    fun isReachable(serverId: ServerId): Boolean

    /** Players currently connected to the named backend. */
    fun playersOn(serverId: ServerId): Set<PlayerId>

    /** Issue a transfer request to send [playerId] to [target]. */
    fun transferPlayer(playerId: PlayerId, target: ServerId)

    /** Names of every backend registered with the proxy. */
    fun registeredServerIds(): Set<ServerId>

    /**
     * Open a Server-List-Ping to [serverId] and return the [BackendSchedule]
     * encoded by the companion in a sample-player entry, if any. Returns
     * `null` if the ping fails or the backend has no schedule sample. Used
     * by `ScheduleDiscoveryPoller` to learn each backend's restart cadence
     * without a player connection.
     */
    fun pingForSchedule(serverId: ServerId): BackendSchedule?
}
