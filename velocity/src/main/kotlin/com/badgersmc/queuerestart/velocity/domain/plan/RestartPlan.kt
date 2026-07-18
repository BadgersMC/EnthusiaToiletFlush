package com.badgersmc.queuerestart.velocity.domain.plan

import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import java.time.Instant
import java.util.UUID

enum class PlanType { SERVER, PROXY, NETWORK }

enum class PlanState {
    SCHEDULED,
    COUNTING_DOWN,
    PREFLIGHT,
    TRANSFERRING,
    DISPATCHING,
    COMPLETED,
    CANCELLED,
    FAILED,
    MISSED,
    NEEDS_REVIEW,
}
data class RestartPlan(
    val id: UUID = UUID.randomUUID(),
    val type: PlanType,
    val targets: Set<ServerId>,
    val createdAt: Instant,
    val executionAt: Instant,
    val warningAt: Instant,
    val reason: String = "",
    val creator: String,
    val automaticKey: String? = null,
    val silent: Boolean = false,
    var state: PlanState = PlanState.SCHEDULED,
    val announcedSeconds: MutableSet<Long> = mutableSetOf(),
    val targetResults: MutableMap<String, String> = linkedMapOf(),
    var actionStarted: Boolean = false,
    var maintenanceEnabled: Boolean = false,
    var failure: String = "",
) {
    fun active(): Boolean = state in setOf(
        PlanState.SCHEDULED,
        PlanState.COUNTING_DOWN,
        PlanState.PREFLIGHT,
        PlanState.TRANSFERRING,
        PlanState.DISPATCHING,
    )
}
