package com.badgersmc.queuerestart.velocity.domain.countdown

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * REQ-003, REQ-004.
 *
 * `CountdownSchedule` decides whether a given seconds-remaining value is
 * a configured mark — i.e. when chat broadcast + sound cue should fire.
 * T-0 is always a mark even if the operator omits it from config.
 */
class CountdownScheduleTest {

    @Test
    fun `T-0 always fires even when not in configured marks`() {
        val schedule = CountdownSchedule(listOf(60, 30, 10))
        assertThat(schedule.fireAt(0)).isEqualTo(MarkSecond(0))
    }

    @Test
    fun `configured marks fire`() {
        val schedule = CountdownSchedule(listOf(60, 30, 10, 5, 1))
        for (m in listOf(60, 30, 10, 5, 1)) {
            assertThat(schedule.fireAt(m)).isEqualTo(MarkSecond(m))
        }
    }

    @Test
    fun `non-configured seconds do not fire`() {
        val schedule = CountdownSchedule(listOf(60, 30, 10))
        for (s in listOf(59, 31, 11, 9, 2)) {
            assertThat(schedule.fireAt(s)).isNull()
        }
    }

    @Test
    fun `driving the full countdown emits exactly configured marks plus T-0 in order`() {
        val schedule = CountdownSchedule(listOf(60, 30, 10, 5, 4, 3, 2, 1))
        val fired = (60 downTo 0)
            .mapNotNull { schedule.fireAt(it) }
            .map { it.secondsRemaining }

        assertThat(fired).containsExactly(60, 30, 10, 5, 4, 3, 2, 1, 0)
    }

    @Test
    fun `duplicates in input are deduped`() {
        val schedule = CountdownSchedule(listOf(60, 60, 30, 30))
        assertThat(schedule.configuredMarks.map { it.secondsRemaining })
            .containsExactly(60, 30, 0)
    }

    @Test
    fun `marks are exposed in descending order including T-0`() {
        val schedule = CountdownSchedule(listOf(10, 60, 5, 30))
        assertThat(schedule.configuredMarks.map { it.secondsRemaining })
            .containsExactly(60, 30, 10, 5, 0)
    }

    @Test
    fun `negative marks are rejected`() {
        assertThatThrownBy { CountdownSchedule(listOf(60, -1)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `negative seconds-remaining never fires`() {
        val schedule = CountdownSchedule(listOf(10))
        assertThat(schedule.fireAt(-1)).isNull()
    }

    @Test
    fun `mark second flags T-0`() {
        assertThat(MarkSecond(0).isT0).isTrue()
        assertThat(MarkSecond(60).isT0).isFalse()
    }
}
