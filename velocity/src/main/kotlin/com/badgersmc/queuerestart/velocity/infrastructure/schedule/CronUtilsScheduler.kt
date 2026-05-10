package com.badgersmc.queuerestart.velocity.infrastructure.schedule

import com.badgersmc.queuerestart.velocity.application.ports.SchedulerPort
import com.badgersmc.queuerestart.velocity.application.schedule.ScheduleDefinition
import com.cronutils.model.CronType
import com.cronutils.model.definition.CronDefinitionBuilder
import com.cronutils.model.time.ExecutionTime
import com.cronutils.parser.CronParser
import java.time.Instant
import java.time.ZonedDateTime

/**
 * REQ-002. Cron-utils-backed [SchedulerPort].
 *
 * Pure tick-driven: callers invoke [tick] with an [Instant]. The Velocity
 * binding wires this to a recurring proxy task and feeds it
 * `Instant.now()`; tests pass arbitrary instants under a fake clock.
 *
 * Cron expressions are evaluated in each [ScheduleDefinition.zone] —
 * SLP-discovered backend schedules pass the backend's advertised zone so
 * cron matching tracks backend-local time even when the proxy host runs
 * in a different zone.
 */
class CronUtilsScheduler : SchedulerPort {

    private val parser = CronParser(
        CronDefinitionBuilder.instanceDefinitionFor(CronType.UNIX),
    )

    private data class Registration(
        val def: ScheduleDefinition,
        val onFire: (ScheduleDefinition) -> Unit,
        val executionTime: ExecutionTime,
        /** Most recent fire instant we've already dispatched, or null. */
        var lastFiredAt: ZonedDateTime?,
    )

    // /qrestart reload mutates this from a command thread while tick()
    // iterates it on the proxy tick thread.
    private val registrations = java.util.concurrent.CopyOnWriteArrayList<Registration>()

    override fun schedule(def: ScheduleDefinition, onFire: (ScheduleDefinition) -> Unit) {
        val cron = try {
            parser.parse(def.cronExpression).also { it.validate() }
        } catch (e: Exception) {
            throw IllegalArgumentException(
                "invalid cron expression for schedule '${def.name}': '${def.cronExpression}'", e,
            )
        }
        registrations += Registration(
            def = def,
            onFire = onFire,
            executionTime = ExecutionTime.forCron(cron),
            lastFiredAt = null,
        )
    }

    override fun cancelAll() {
        registrations.clear()
    }

    /**
     * Advance the scheduler to [now]. For each registration, walk forward
     * from the last-fired instant (or the current `now` on first tick),
     * firing exactly once per cron-match in the interval `(lastFired, now]`.
     */
    fun tick(now: Instant) {
        for (reg in registrations) {
            val zoned = now.atZone(reg.def.zone)
            if (reg.lastFiredAt == null) {
                // First tick: fire iff the most recent cron match falls
                // within the past [graceSeconds] window — catches the case
                // of "scheduler started seconds after a cron instant".
                val previous = reg.executionTime.lastExecution(zoned).orElse(null) ?: continue
                val graceCutoff = zoned.minusSeconds(GRACE_SECONDS)
                if (!previous.isBefore(graceCutoff)) {
                    reg.onFire(reg.def)
                }
                reg.lastFiredAt = previous
                continue
            }

            var cursor = reg.lastFiredAt!!
            while (true) {
                val next = reg.executionTime.nextExecution(cursor).orElse(null) ?: break
                if (next.isAfter(zoned)) break
                reg.onFire(reg.def)
                reg.lastFiredAt = next
                cursor = next
            }
        }
    }

    private companion object {
        /** First-tick grace: fire if the most recent cron match was this recent. */
        const val GRACE_SECONDS = 60L
    }
}
