package com.badgersmc.queuerestart.velocity.application.gate

import com.badgersmc.queuerestart.common.protocol.CheckOutcome
import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * REQ-040, REQ-041, REQ-042, REQ-043.
 *
 * Per-player gate. Holds the player on the hub until a CheckHacks verdict
 * arrives, with a configurable timeout fallback. Bypass perm holders skip
 * the gate entirely.
 */
class CheckGateTest {

    private fun pid(name: String) = PlayerId(UUID.nameUUIDFromBytes(name.toByteArray()))

    private fun gate(timeout: Int = 60, releaseOnTimeout: Boolean = true) =
        CheckGate(timeoutSeconds = timeout, releaseOnTimeout = releaseOnTimeout)

    @Test
    fun `bypass perm releases instantly (REQ-043)`() {
        val g = gate()
        val outcome = g.register(pid("alice"), hasBypass = true, nowSeconds = 0)
        assertThat(outcome).isEqualTo(GateOutcome.RELEASED)
        assertThat(g.isPending(pid("alice"))).isFalse()
    }

    @Test
    fun `register without bypass starts pending`() {
        val g = gate()
        val outcome = g.register(pid("alice"), hasBypass = false, nowSeconds = 0)
        assertThat(outcome).isEqualTo(GateOutcome.PENDING)
        assertThat(g.isPending(pid("alice"))).isTrue()
    }

    @Test
    fun `CLEAN outcome releases (REQ-040)`() {
        val g = gate()
        g.register(pid("alice"), hasBypass = false, nowSeconds = 0)
        val outcome = g.onResult(pid("alice"), CheckOutcome.CLEAN)
        assertThat(outcome).isEqualTo(GateOutcome.RELEASED)
        assertThat(g.isPending(pid("alice"))).isFalse()
    }

    @Test
    fun `PROTECTED outcome releases`() {
        val g = gate()
        g.register(pid("alice"), hasBypass = false, nowSeconds = 0)
        assertThat(g.onResult(pid("alice"), CheckOutcome.PROTECTED))
            .isEqualTo(GateOutcome.RELEASED)
    }

    @Test
    fun `DETECTED outcome drops (REQ-041)`() {
        val g = gate()
        g.register(pid("alice"), hasBypass = false, nowSeconds = 0)
        val outcome = g.onResult(pid("alice"), CheckOutcome.DETECTED)
        assertThat(outcome).isEqualTo(GateOutcome.DROPPED)
        assertThat(g.isPending(pid("alice"))).isFalse()
    }

    @Test
    fun `timeout releases when release-on-timeout is true (REQ-042)`() {
        val g = gate(timeout = 60, releaseOnTimeout = true)
        g.register(pid("alice"), hasBypass = false, nowSeconds = 0)

        assertThat(g.tick(nowSeconds = 30)).isEmpty()
        val expired = g.tick(nowSeconds = 60)

        assertThat(expired).containsExactly(pid("alice") to GateOutcome.RELEASED)
        assertThat(g.isPending(pid("alice"))).isFalse()
    }

    @Test
    fun `timeout drops when release-on-timeout is false (REQ-042)`() {
        val g = gate(timeout = 60, releaseOnTimeout = false)
        g.register(pid("alice"), hasBypass = false, nowSeconds = 0)

        val expired = g.tick(nowSeconds = 61)

        assertThat(expired).containsExactly(pid("alice") to GateOutcome.DROPPED)
    }

    @Test
    fun `result after release is a no-op`() {
        val g = gate()
        g.register(pid("alice"), hasBypass = false, nowSeconds = 0)
        g.onResult(pid("alice"), CheckOutcome.CLEAN)

        // second result for the same player must not re-introduce them
        val outcome = g.onResult(pid("alice"), CheckOutcome.DETECTED)
        assertThat(outcome).isEqualTo(GateOutcome.UNKNOWN)
        assertThat(g.isPending(pid("alice"))).isFalse()
    }

    @Test
    fun `tracks multiple players independently`() {
        val g = gate()
        g.register(pid("alice"), hasBypass = false, nowSeconds = 0)
        g.register(pid("bob"), hasBypass = false, nowSeconds = 0)

        g.onResult(pid("alice"), CheckOutcome.CLEAN)

        assertThat(g.isPending(pid("alice"))).isFalse()
        assertThat(g.isPending(pid("bob"))).isTrue()
    }

    @Test
    fun `tick reports nothing before any deadline elapses`() {
        val g = gate(timeout = 60)
        g.register(pid("alice"), hasBypass = false, nowSeconds = 0)
        assertThat(g.tick(nowSeconds = 59)).isEmpty()
    }

    @Test
    fun `unknown player result is UNKNOWN`() {
        val g = gate()
        assertThat(g.onResult(pid("ghost"), CheckOutcome.CLEAN))
            .isEqualTo(GateOutcome.UNKNOWN)
    }
}
