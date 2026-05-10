package com.badgersmc.queuerestart.common.schedule

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.ZoneId

class ScheduleEncodingTest {

    @Test
    fun `round trips multiple times`() {
        val original = BackendSchedule(
            times = listOf(LocalTime.of(4, 0), LocalTime.of(16, 30)),
            zone = ZoneId.of("America/New_York"),
            warnMinutes = 30,
        )
        val encoded = ScheduleEncoding.encode(original)
        assertThat(encoded).isEqualTo("QR_SCHEDULE:04:00,16:30:America/New_York:30")
        assertThat(ScheduleEncoding.decode(encoded)).isEqualTo(original)
    }

    @Test
    fun `round trips empty times list`() {
        val original = BackendSchedule(
            times = emptyList(),
            zone = ZoneId.of("UTC"),
            warnMinutes = 0,
        )
        val encoded = ScheduleEncoding.encode(original)
        assertThat(ScheduleEncoding.decode(encoded)).isEqualTo(original)
    }

    @Test
    fun `decode rejects missing prefix`() {
        assertThat(ScheduleEncoding.decode("not_a_marker")).isNull()
    }

    @Test
    fun `decode rejects malformed payload`() {
        assertThat(ScheduleEncoding.decode("QR_SCHEDULE:nope")).isNull()
        assertThat(ScheduleEncoding.decode("QR_SCHEDULE:04:00:notazone:30")).isNull()
        assertThat(ScheduleEncoding.decode("QR_SCHEDULE:04:00:UTC:abc")).isNull()
    }

    @Test
    fun `decode tolerates unparseable individual times`() {
        // Times that don't parse get dropped; a partially-valid schedule still
        // decodes — better than rejecting the whole announcement on one typo.
        val decoded = ScheduleEncoding.decode("QR_SCHEDULE:04:00,99:99,16:00:UTC:30")
        assertThat(decoded).isEqualTo(
            BackendSchedule(
                times = listOf(LocalTime.of(4, 0), LocalTime.of(16, 0)),
                zone = ZoneId.of("UTC"),
                warnMinutes = 30,
            )
        )
    }

    @Test
    fun `encode rejects negative warnMinutes`() {
        org.assertj.core.api.Assertions.assertThatThrownBy {
            ScheduleEncoding.encode(
                BackendSchedule(
                    times = emptyList(),
                    zone = ZoneId.of("UTC"),
                    warnMinutes = -1,
                )
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
