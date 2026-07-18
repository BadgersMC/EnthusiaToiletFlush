package com.badgersmc.queuerestart.velocity.domain.plan

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object RestartTimes {
    private val token = Regex("(\\d+)\\s*(hours?|h|minutes?|mins?|m|seconds?|secs?|s)?", RegexOption.IGNORE_CASE)

    fun parseDuration(raw: String, maximum: Duration = Duration.ofDays(7)): Duration {
        val value = raw.trim()
        require(!value.startsWith('-')) { "duration must be positive" }
        var end = 0
        var seconds = 0L
        for (match in token.findAll(value)) {
            require(match.range.first == end) { "invalid duration '$raw'" }
            end = match.range.last + 1
            val amount = match.groupValues[1].toLong()
            val unit = match.groupValues[2].lowercase()
            seconds = Math.addExact(seconds, amount * when {
                unit.startsWith("h") -> 3600
                unit.startsWith("s") -> 1
                else -> 60
            })
        }
        require(end == value.length && seconds > 0) { "invalid duration '$raw'" }
        return Duration.ofSeconds(seconds).also { require(it <= maximum) { "duration exceeds configured maximum" } }
    }

    fun format(duration: Duration): String {
        var seconds = duration.seconds.coerceAtLeast(0)
        val hours = seconds / 3600; seconds %= 3600
        val minutes = seconds / 60; seconds %= 60
        return listOf(hours to "hour", minutes to "minute", seconds to "second")
            .filter { it.first > 0 }
            .joinToString(" ") { (amount, unit) -> "$amount $unit${if (amount == 1L) "" else "s"}" }
            .ifBlank { "0 seconds" }
    }

    fun nextClock(raw: String, zone: ZoneId, now: Instant): Instant {
        require(raw.matches(Regex("\\d{2}:\\d{2}"))) { "time must use HH:mm" }
        val parts = raw.split(':')
        val time = LocalTime.of(parts[0].toInt(), parts[1].toInt())
        var candidate = ZonedDateTime.of(LocalDate.ofInstant(now, zone), time, zone)
        if (!candidate.toInstant().isAfter(now)) candidate = candidate.plusDays(1)
        return candidate.toInstant()
    }
}
