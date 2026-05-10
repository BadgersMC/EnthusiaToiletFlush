package com.badgersmc.queuerestart.velocity.domain.cohort

import com.badgersmc.queuerestart.velocity.domain.id.PlayerId

/** A player snapshotted at arm-time who should be re-queued after restart. */
data class CohortMember(val playerId: PlayerId)

/**
 * Snapshot of players present on a target server when a restart is armed.
 * REQ-030. Immutable; the rejoin pipeline filters offline members at
 * enqueue time (REQ-034) without mutating the original cohort.
 */
data class Cohort(val members: Set<CohortMember>) {
    val size: Int get() = members.size
    val isEmpty: Boolean get() = members.isEmpty()
}
