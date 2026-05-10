package com.badgersmc.queuerestart.velocity.application.ports

import com.badgersmc.queuerestart.velocity.application.drain.DrainOrder
import com.badgersmc.queuerestart.velocity.domain.id.ServerId

/**
 * Outbound port — typed access to `config.yml` after parsing. Adapter:
 * `infrastructure/config/ConfigurateConfigAdapter`.
 */
interface ConfigPort {
    fun snapshot(): QueueRestartConfig
    fun reload()
}

data class QueueRestartConfig(
    val hubServer: ServerId,
    val fallbackHubs: List<ServerId>,
    val drain: DrainConfig,
    val rejoin: RejoinConfig,
    val countdown: CountdownConfig,
    val sounds: Map<String, SoundCue>,
    val rankLadder: Map<String, Int>,
    val rankDefault: Int,
)

data class DrainConfig(
    val batchSize: Int,
    val batchIntervalTicks: Int,
    val drainLeadSeconds: Int,
    val forceDrainTimeoutSeconds: Int,
    val drainOrder: DrainOrder,
)

data class RejoinConfig(
    val enabled: Boolean,
    val enqueueOnServerUp: Boolean,
    val releaseOnCheckhacksCleared: Boolean,
    val checkGateTimeoutSeconds: Int,
    val releaseOnTimeout: Boolean,
    val pingPollSeconds: Int,
)

data class CountdownConfig(
    val marksSeconds: List<Int>,
    val message: String,
    val messageT0: String,
    val cancelMessage: String,
)

data class SoundCue(val key: String, val volume: Float, val pitch: Float) {
    init {
        require(volume in 0f..1f) {
            "sound volume must be in [0.0, 1.0]; got $volume (key=$key)"
        }
    }
}
