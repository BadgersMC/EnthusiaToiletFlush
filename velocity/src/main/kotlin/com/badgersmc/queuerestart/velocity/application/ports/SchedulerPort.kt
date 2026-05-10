package com.badgersmc.queuerestart.velocity.application.ports

import com.badgersmc.queuerestart.velocity.application.schedule.ScheduleDefinition

/**
 * Outbound port — fires a callback on a cron schedule. Implemented by
 * `infrastructure/schedule/CronUtilsScheduler`.
 */
interface SchedulerPort {
    /** Register [def] and invoke [onFire] each time its cron expression matches. */
    fun schedule(def: ScheduleDefinition, onFire: (ScheduleDefinition) -> Unit)

    /** Cancel every previously-registered schedule. Idempotent. */
    fun cancelAll()
}
