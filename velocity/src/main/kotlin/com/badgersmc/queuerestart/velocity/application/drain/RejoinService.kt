package com.badgersmc.queuerestart.velocity.application.drain

import com.badgersmc.queuerestart.velocity.application.ports.ProxyPort
import com.badgersmc.queuerestart.velocity.application.ports.QueuePort
import com.badgersmc.queuerestart.velocity.domain.cohort.Cohort
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import com.badgersmc.queuerestart.velocity.domain.rank.RankLadder

/**
 * REQ-030, REQ-032, REQ-033, REQ-034.
 *
 * Given a cohort snapshotted at arm-time and a target server that has
 * just come back online, enqueue every still-connected member with their
 * rank-resolved weight. Offline members are silently dropped (REQ-034) —
 * the cohort is a snapshot, not a live source of truth.
 */
class RejoinService(
    private val proxy: ProxyPort,
    private val queue: QueuePort,
    private val rankLadder: RankLadder,
) {

    fun enqueueRejoin(target: ServerId, cohort: Cohort) {
        for (member in cohort.members) {
            val pid = member.playerId
            if (!proxy.isOnline(pid)) continue
            val weight = rankLadder.resolve(proxy.permissionsOf(pid))
            queue.enqueue(target, pid, weight)
        }
    }
}
