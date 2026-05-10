package com.badgersmc.queuerestart.velocity.infrastructure.schedule

import com.badgersmc.queuerestart.velocity.application.schedule.ScheduleDefinition
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * REQ-002.
 *
 * Drives the scheduler with a fake clock, asserting fires happen only at
 * cron-matching instants. Uses Unix-style cron ("min hour dom month dow").
 */
class CronUtilsSchedulerTest {

    private val zone = ZoneId.of("UTC")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone).toInstant()

    private fun nightly() = ScheduleDefinition(
        name = "nightly",
        target = ServerId("survival"),
        cronExpression = "0 4 * * *", // every day at 04:00 UTC
        warnMinutes = 20,
        zone = zone,
    )

    @Test
    fun `fires when tick crosses the next execution instant`() {
        val sched = CronUtilsScheduler()
        val fired = mutableListOf<ScheduleDefinition>()
        sched.schedule(nightly(), fired::add)

        // Start at 03:30 — nothing yet.
        sched.tick(at(2026, 1, 1, 3, 30))
        assertThat(fired).isEmpty()

        // Tick past 04:00 — must fire exactly once.
        sched.tick(at(2026, 1, 1, 4, 30))
        assertThat(fired).hasSize(1)
        assertThat(fired[0].name).isEqualTo("nightly")

        // Same day, later — must not fire again.
        sched.tick(at(2026, 1, 1, 23, 0))
        assertThat(fired).hasSize(1)

        // Next day past 04:00 — fires again.
        sched.tick(at(2026, 1, 2, 4, 5))
        assertThat(fired).hasSize(2)
    }

    @Test
    fun `multiple schedules fire independently`() {
        val sched = CronUtilsScheduler()
        val fired = mutableListOf<String>()
        sched.schedule(nightly()) { fired += it.name }
        sched.schedule(
            ScheduleDefinition(
                name = "noon",
                target = ServerId("creative"),
                cronExpression = "0 12 * * *",
                warnMinutes = 5,
                zone = zone,
            ),
        ) { fired += it.name }

        sched.tick(at(2026, 1, 1, 4, 1))
        assertThat(fired).containsExactly("nightly")

        sched.tick(at(2026, 1, 1, 12, 1))
        assertThat(fired).containsExactly("nightly", "noon")
    }

    @Test
    fun `cancelAll prevents further firings`() {
        val sched = CronUtilsScheduler()
        val fired = mutableListOf<String>()
        sched.schedule(nightly()) { fired += it.name }

        sched.cancelAll()
        sched.tick(at(2026, 1, 1, 4, 30))

        assertThat(fired).isEmpty()
    }

    @Test
    fun `invalid cron expression is rejected at register`() {
        val sched = CronUtilsScheduler()
        val bad = ScheduleDefinition(
            name = "bad",
            target = ServerId("survival"),
            cronExpression = "not a cron",
            warnMinutes = 5,
        )
        assertThatThrownBy { sched.schedule(bad) {} }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `weekly cron only fires on the configured day`() {
        val sched = CronUtilsScheduler()
        val fired = mutableListOf<String>()
        // every Monday at 05:00 UTC
        sched.schedule(
            ScheduleDefinition(
                name = "weekly",
                target = ServerId("creative"),
                cronExpression = "0 5 * * 1",
                warnMinutes = 15,
                zone = zone,
            ),
        ) { fired += it.name }

        // 2026-01-05 is a Monday
        sched.tick(at(2026, 1, 4, 5, 30)) // Sunday, must not fire
        assertThat(fired).isEmpty()

        sched.tick(at(2026, 1, 5, 5, 30)) // Monday, fires
        assertThat(fired).containsExactly("weekly")
    }
}
