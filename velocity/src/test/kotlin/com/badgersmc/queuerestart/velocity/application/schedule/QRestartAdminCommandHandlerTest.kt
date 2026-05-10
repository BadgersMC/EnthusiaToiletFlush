package com.badgersmc.queuerestart.velocity.application.schedule

import com.badgersmc.queuerestart.velocity.application.ports.ConfigPort
import com.badgersmc.queuerestart.velocity.application.ports.QueueRestartConfig
import com.badgersmc.queuerestart.velocity.application.ports.SchedulerPort
import com.badgersmc.queuerestart.velocity.domain.cohort.Cohort
import com.badgersmc.queuerestart.velocity.domain.coordinator.RestartState
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * REQ-050, REQ-051.
 *
 * `/qrestart reload` reloads config and reschedules cron entries without
 * touching coordinators (REQ-050). `/qrestart trigger <name>` arms the
 * named schedule's restart immediately (REQ-051).
 */
class QRestartAdminCommandHandlerTest {

    private val survival = ServerId("survival")
    private val hub = ServerId("lobby")

    private class FakeScheduler : SchedulerPort {
        val registered = mutableListOf<ScheduleDefinition>()
        var cancelled = 0
        override fun schedule(def: ScheduleDefinition, onFire: (ScheduleDefinition) -> Unit) {
            registered += def
        }
        override fun cancelAll() {
            cancelled++
            registered.clear()
        }
    }

    private class FakeConfigPort(private var snap: QueueRestartConfig) : ConfigPort {
        var reloads = 0
        override fun snapshot(): QueueRestartConfig = snap
        override fun reload() { reloads++ }
        fun replace(next: QueueRestartConfig) { snap = next }
    }

    private val nightly = ScheduleDefinition(
        name = "nightly",
        target = survival,
        cronExpression = "0 4 * * *",
        warnMinutes = 20,
    )

    @Test
    fun `reload reparses proxy config without touching cron registrations (REQ-050)`() {
        // Schedules are owned by backends and discovered via SLP, not by config.
        // /qrestart reload reparses proxy-side settings only and leaves the
        // scheduler alone so in-flight cron entries survive.
        val sched = FakeScheduler()
        val service = ScheduleService(sched, onTrigger = {})
        service.loadAll(listOf(nightly))
        val registeredBefore = sched.registered.toList()

        val config = FakeConfigPort(snap = stubConfig())
        val handler = QRestartAdminCommandHandler(
            config = config,
            scheduleService = service,
            schedRestartHandler = noopSchedHandler(),
        )

        val result = handler.reload()

        assertThat(result).isInstanceOf(AdminCommandResult.Reloaded::class.java)
        assertThat(config.reloads).isEqualTo(1)
        assertThat(sched.cancelled).isEqualTo(0)
        assertThat(sched.registered).isEqualTo(registeredBefore)
    }

    @Test
    fun `trigger known schedule arms via the schedrestart handler (REQ-051)`() {
        val sched = FakeScheduler()
        val service = ScheduleService(sched, onTrigger = {})
        service.loadAll(listOf(nightly))

        var armed: Pair<ServerId, Int>? = null
        val schedHandler = SchedRestartCommandHandler(
            registry = CoordinatorRegistry(),
            hubServer = hub,
            companionPresent = { true },
            cohortFor = { Cohort(emptySet()) },
        )

        val handler = QRestartAdminCommandHandler(
            config = FakeConfigPort(stubConfig()),
            scheduleService = service,
            schedRestartHandler = schedHandler,
        )

        val result = handler.trigger("nightly")

        assertThat(result).isInstanceOf(AdminCommandResult.Triggered::class.java)
        assertThat((result as AdminCommandResult.Triggered).schedule).isEqualTo("nightly")
        // verify the SchedRestartCommandHandler armed the survival coordinator
        val state = (schedHandler.status() as SchedCommandResult.Status).states
        assertThat(state[survival]).isEqualTo(RestartState.ARMED)
    }

    @Test
    fun `trigger unknown schedule is rejected`() {
        val sched = FakeScheduler()
        val service = ScheduleService(sched, onTrigger = {})
        service.loadAll(listOf(nightly))

        val handler = QRestartAdminCommandHandler(
            config = FakeConfigPort(stubConfig()),
            scheduleService = service,
            schedRestartHandler = noopSchedHandler(),
        )

        val result = handler.trigger("unknown")

        assertThat(result).isInstanceOf(AdminCommandResult.Rejected::class.java)
        assertThat((result as AdminCommandResult.Rejected).reason)
            .containsIgnoringCase("unknown")
    }

    private fun noopSchedHandler() = SchedRestartCommandHandler(
        registry = CoordinatorRegistry(),
        hubServer = hub,
        companionPresent = { true },
        cohortFor = { Cohort(emptySet()) },
    )

    private fun stubConfig() = QueueRestartConfig(
        hubServer = hub,
        fallbackHubs = emptyList(),
        drain = com.badgersmc.queuerestart.velocity.application.ports.DrainConfig(
            batchSize = 10,
            batchIntervalTicks = 40,
            drainLeadSeconds = 30,
            forceDrainTimeoutSeconds = 120,
            drainOrder = com.badgersmc.queuerestart.velocity.application.drain.DrainOrder.PRIORITY_ASC,
        ),
        rejoin = com.badgersmc.queuerestart.velocity.application.ports.RejoinConfig(
            enabled = true,
            enqueueOnServerUp = true,
            releaseOnCheckhacksCleared = true,
            checkGateTimeoutSeconds = 60,
            releaseOnTimeout = true,
            pingPollSeconds = 3,
        ),
        countdown = com.badgersmc.queuerestart.velocity.application.ports.CountdownConfig(
            marksSeconds = listOf(60, 30, 10),
            message = "<gold>warn",
            messageT0 = "<red>now",
            cancelMessage = "<green>cancelled",
        ),
        sounds = emptyMap(),
        rankLadder = emptyMap(),
        rankDefault = 0,
    )
}
