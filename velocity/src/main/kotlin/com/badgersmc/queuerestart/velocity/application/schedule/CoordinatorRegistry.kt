package com.badgersmc.queuerestart.velocity.application.schedule

import com.badgersmc.queuerestart.velocity.domain.coordinator.RestartCoordinator
import com.badgersmc.queuerestart.velocity.domain.id.ServerId

/**
 * One [RestartCoordinator] per target server. Lives in application so
 * commands and services share the same instances.
 */
class CoordinatorRegistry {
    private val byServer = mutableMapOf<ServerId, RestartCoordinator>()

    fun get(server: ServerId): RestartCoordinator =
        byServer.getOrPut(server) { RestartCoordinator(server) }

    fun all(): Map<ServerId, RestartCoordinator> = byServer.toMap()
}
