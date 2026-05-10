package com.badgersmc.queuerestart.velocity.application.drain

import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * REQ-010, REQ-011, REQ-014.
 *
 * Pure planning: produces ordered batches honoring batch-size, drain-order,
 * and the `queuerestart.bypass.drain` exclusion. Time spacing
 * (`batch-interval-ticks`) is the infrastructure layer's responsibility.
 */
class DrainPlannerTest {

    private val planner = DrainPlanner()

    private fun pid(name: String): PlayerId =
        PlayerId(UUID.nameUUIDFromBytes(name.toByteArray()))

    private fun cand(name: String, weight: Int, bypass: Boolean = false) =
        DrainCandidate(pid(name), weight, bypass)

    @Test
    fun `bypass perm holders are excluded from drain (REQ-014)`() {
        val plan = planner.plan(
            candidates = listOf(
                cand("alice", 100),
                cand("bob", 200, bypass = true),
                cand("carol", 50),
            ),
            order = DrainOrder.PRIORITY_ASC,
            batchSize = 10,
        )
        val ids = plan.flatten()
        assertThat(ids).doesNotContain(pid("bob"))
        assertThat(ids).contains(pid("alice"), pid("carol"))
    }

    @Test
    fun `priority asc orders low-weight first (REQ-011)`() {
        val plan = planner.plan(
            candidates = listOf(
                cand("owner", 1000),
                cand("vip", 100),
                cand("default", 0),
                cand("mvp", 300),
            ),
            order = DrainOrder.PRIORITY_ASC,
            batchSize = 10,
        )
        assertThat(plan.flatten()).containsExactly(
            pid("default"), pid("vip"), pid("mvp"), pid("owner"),
        )
    }

    @Test
    fun `priority desc orders high-weight first`() {
        val plan = planner.plan(
            candidates = listOf(
                cand("owner", 1000),
                cand("vip", 100),
                cand("default", 0),
                cand("mvp", 300),
            ),
            order = DrainOrder.PRIORITY_DESC,
            batchSize = 10,
        )
        assertThat(plan.flatten()).containsExactly(
            pid("owner"), pid("mvp"), pid("vip"), pid("default"),
        )
    }

    @Test
    fun `batches honor batch-size with smaller tail`() {
        val cands = (1..7).map { cand("p$it", it * 10) }
        val plan = planner.plan(cands, DrainOrder.PRIORITY_ASC, batchSize = 3)
        assertThat(plan).hasSize(3)
        assertThat(plan[0]).hasSize(3)
        assertThat(plan[1]).hasSize(3)
        assertThat(plan[2]).hasSize(1)
    }

    @Test
    fun `empty candidate list yields empty plan`() {
        assertThat(planner.plan(emptyList(), DrainOrder.PRIORITY_ASC, batchSize = 10)).isEmpty()
    }

    @Test
    fun `all bypass yields empty plan`() {
        val plan = planner.plan(
            candidates = listOf(
                cand("a", 10, bypass = true),
                cand("b", 20, bypass = true),
            ),
            order = DrainOrder.PRIORITY_ASC,
            batchSize = 10,
        )
        assertThat(plan).isEmpty()
    }

    @Test
    fun `equal weights preserve insertion order (stable)`() {
        val plan = planner.plan(
            candidates = listOf(
                cand("first", 100),
                cand("second", 100),
                cand("third", 100),
            ),
            order = DrainOrder.PRIORITY_ASC,
            batchSize = 10,
        )
        assertThat(plan.flatten()).containsExactly(
            pid("first"), pid("second"), pid("third"),
        )
    }

    @Test
    fun `non-positive batch size is rejected`() {
        assertThatThrownBy {
            planner.plan(listOf(cand("a", 1)), DrainOrder.PRIORITY_ASC, batchSize = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            planner.plan(listOf(cand("a", 1)), DrainOrder.PRIORITY_ASC, batchSize = -1)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
