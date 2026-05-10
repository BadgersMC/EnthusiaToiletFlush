package com.badgersmc.queuerestart.paper

import com.badgersmc.queuerestart.common.protocol.RestartMode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * REQ-021. RestartExecutor dispatches the correct action per [RestartMode]
 * using a fake [ServerControl] — no Bukkit on the test classpath.
 */
class RestartExecutorTest {

    private class FakeControl : ServerControl {
        var shutdownCalls = 0
        val dispatched = mutableListOf<String>()
        var exitCode: Int? = null
        override fun shutdown() { shutdownCalls++ }
        override fun dispatchConsoleCommand(command: String) { dispatched += command }
        override fun exitProcess(code: Int) { exitCode = code }
    }

    private class CapturingScheduler : RestartScheduler {
        data class ScheduledRun(val delaySeconds: Int, val action: () -> Unit)
        val queued = mutableListOf<ScheduledRun>()
        override fun runAfterSeconds(delaySeconds: Int, action: () -> Unit) {
            queued += ScheduledRun(delaySeconds, action)
        }
        fun fireAll() = queued.toList().also { queued.clear() }.forEach { it.action() }
    }

    @Test
    fun `SHUTDOWN mode invokes server shutdown`() {
        val ctl = FakeControl()
        RestartExecutor(ctl).execute(RestartMode.SHUTDOWN, argument = "", delaySeconds = 0)
        assertThat(ctl.shutdownCalls).isEqualTo(1)
        assertThat(ctl.dispatched).isEmpty()
        assertThat(ctl.exitCode).isNull()
    }

    @Test
    fun `COMMAND mode dispatches the argument as a console command`() {
        val ctl = FakeControl()
        RestartExecutor(ctl).execute(RestartMode.COMMAND, argument = "restart", delaySeconds = 0)
        assertThat(ctl.dispatched).containsExactly("restart")
        assertThat(ctl.shutdownCalls).isEqualTo(0)
    }

    @Test
    fun `EXIT_CODE mode exits with the parsed integer argument`() {
        val ctl = FakeControl()
        RestartExecutor(ctl).execute(RestartMode.EXIT_CODE, argument = "42", delaySeconds = 0)
        assertThat(ctl.exitCode).isEqualTo(42)
    }

    @Test
    fun `EXIT_CODE rejects a non-numeric argument`() {
        val ctl = FakeControl()
        assertThatThrownBy {
            RestartExecutor(ctl).execute(RestartMode.EXIT_CODE, argument = "nope", delaySeconds = 0)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `negative delay is rejected`() {
        val ctl = FakeControl()
        assertThatThrownBy {
            RestartExecutor(ctl).execute(RestartMode.SHUTDOWN, "", delaySeconds = -1)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `delay defers the action through the scheduler`() {
        val ctl = FakeControl()
        val sched = CapturingScheduler()
        RestartExecutor(ctl, sched).execute(RestartMode.SHUTDOWN, "", delaySeconds = 60)

        assertThat(sched.queued).singleElement().satisfies({
            assertThat(it.delaySeconds).isEqualTo(60)
        })
        assertThat(ctl.shutdownCalls).isEqualTo(0)

        sched.fireAll()
        assertThat(ctl.shutdownCalls).isEqualTo(1)
    }
}
