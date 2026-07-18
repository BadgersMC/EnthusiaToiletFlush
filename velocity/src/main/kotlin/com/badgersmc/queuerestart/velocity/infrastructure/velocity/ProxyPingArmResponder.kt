package com.badgersmc.queuerestart.velocity.infrastructure.velocity

import com.badgersmc.queuerestart.common.schedule.ArmEncoding
import com.badgersmc.queuerestart.common.schedule.CancelEncoding
import com.badgersmc.queuerestart.common.schedule.ProxyPollHandshake
import com.badgersmc.queuerestart.velocity.application.arm.PendingArmStore
import com.badgersmc.queuerestart.velocity.application.ports.ClockPort
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyPingEvent
import com.velocitypowered.api.proxy.server.ServerPing
import org.slf4j.Logger

/**
 * REQ-022. Listens for SLP requests carrying the magic
 * `QR_POLL:<server-id>` hostname (set by the companion's poller),
 * looks up the corresponding pending arm in [PendingArmStore], and
 * embeds the encoded arm into the response's `samplePlayers` list with
 * the [ArmEncoding.MARKER_UUID] marker.
 *
 * Non-QR_POLL pings pass through untouched. We `consume` (not `peek`)
 * the arm on read so a backend executes the restart exactly once even
 * if its poll loop fires multiple requests during arm window.
 *
 * The response is sent back over the same TCP connection that initiated
 * the SLP request and is never observed by a real client (real clients
 * never send a hostname starting with `QR_POLL:`).
 */
class ProxyPingArmResponder(
    private val store: PendingArmStore,
    private val clock: ClockPort,
    private val logger: Logger,
) {

    @Subscribe
    fun onProxyPing(event: ProxyPingEvent) {
        val rawHost = event.connection.rawVirtualHost.orElse(null) ?: return
        val serverId = ProxyPollHandshake.parseHostname(rawHost) ?: return

        val delivery = store.consumeDelivery(ServerId(serverId), clock.now())
        val sample = if (delivery == null) {
            // Empty sample — companion treats absence as "no arm pending".
            emptyList<ServerPing.SamplePlayer>()
        } else {
            val encoded = when (delivery) {
                is PendingArmStore.Delivery.Arm -> {
                    logger.info(
                        "queue-restart: SLP poll-back delivering arm to {} (delay={}s, mode={})",
                        serverId, delivery.value.delaySeconds, delivery.value.mode,
                    )
                    ArmEncoding.encode(delivery.value)
                }
                PendingArmStore.Delivery.Cancel -> {
                    logger.info("queue-restart: SLP poll-back delivering cancellation to {}", serverId)
                    CancelEncoding.VALUE
                }
            }
            listOf(ServerPing.SamplePlayer(encoded, ArmEncoding.MARKER_UUID))
        }

        val updated = event.ping.asBuilder()
            .clearSamplePlayers()
            .samplePlayers(sample)
            .build()
        event.ping = updated
    }
}
