package com.badgersmc.queuerestart.paper

import com.badgersmc.queuerestart.common.schedule.BackendSchedule
import com.badgersmc.queuerestart.common.schedule.ScheduleEncoding
import com.destroystokyo.paper.event.server.PaperServerListPingEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/**
 * Injects this backend's [BackendSchedule] into every Server-List-Ping
 * response as a synthetic player-sample entry tagged with
 * [ScheduleEncoding.MARKER_UUID]. The Velocity proxy polls SLP on its own
 * cadence and pulls the schedule out of the sample list — no plugin
 * message channel and no online player required, sidestepping
 * Velocity/Bukkit's "channel needs a player" constraint.
 *
 * The marker UUID is fixed and the proxy strips entries with this id
 * before any client sees the response, so the synthetic sample never
 * leaks into a real ping reply. We register at LOWEST priority so other
 * plugins that mutate the player sample run after us and won't drop our
 * entry.
 */
class SchedulePingListener(private val schedule: BackendSchedule) : Listener {

    private val encodedName: String = ScheduleEncoding.encode(schedule)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPing(event: PaperServerListPingEvent) {
        event.listedPlayers.add(
            PaperServerListPingEvent.ListedPlayerInfo(encodedName, ScheduleEncoding.MARKER_UUID)
        )
    }
}
