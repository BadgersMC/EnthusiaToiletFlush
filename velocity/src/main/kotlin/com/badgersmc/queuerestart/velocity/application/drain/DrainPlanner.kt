package com.badgersmc.queuerestart.velocity.application.drain

import com.badgersmc.queuerestart.velocity.domain.id.PlayerId

/** Ordering policy from `drain-order` config. */
enum class DrainOrder {
    /** Low rank weight leaves first. */
    PRIORITY_ASC,

    /** High rank weight leaves first. */
    PRIORITY_DESC,
}

/**
 * One player considered for drain. The application layer constructs these
 * by combining the cohort with rank-resolved weights and bypass-perm flags
 * sourced from the proxy adapter.
 */
data class DrainCandidate(
    val playerId: PlayerId,
    val weight: Int,
    val bypassDrain: Boolean,
)

/**
 * REQ-010, REQ-011, REQ-014.
 *
 * Pure planning: produces ordered batches. The actual time spacing between
 * batches (`batch-interval-ticks`) is the infrastructure layer's job — this
 * planner only decides who goes in which batch and in what order.
 */
class DrainPlanner {

    fun plan(
        candidates: List<DrainCandidate>,
        order: DrainOrder,
        batchSize: Int,
    ): List<List<PlayerId>> {
        require(batchSize > 0) { "batchSize must be > 0, got $batchSize" }

        val included = candidates.filterNot { it.bypassDrain }
        if (included.isEmpty()) return emptyList()

        // Stable sort: equal-weight candidates retain insertion order.
        val ordered = when (order) {
            DrainOrder.PRIORITY_ASC -> included.sortedBy { it.weight }
            DrainOrder.PRIORITY_DESC -> included.sortedByDescending { it.weight }
        }

        return ordered.map { it.playerId }.chunked(batchSize)
    }
}
