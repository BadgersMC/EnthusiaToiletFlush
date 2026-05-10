package com.badgersmc.queuerestart.velocity.domain.rank

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * REQ-033, implementation.md §4 domain/rank.
 *
 * Highest-weight matching permission wins; missing → default; ties stable
 * (first declaration in the ladder wins).
 */
class RankLadderTest {

    private val ladder = RankLadder(
        entries = linkedMapOf(
            "group.owner" to 1000,
            "group.admin" to 900,
            "group.mvp+" to 500,
            "group.mvp" to 300,
            "group.vip+" to 150,
            "group.vip" to 100,
        ),
        default = 0,
    )

    @Test
    fun `single matching permission resolves to its weight`() {
        assertThat(ladder.resolve(setOf("group.mvp"))).isEqualTo(300)
    }

    @Test
    fun `multiple matching permissions resolve to highest weight`() {
        assertThat(ladder.resolve(setOf("group.vip", "group.mvp+", "group.vip+")))
            .isEqualTo(500)
    }

    @Test
    fun `no matching permissions resolves to default`() {
        assertThat(ladder.resolve(setOf("group.unknown", "other.perm"))).isEqualTo(0)
    }

    @Test
    fun `empty permission set resolves to default`() {
        assertThat(ladder.resolve(emptySet())).isEqualTo(0)
    }

    @Test
    fun `tie at same weight resolves stably to first declared`() {
        val tied = RankLadder(
            entries = linkedMapOf(
                "group.alpha" to 500,
                "group.beta" to 500,
                "group.gamma" to 500,
            ),
            default = 0,
        )
        // both alpha and beta tie — alpha is declared first, weight is the
        // same so the value should still be 500 regardless, but exposing the
        // *name* of the resolved match must be alpha.
        val match = tied.resolveEntry(setOf("group.gamma", "group.beta", "group.alpha"))
        assertThat(match?.permission).isEqualTo("group.alpha")
        assertThat(match?.weight).isEqualTo(500)
    }

    @Test
    fun `resolveEntry returns null when no permission matches`() {
        assertThat(ladder.resolveEntry(setOf("nope"))).isNull()
    }

    @Test
    fun `default is configurable`() {
        val custom = RankLadder(
            entries = linkedMapOf("group.vip" to 100),
            default = 42,
        )
        assertThat(custom.resolve(emptySet())).isEqualTo(42)
    }
}
