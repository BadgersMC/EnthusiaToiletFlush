package com.badgersmc.queuerestart.velocity.application.schedule

import com.badgersmc.queuerestart.velocity.application.ports.ConfigPort

sealed interface AdminCommandResult {
    data object Reloaded : AdminCommandResult
    data class Triggered(val schedule: String) : AdminCommandResult
    data class Rejected(val reason: String) : AdminCommandResult
}

/**
 * REQ-050, REQ-051.
 *
 * `/qrestart reload` reparses config and rebuilds cron registrations —
 * does NOT touch in-flight RestartCoordinator state machines, so an
 * armed countdown survives a reload.
 *
 * `/qrestart trigger <name>` looks up the schedule by name and arms its
 * restart immediately by delegating to [SchedRestartCommandHandler.arm]
 * — same path as if the cron had fired.
 */
class QRestartAdminCommandHandler(
    private val config: ConfigPort,
    private val scheduleService: ScheduleService,
    private val schedRestartHandler: SchedRestartCommandHandler,
    /**
     * SECURITY (REQ-090): callback fired after [ConfigPort.reload] so the
     * infrastructure layer can re-derive any state that depends on the
     * snapshot — notably the `VelocityProxyServerBackend.withRankLadder`
     * probe set. Without this, a rank-ladder edit + /qrestart reload
     * leaves new nodes unprobed and players slip through with default
     * queue weights.
     */
    private val onReload: () -> Unit = {},
) {

    fun reload(): AdminCommandResult {
        config.reload()
        onReload()
        // Schedules are owned by the backends and discovered via SLP — the
        // discovery poller refreshes them on its own cadence, so /qrestart
        // reload only needs to reparse proxy-side settings (countdown
        // messages, rank ladder, etc.). Cron entries stay untouched here.
        return AdminCommandResult.Reloaded
    }

    fun trigger(name: String): AdminCommandResult {
        val def = scheduleService.findByName(name)
            ?: return AdminCommandResult.Rejected("unknown schedule '$name'")
        val armed = schedRestartHandler.arm(def.target, durationMinutes = def.warnMinutes)
        return when (armed) {
            is SchedCommandResult.Armed -> AdminCommandResult.Triggered(name)
            is SchedCommandResult.Rejected -> AdminCommandResult.Rejected(armed.reason)
            else -> AdminCommandResult.Rejected("unexpected arm outcome: $armed")
        }
    }
}
