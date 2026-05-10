package com.badgersmc.queuerestart.velocity.application.schedule

import com.badgersmc.queuerestart.velocity.application.ports.AudiencePort
import com.badgersmc.queuerestart.velocity.application.ports.SoundCue
import com.badgersmc.queuerestart.velocity.domain.countdown.CountdownSchedule
import com.badgersmc.queuerestart.velocity.domain.id.ServerId

/**
 * REQ-003, REQ-004.
 *
 * Holds the active countdown for each target server and dispatches the
 * configured chat message + sound at every mark including T-0. The
 * orchestrator (T-072) is responsible for computing seconds-remaining
 * and feeding it via [tick].
 *
 * Idempotent: calling [tick] for the same `(target, secondsRemaining)`
 * twice fires only once, so a 1 Hz tick driver can safely overshoot.
 */
class CountdownBroadcaster(
    private val audience: AudiencePort,
    private val messageTemplate: String,
    private val t0Template: String,
    private val soundResolver: (Int) -> SoundCue?,
    /** Operator-visible log line per fired mark. Default no-op for tests. */
    private val onMark: (ServerId, Int, Boolean) -> Unit = { _, _, _ -> },
) {

    private data class Active(
        val schedule: CountdownSchedule,
        val hub: ServerId,
        var lastFiredSecond: Int? = null,
    )

    private val active = mutableMapOf<ServerId, Active>()

    fun register(target: ServerId, schedule: CountdownSchedule, hub: ServerId) {
        active[target] = Active(schedule, hub)
    }

    fun cancel(target: ServerId) {
        active.remove(target)
    }

    fun tick(target: ServerId, secondsRemaining: Int) {
        val a = active[target] ?: return
        val mark = a.schedule.fireAt(secondsRemaining) ?: return
        if (a.lastFiredSecond == mark.secondsRemaining) return
        a.lastFiredSecond = mark.secondsRemaining

        val template = if (mark.isT0) t0Template else messageTemplate
        val placeholders = mapOf(
            "server" to target.value,
            "time" to formatTime(mark.secondsRemaining),
            "hub" to a.hub.value,
        )
        audience.broadcast(target, template, placeholders)
        soundResolver(mark.secondsRemaining)?.let { audience.playSound(target, it) }
        onMark(target, mark.secondsRemaining, mark.isT0)
    }

    private fun formatTime(seconds: Int): String = when {
        seconds == 0 -> "0s"
        seconds % 60 == 0 -> "${seconds / 60}m"
        else -> "${seconds}s"
    }
}
