package com.badgersmc.queuerestart.velocity.application.schedule

import com.badgersmc.queuerestart.velocity.application.ports.SchedulerPort
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * REQ-002, REQ-050, REQ-051.
 *
 * ScheduleService owns the *registration* of cron schedules with the
 * scheduler port and the lookup by name for ad-hoc /qrestart trigger.
 * Reload rebuilds registrations only — RestartCoordinator state machines
 * (in-flight countdowns) live elsewhere and are not touched here.
 */
class ScheduleServiceTest {

    private class RecordingScheduler : SchedulerPort {
        data class Registration(val def: ScheduleDefinition, val onFire: (ScheduleDefinition) -> Unit)
        val registered = mutableListOf<Registration>()
        var cancelCount = 0
        override fun schedule(def: ScheduleDefinition, onFire: (ScheduleDefinition) -> Unit) {
            registered += Registration(def, onFire)
        }
        override fun cancelAll() {
            cancelCount++
            registered.clear()
        }
    }

    private val survivalNightly = ScheduleDefinition(
        name = "survival-nightly",
        target = ServerId("survival"),
        cronExpression = "0 4 * * *",
        warnMinutes = 20,
    )

    private val creativeWeekly = ScheduleDefinition(
        name = "creative-weekly",
        target = ServerId("creative"),
        cronExpression = "0 5 * * 1",
        warnMinutes = 15,
    )

    @Test
    fun `loadAll registers every schedule with the scheduler port`() {
        val sched = RecordingScheduler()
        val fired = mutableListOf<ScheduleDefinition>()
        val service = ScheduleService(sched, onTrigger = fired::add)

        service.loadAll(listOf(survivalNightly, creativeWeekly))

        assertThat(sched.registered.map { it.def }).containsExactly(survivalNightly, creativeWeekly)
    }

    @Test
    fun `scheduler firing invokes onTrigger callback`() {
        val sched = RecordingScheduler()
        val fired = mutableListOf<ScheduleDefinition>()
        val service = ScheduleService(sched, onTrigger = fired::add)

        service.loadAll(listOf(survivalNightly))
        sched.registered.first().onFire(survivalNightly)

        assertThat(fired).containsExactly(survivalNightly)
    }

    @Test
    fun `reload cancels old registrations and registers new (REQ-050)`() {
        val sched = RecordingScheduler()
        val service = ScheduleService(sched, onTrigger = {})

        service.loadAll(listOf(survivalNightly))
        service.reload(listOf(creativeWeekly))

        assertThat(sched.cancelCount).isEqualTo(1)
        assertThat(sched.registered.map { it.def }).containsExactly(creativeWeekly)
    }

    @Test
    fun `trigger by name returns the matching definition (REQ-051)`() {
        val sched = RecordingScheduler()
        val service = ScheduleService(sched, onTrigger = {})
        service.loadAll(listOf(survivalNightly, creativeWeekly))

        assertThat(service.findByName("creative-weekly")).isEqualTo(creativeWeekly)
    }

    @Test
    fun `trigger unknown name returns null`() {
        val service = ScheduleService(RecordingScheduler(), onTrigger = {})
        service.loadAll(listOf(survivalNightly))
        assertThat(service.findByName("nope")).isNull()
    }

    @Test
    fun `duplicate names are rejected at load`() {
        val service = ScheduleService(RecordingScheduler(), onTrigger = {})
        val duplicate = survivalNightly.copy(target = ServerId("creative"))
        assertThatThrownBy { service.loadAll(listOf(survivalNightly, duplicate)) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `empty load is permitted and registers nothing`() {
        val sched = RecordingScheduler()
        val service = ScheduleService(sched, onTrigger = {})
        service.loadAll(emptyList())
        assertThat(sched.registered).isEmpty()
    }

    @Test
    fun `reload to empty cancels all without errors`() {
        val sched = RecordingScheduler()
        val service = ScheduleService(sched, onTrigger = {})
        service.loadAll(listOf(survivalNightly))
        service.reload(emptyList())
        assertThat(sched.registered).isEmpty()
    }
}
