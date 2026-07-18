package com.badgersmc.queuerestart.velocity.application.network

import com.badgersmc.queuerestart.velocity.application.ports.ExternalRestartExecutor
import com.badgersmc.queuerestart.velocity.application.ports.NetworkControlPort
import com.badgersmc.queuerestart.velocity.application.ports.NetworkRestartConfig
import com.badgersmc.queuerestart.velocity.application.ports.PowerActionResult
import com.badgersmc.queuerestart.velocity.application.ports.RestartNotice
import com.badgersmc.queuerestart.velocity.application.ports.RestartPlanStore
import com.badgersmc.queuerestart.velocity.application.ports.TransferSummary
import com.badgersmc.queuerestart.velocity.application.schedule.SchedCommandResult
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import com.badgersmc.queuerestart.velocity.domain.plan.PlanState
import com.badgersmc.queuerestart.velocity.domain.plan.PlanType
import com.badgersmc.queuerestart.velocity.domain.plan.RestartPlan
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class NetworkRestartServiceTest {
    private val hub = ServerId("HUB")
    private val smp = ServerId("SMP")

    @Test
    fun `initial proxy warning does not repeat its matching threshold`() {
        val control = FakeControl()
        val service = service(control)
        val now = Instant.now()
        service.createManual(PlanType.PROXY, emptySet(), now.plusSeconds(7200), now, "", "console", false)

        service.tick(now)

        assertThat(control.broadcasts).hasSize(1)
    }

    @Test
    fun `manual plan replaces an overlapping automatic plan`() {
        val service = service(FakeControl())
        val now = Instant.now()
        val automatic = RestartPlan(
            type = PlanType.NETWORK,
            targets = setOf(hub, smp),
            createdAt = now,
            executionAt = now.plusSeconds(120),
            warningAt = now,
            reason = "",
            creator = "AUTOMATIC:nightly",
            automaticKey = "nightly@${now.plusSeconds(120)}",
        )
        service.schedule(automatic)

        service.createManual(PlanType.PROXY, emptySet(), now.plusSeconds(180), now, "", "console", false)

        assertThat(automatic.state).isEqualTo(PlanState.CANCELLED)
        assertThat(service.allPlans().count { it.active() }).isEqualTo(1)
    }

    @Test
    fun `proxy disconnect happens before its external restart request`() {
        val events = mutableListOf<String>()
        val control = FakeControl(events)
        val service = service(control, FakeExecutor(events))
        val now = Instant.now()
        service.createManual(PlanType.PROXY, emptySet(), now.plusSeconds(1), now, "", "console", false)

        service.tick(now.plusSeconds(2))

        assertThat(events).containsSubsequence("disconnect", "restart:proxy")
    }

    private fun service(control: FakeControl, executor: ExternalRestartExecutor = FakeExecutor(mutableListOf())): NetworkRestartService {
        val config = NetworkRestartConfig.disabled().copy(
            enabled = true,
            serverIds = mapOf(hub to "hub1234", smp to "smp1234"),
            proxyServerId = "proxy1234",
            members = listOf(hub, smp),
            hubServers = listOf(hub),
        )
        return NetworkRestartService(
            config = { config },
            schedules = { emptyList() },
            executor = executor,
            control = control,
            store = object : RestartPlanStore {
                override fun load(): List<RestartPlan> = emptyList()
                override fun save(plans: Collection<RestartPlan>) = Unit
            },
            backendArm = { server, seconds, _ -> SchedCommandResult.Armed(server, seconds) },
            backendCancel = {},
            audit = { _, _ -> },
        )
    }

    private class FakeExecutor(private val events: MutableList<String>) : ExternalRestartExecutor {
        override val name = "fake"
        override fun preflight(panelServerId: String): CompletionStage<PowerActionResult> =
            CompletableFuture.completedFuture(PowerActionResult(true, "ok"))
        override fun restart(actionKey: String, panelServerId: String): CompletionStage<PowerActionResult> {
            events += "restart:${actionKey.substringAfterLast(':')}"
            return CompletableFuture.completedFuture(PowerActionResult(true, "ok"))
        }
    }

    private class FakeControl(private val events: MutableList<String> = mutableListOf()) : NetworkControlPort {
        val broadcasts = mutableListOf<RestartNotice>()
        override fun broadcast(notice: RestartNotice) { broadcasts += notice }
        override fun disconnectAll(notice: RestartNotice) { events += "disconnect" }
        override fun transferAll(from: ServerId, destinations: List<ServerId>): CompletionStage<TransferSummary> =
            CompletableFuture.completedFuture(TransferSummary(0, 0, 0))
        override fun setMaintenance(enabled: Boolean, duration: Duration) = Unit
        override fun maintenanceActive(): Boolean = false
    }
}
