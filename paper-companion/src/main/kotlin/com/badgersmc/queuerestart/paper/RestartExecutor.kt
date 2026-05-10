package com.badgersmc.queuerestart.paper

import com.badgersmc.queuerestart.common.protocol.RestartMode

/**
 * Bukkit-free abstraction over server lifecycle ops. The Paper-bound impl
 * (`BukkitServerControl`) calls `Bukkit.shutdown()`,
 * `Bukkit.dispatchCommand(consoleSender, …)`, and `System.exit(code)`.
 */
interface ServerControl {
    fun shutdown()
    fun dispatchConsoleCommand(command: String)
    fun exitProcess(code: Int)
}

/**
 * Schedules an action `delaySeconds` from now on the server's main thread.
 * The Paper-bound impl wraps `Bukkit.getScheduler().runTaskLater(…)`.
 * The default in-process implementation runs the action synchronously,
 * which keeps tests ergonomic and makes a `delaySeconds == 0` message
 * behave identically to the legacy "fire now" path.
 */
fun interface RestartScheduler {
    fun runAfterSeconds(delaySeconds: Int, action: () -> Unit)

    companion object {
        val IMMEDIATE: RestartScheduler = RestartScheduler { _, action -> action() }
    }
}

/**
 * REQ-021. Executes a `RestartNow` plugin message by dispatching the right
 * action against [ServerControl] after deferring by `delaySeconds`.
 *
 * The deferral closes the gap created by Velocity dropping plugin messages
 * to backends with no players — the proxy ships RestartNow at countdown
 * start (while at least one player is on target) and the companion's local
 * timer fires the actual shutdown later.
 */
class RestartExecutor(
    private val control: ServerControl,
    private val scheduler: RestartScheduler = RestartScheduler.IMMEDIATE,
) {

    fun execute(mode: RestartMode, argument: String, delaySeconds: Int) {
        require(delaySeconds >= 0) { "delaySeconds must be ≥ 0; got $delaySeconds" }
        scheduler.runAfterSeconds(delaySeconds) {
            when (mode) {
                RestartMode.SHUTDOWN -> control.shutdown()
                RestartMode.COMMAND -> control.dispatchConsoleCommand(argument)
                RestartMode.EXIT_CODE -> {
                    val code = argument.toIntOrNull()
                        ?: throw IllegalArgumentException(
                            "EXIT_CODE mode requires a numeric argument; got '$argument'",
                        )
                    control.exitProcess(code)
                }
            }
        }
    }
}
