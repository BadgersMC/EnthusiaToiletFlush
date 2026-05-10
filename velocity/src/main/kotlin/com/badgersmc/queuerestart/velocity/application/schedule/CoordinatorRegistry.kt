package com.badgersmc.queuerestart.velocity.application.schedule

import com.badgersmc.queuerestart.velocity.domain.coordinator.RestartCoordinator
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import java.util.concurrent.ConcurrentHashMap

/**
 * One [RestartCoordinator] per target server. Lives in application so
 * commands and services share the same instances.
 *
 * Accessed from the proxy tick (orchestrator/ping poller), command threads
 * (arm/cancel via /schedrestart, /qrestart), and the messaging callback
 * thread. ConcurrentHashMap + computeIfAbsent guarantees a single
 * coordinator instance per ServerId under contention.
 */
class CoordinatorRegistry {
    private val byServer = ConcurrentHashMap<ServerId, RestartCoordinator>()

    fun get(server: ServerId): RestartCoordinator =
        byServer.computeIfAbsent(server) { RestartCoordinator(server) }

    fun all(): Map<ServerId, RestartCoordinator> = byServer.toMap()
}
