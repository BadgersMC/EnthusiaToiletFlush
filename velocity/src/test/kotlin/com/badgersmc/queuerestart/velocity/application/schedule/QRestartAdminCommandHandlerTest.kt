package com.badgersmc.queuerestart.velocity.application.schedule

import com.badgersmc.queuerestart.velocity.application.ports.ConfigPort
import com.badgersmc.queuerestart.velocity.application.ports.QueueRestartConfig
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * REQ-050, REQ-051.
 *
 * `/qrestart reload` reloads the Velocity-owned schedule configuration.
 * `/qrestart trigger <name>` delegates to the proxy restart-plan service.
 */
class QRestartAdminCommandHandlerTest {

    private val hub = ServerId("lobby")

    private class FakeConfigPort(private var snap: QueueRestartConfig) : ConfigPort {
        var reloads = 0
        override fun snapshot(): QueueRestartConfig = snap
        override fun reload() { reloads++ }
        fun replace(next: QueueRestartConfig) { snap = next }
    }

    @Test
    fun `reload reparses Velocity configuration and invokes reload callback`() {
        val config = FakeConfigPort(snap = stubConfig())
        var reloaded = false
        val handler = QRestartAdminCommandHandler(
            config = config,
            triggerSchedule = { false },
            onReload = { reloaded = true },
        )

        val result = handler.reload()

        assertThat(result).isInstanceOf(AdminCommandResult.Reloaded::class.java)
        assertThat(config.reloads).isEqualTo(1)
        assertThat(reloaded).isTrue()
    }

    @Test
    fun `trigger delegates a configured schedule to the plan service`() {
        var requested: String? = null
        val handler = QRestartAdminCommandHandler(
            config = FakeConfigPort(stubConfig()),
            triggerSchedule = { name -> requested = name; true },
        )

        val result = handler.trigger("nightly")

        assertThat(result).isInstanceOf(AdminCommandResult.Triggered::class.java)
        assertThat((result as AdminCommandResult.Triggered).schedule).isEqualTo("nightly")
        assertThat(requested).isEqualTo("nightly")
    }

    @Test
    fun `trigger unknown schedule is rejected`() {
        val handler = QRestartAdminCommandHandler(
            config = FakeConfigPort(stubConfig()),
            triggerSchedule = { false },
        )

        val result = handler.trigger("unknown")

        assertThat(result).isInstanceOf(AdminCommandResult.Rejected::class.java)
        assertThat((result as AdminCommandResult.Rejected).reason)
            .containsIgnoringCase("unknown")
    }

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
