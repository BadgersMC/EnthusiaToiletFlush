package com.badgersmc.queuerestart.common.schedule

import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeParseException
import java.util.UUID

/**
 * What a backend tells the proxy about its own restart cadence. Encoded
 * into a Server-List-Ping `samplePlayers` entry by the companion and
 * decoded by the proxy on each ping — no plugin-message channel and no
 * player connection required.
 *
 * implementation.md §6 (extension): SLP-based discovery sidesteps
 * Velocity's plugin-message-needs-a-player constraint while reusing the
 * TCP exchange Velocity already opens for liveness.
 */
data class BackendSchedule(
    val times: List<LocalTime>,
    val zone: ZoneId,
    val warnMinutes: Int,
)

/**
 * Wire format for [BackendSchedule] inside a `samplePlayer.name` field.
 * Format: `QR_SCHEDULE:<HH:mm,HH:mm,…>:<zone>:<warnMinutes>`
 *
 * The marker UUID [MARKER_UUID] disambiguates our entry from real player
 * samples; the proxy strips entries with this UUID before forwarding the
 * ping response (defence-in-depth — Velocity drops the proxy-side ping
 * before clients see it anyway).
 */
object ScheduleEncoding {
    const val PREFIX: String = "QR_SCHEDULE:"
    val MARKER_UUID: UUID = UUID.fromString("00000000-0000-0000-0000-000000515200")

    fun encode(schedule: BackendSchedule): String {
        require(schedule.warnMinutes >= 0) { "warnMinutes must be ≥ 0" }
        val timesPart = schedule.times.joinToString(",") {
            "%02d:%02d".format(it.hour, it.minute)
        }
        return "$PREFIX$timesPart:${schedule.zone.id}:${schedule.warnMinutes}"
    }

    fun decode(name: String): BackendSchedule? {
        if (!name.startsWith(PREFIX)) return null
        val body = name.substring(PREFIX.length)
        // Last two fields are zone and warnMinutes; everything before is times.
        // Zone IDs may contain ':' (none common, but be defensive) so split
        // from the right by exactly two delimiters.
        val warnIdx = body.lastIndexOf(':')
        if (warnIdx < 0) return null
        val warnPart = body.substring(warnIdx + 1)
        val zoneEnd = body.lastIndexOf(':', warnIdx - 1)
        if (zoneEnd < 0) return null
        val zonePart = body.substring(zoneEnd + 1, warnIdx)
        val timesPart = body.substring(0, zoneEnd)

        val warnMinutes = warnPart.toIntOrNull() ?: return null
        val zone = try {
            ZoneId.of(zonePart)
        } catch (_: Exception) {
            return null
        }
        val times = if (timesPart.isEmpty()) {
            emptyList()
        } else {
            timesPart.split(",").mapNotNull { entry ->
                try {
                    LocalTime.parse(entry.trim())
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
        return BackendSchedule(times, zone, warnMinutes)
    }
}
