package com.badgersmc.queuerestart.velocity.domain.plan

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class RestartTimesTest {
    @Test fun `parses supported durations`() {
        assertThat(RestartTimes.parseDuration("30")).isEqualTo(Duration.ofMinutes(30))
        assertThat(RestartTimes.parseDuration("30 sec")).isEqualTo(Duration.ofSeconds(30))
        assertThat(RestartTimes.parseDuration("1h30m")).isEqualTo(Duration.ofMinutes(90))
        assertThat(RestartTimes.parseDuration("1h 30m")).isEqualTo(Duration.ofMinutes(90))
        assertThatThrownBy { RestartTimes.parseDuration("0") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { RestartTimes.parseDuration("-5m") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test fun `clock schedules tomorrow after todays time`() {
        val zone = ZoneId.of("America/Indiana/Indianapolis")
        val now = ZonedDateTime.of(2026, 7, 17, 23, 0, 0, 0, zone).toInstant()
        assertThat(RestartTimes.nextClock("00:00", zone, now)).isEqualTo(ZonedDateTime.of(2026, 7, 18, 0, 0, 0, 0, zone).toInstant())
    }
}
