package com.badgersmc.queuerestart.velocity.application.schedule

import com.badgersmc.queuerestart.common.schedule.BackendSchedule
import com.badgersmc.queuerestart.velocity.domain.id.ServerId

/**
 * Per-backend cache of the [BackendSchedule] each companion advertises via
 * Server-List-Ping samples. The proxy never edits a config file for
 * schedules — it learns each backend's cadence from the backend itself
 * (companion-as-source-of-truth) and re-arms the cron scheduler whenever
 * a change is observed.
 *
 * Listeners are invoked exactly once per detected change and receive an
 * immutable snapshot of the full cache.
 */
class BackendScheduleCache {

    private val byServer = linkedMapOf<ServerId, BackendSchedule>()
    private val listeners = mutableListOf<(Map<ServerId, BackendSchedule>) -> Unit>()

    /**
     * Replace [server]'s cached schedule. Returns true if the cache changed
     * (new entry, removed entry, or different value); false when [schedule]
     * matches the current entry. Subscribers are notified only on change.
     */
    fun put(server: ServerId, schedule: BackendSchedule?): Boolean {
        val prev = byServer[server]
        val changed = when {
            schedule == null && prev == null -> false
            schedule == null -> { byServer.remove(server); true }
            prev != schedule -> { byServer[server] = schedule; true }
            else -> false
        }
        if (changed) fireListeners()
        return changed
    }

    fun snapshot(): Map<ServerId, BackendSchedule> = byServer.toMap()

    fun subscribe(listener: (Map<ServerId, BackendSchedule>) -> Unit) {
        listeners += listener
        listener(snapshot())
    }

    private fun fireListeners() {
        val snap = snapshot()
        listeners.forEach { it(snap) }
    }
}
