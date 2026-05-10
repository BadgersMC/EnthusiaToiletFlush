package com.badgersmc.queuerestart.paper

import com.badgersmc.queuerestart.common.schedule.BackendSchedule
import com.badgersmc.queuerestart.common.schedule.ScheduleEncoding
import com.destroystokyo.paper.event.server.PaperServerListPingEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import java.net.InetAddress

/**
 * Injects this backend's [BackendSchedule] into Server-List-Ping responses
 * as a synthetic sample-player entry tagged with
 * [ScheduleEncoding.MARKER_UUID]. The Velocity proxy polls SLP on its own
 * cadence and pulls the schedule out of the sample list — no plugin
 * message channel and no online player required.
 *
 * SECURITY (REQ-090, finding C): the marker entry only leaks restart
 * cadence to whoever can reach this backend's Minecraft port. On panel
 * hosts the backend port is normally only reachable from the proxy host,
 * but a misconfiguration or direct port exposure would broadcast
 * restart times + zone + warn-minutes to anyone on the internet —
 * reconnaissance for timed raid attacks. We gate the listener on the
 * peer address: by default only loopback / RFC1918 / link-local peers
 * see the announcement, which covers the typical proxy-on-LAN topology.
 *
 * `allowedPeerCidrs` opt-in (loaded from config) widens this when the
 * proxy lives on a public IP. Setting it to `[0.0.0.0/0]` restores the
 * pre-patch behavior at the operator's own risk.
 */
class SchedulePingListener(
    schedule: BackendSchedule,
    private val peerFilter: PeerFilter = PeerFilter.PRIVATE_ONLY,
) : Listener {

    private val encodedName: String = ScheduleEncoding.encode(schedule)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPing(event: PaperServerListPingEvent) {
        val peer = event.client.address.address ?: return
        if (!peerFilter.allows(peer)) return
        event.listedPlayers.add(
            PaperServerListPingEvent.ListedPlayerInfo(encodedName, ScheduleEncoding.MARKER_UUID)
        )
    }

    fun interface PeerFilter {
        fun allows(peer: InetAddress): Boolean

        companion object {
            /** Loopback + private (10/8, 172.16/12, 192.168/16) + link-local. */
            val PRIVATE_ONLY: PeerFilter = PeerFilter { addr ->
                addr.isLoopbackAddress || addr.isSiteLocalAddress || addr.isLinkLocalAddress
            }

            /** Operator opt-in: announce schedule to every peer. */
            val ALL: PeerFilter = PeerFilter { true }
        }
    }
}
