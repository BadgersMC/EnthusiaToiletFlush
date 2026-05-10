package com.badgersmc.queuerestart.velocity.application.schedule

import com.badgersmc.queuerestart.velocity.application.ports.ProxyPort
import java.time.Duration
import java.time.Instant

/**
 * On a low-frequency tick, polls every registered backend's Server-List-Ping
 * for an embedded [com.badgersmc.queuerestart.common.schedule.BackendSchedule]
 * sample and updates the [BackendScheduleCache].
 *
 * Pings are cheap (TCP handshake + status response) and don't require a
 * player on the target — that's the whole point of using SLP for schedule
 * discovery. Cadence is intentionally slow ([pollIntervalSeconds]) since
 * backend schedules don't change often; a fresh announce shows up within
 * one poll interval of the operator editing companion config + reloading.
 *
 * Wire this onto the proxy's 1 Hz tick via [tick]; it self-rate-limits.
 */
class ScheduleDiscoveryPoller(
    private val proxy: ProxyPort,
    private val cache: BackendScheduleCache,
    private val pollIntervalSeconds: Long = 30,
) {

    private var nextPollAt: Instant? = null

    /** Drive the poller. Call from the proxy tick task. */
    fun tick(now: Instant) {
        val due = nextPollAt
        if (due != null && now.isBefore(due)) return
        nextPollAt = now.plus(Duration.ofSeconds(pollIntervalSeconds))
        for (server in proxy.registeredServerIds()) {
            val schedule = proxy.pingForSchedule(server)
            cache.put(server, schedule)
        }
    }
}
