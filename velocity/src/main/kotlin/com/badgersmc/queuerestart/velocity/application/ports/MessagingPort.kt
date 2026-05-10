package com.badgersmc.queuerestart.velocity.application.ports

import com.badgersmc.queuerestart.common.protocol.CheckOutcome
import com.badgersmc.queuerestart.common.protocol.RestartMode
import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId

/**
 * Outbound port — sends plugin messages on channel `qrestart:v1` and
 * exposes inbound subscriptions. Implemented by
 * `infrastructure/messaging/PluginMessageAdapter`.
 */
interface MessagingPort {
    /** Send `DrainRequest` (0x01) to the named backend. */
    fun sendDrainRequest(target: ServerId)

    /**
     * Send `RestartNow` (0x10) to the named backend. The companion will
     * defer the actual shutdown by [delaySeconds] from receipt — sending
     * early (while a player is still on the target) is required because
     * Velocity drops plugin messages when no player is connected.
     */
    fun sendRestartNow(target: ServerId, mode: RestartMode, argument: String, delaySeconds: Int)

    /** Register a handler for `DrainAck` (0x02) from any backend. */
    fun onDrainAck(handler: (ServerId, Int) -> Unit)

    /** Register a handler for `CheckHacksResult` (0x20) from any backend. */
    fun onCheckHacksResult(handler: (PlayerId, CheckOutcome) -> Unit)
}
