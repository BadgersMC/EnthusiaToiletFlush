package com.badgersmc.queuerestart.paper

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Resolves the next absolute moment a daily [time] will occur in [zone],
 * starting from [now]. If [time] has not yet happened today (strictly after
 * [now]), today's instance is returned; otherwise tomorrow's.
 *
 * Pure for testability — the scheduler that consumes this is not.
 */
object NextOccurrence {
    fun compute(now: ZonedDateTime, time: LocalTime, zone: ZoneId): ZonedDateTime {
        val today = ZonedDateTime.of(now.toLocalDate(), time, zone)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }
}
