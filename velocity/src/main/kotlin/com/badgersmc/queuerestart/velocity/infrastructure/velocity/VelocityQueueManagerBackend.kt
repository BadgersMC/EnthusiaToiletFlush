package com.badgersmc.queuerestart.velocity.infrastructure.velocity

import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import com.velocitypowered.api.proxy.ProxyServer
import com.velocityctd.api.queue.Queue
import com.velocityctd.api.queue.QueueEntryData
import org.slf4j.Logger

/**
 * REQ-032, REQ-033 binding. Implements [QueueManagerBackend] over CTD's
 * `ProxyServer.getQueueManager()` API. Falls back to a logging no-op when
 * the CTD queue subsystem is unavailable (running on vanilla Velocity, or
 * CTD started with queue disabled), so the proxy plugin still enables.
 *
 * Not unit-tested — exercised by the e2e runbook (`docs/e2e-runbook.md`).
 * The `QueueAdapter` unit suite covers the adapter contract; this class
 * is a thin shim against framework symbols.
 */
class VelocityQueueManagerBackend(
    private val proxy: ProxyServer,
    private val logger: Logger,
) : QueueManagerBackend {

    private val available: Boolean = probeAvailability()

    private fun probeAvailability(): Boolean = try {
        // Reachable iff (a) CTD API on classpath and (b) queue subsystem enabled.
        if (proxy.isQueueEnabled && proxy.queueManager != null) {
            logger.info("VelocityCTD queue subsystem detected; rejoin queue active.")
            true
        } else {
            logger.warn(
                "VelocityCTD queue subsystem disabled (isQueueEnabled=false); " +
                    "rejoin queue will no-op. Cohort members will reconnect without queue ordering.",
            )
            false
        }
    } catch (t: NoSuchMethodError) {
        logger.warn(
            "VelocityCTD queue API not present at runtime; rejoin queue will no-op. " +
                "Install velocity-ctd to enable rank-weighted rejoin.",
        )
        false
    } catch (t: Throwable) {
        logger.warn("VelocityCTD queue probe failed; rejoin queue will no-op.", t)
        false
    }

    override fun enqueueWithWeight(server: ServerId, player: PlayerId, weight: Int) {
        if (!available) return
        val mgr = try {
            proxy.queueManager
        } catch (t: Throwable) {
            logger.debug("queueManager unavailable on enqueue", t)
            return
        }
        val p = proxy.getPlayer(player.uuid).orElse(null)
        if (p == null) {
            logger.debug("Player {} offline; skipping enqueue to {}", player.uuid, server.value)
            return
        }
        val q: Queue? = mgr.getQueue(server.value)
        if (q == null) {
            logger.debug("No CTD queue registered for server {}; skipping enqueue", server.value)
            return
        }
        q.enqueue(QueueEntryData(player.uuid, p.username, weight, /* fullBypass */ false, /* queueBypass */ false))
    }

    override fun remove(player: PlayerId) {
        if (!available) return
        val mgr = try {
            proxy.queueManager
        } catch (t: Throwable) {
            return
        }
        mgr.removePlayerEntirely(player.uuid)
    }
}
