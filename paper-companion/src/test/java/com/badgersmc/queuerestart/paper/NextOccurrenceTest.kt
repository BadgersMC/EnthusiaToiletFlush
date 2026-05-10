package com.badgersmc.queuerestart.paper

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class NextOccurrenceTest {

    private val ny = ZoneId.of("America/New_York")

    @Test
    fun `time later today is today`() {
        val now = ZonedDateTime.of(2026, 5, 9, 10, 0, 0, 0, ny)
        val target = NextOccurrence.compute(now, LocalTime.of(16, 0), ny)
        assertThat(target).isEqualTo(ZonedDateTime.of(2026, 5, 9, 16, 0, 0, 0, ny))
    }

    @Test
    fun `time earlier today rolls to tomorrow`() {
        val now = ZonedDateTime.of(2026, 5, 9, 17, 0, 0, 0, ny)
        val target = NextOccurrence.compute(now, LocalTime.of(16, 0), ny)
        assertThat(target).isEqualTo(ZonedDateTime.of(2026, 5, 10, 16, 0, 0, 0, ny))
    }

    @Test
    fun `time exactly equal to now rolls to tomorrow`() {
        // strictly-after semantics: equal is treated as already past, so we
        // don't re-fire the same instant on a re-arm.
        val now = ZonedDateTime.of(2026, 5, 9, 4, 0, 0, 0, ny)
        val target = NextOccurrence.compute(now, LocalTime.of(4, 0), ny)
        assertThat(target).isEqualTo(ZonedDateTime.of(2026, 5, 10, 4, 0, 0, 0, ny))
    }

    @Test
    fun `month and year roll over correctly`() {
        val now = ZonedDateTime.of(2026, 12, 31, 23, 30, 0, 0, ny)
        val target = NextOccurrence.compute(now, LocalTime.of(4, 0), ny)
        assertThat(target).isEqualTo(ZonedDateTime.of(2027, 1, 1, 4, 0, 0, 0, ny))
    }
}
