package com.badgersmc.queuerestart.common.schedule

import com.badgersmc.queuerestart.common.protocol.RestartMode
import java.util.UUID

/**
 * A pending restart arm published by the proxy via SLP poll-back so a
 * companion with no online players can still discover and execute it.
 * Mirrors what `RestartNowMessage` carries on the plugin-message path.
 */
data class PendingArm(
    val delaySeconds: Int,
    val mode: RestartMode,
    val argument: String,
)

/**
 * Wire format for [PendingArm] inside an SLP `samplePlayer.name`:
 * `QR_ARM:<delaySeconds>:<mode>:<argument>`. The argument tail may
 * contain `:` (preserved verbatim).
 *
 * The marker UUID [MARKER_UUID] disambiguates the entry from real player
 * samples and from the schedule-discovery marker. The proxy strips
 * QR_POLL pings before any real client sees them, but defence in depth
 * — using a marker UUID means even a leaked sample is still identifiable
 * as out-of-band metadata rather than a real player profile.
 */
object ArmEncoding {
    const val PREFIX: String = "QR_ARM:"
    val MARKER_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-0000005152a0")

    fun encode(arm: PendingArm): String {
        require(arm.delaySeconds >= 0) { "delaySeconds must be ≥ 0" }
        return "$PREFIX${arm.delaySeconds}:${arm.mode.name}:${arm.argument}"
    }

    fun decode(name: String): PendingArm? {
        if (!name.startsWith(PREFIX)) return null
        val body = name.substring(PREFIX.length)
        val firstColon = body.indexOf(':')
        if (firstColon < 0) return null
        val delayStr = body.substring(0, firstColon)
        val rest = body.substring(firstColon + 1)
        val secondColon = rest.indexOf(':')
        if (secondColon < 0) return null
        val modeStr = rest.substring(0, secondColon)
        val argument = rest.substring(secondColon + 1)
        val delaySeconds = delayStr.toIntOrNull() ?: return null
        if (delaySeconds < 0) return null
        val mode = try {
            RestartMode.valueOf(modeStr)
        } catch (_: IllegalArgumentException) {
            return null
        }
        return PendingArm(delaySeconds, mode, argument)
    }
}
