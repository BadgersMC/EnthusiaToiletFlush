package com.badgersmc.queuerestart.velocity.application.arm

import com.badgersmc.queuerestart.common.schedule.PendingArm
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import java.time.Duration
import java.time.Instant

/**
 * REQ-022. Per-server slot holding the latest pending arm published by
 * the orchestrator for SLP-poll-back delivery to a companion that has no
 * online players.
 *
 * Single slot per server (not a queue) — only the most recent arm
 * matters; an /qrestart cancel clears the slot, a re-arm overwrites.
 * Entries expire after [ttl] to bound risk of a stale arm firing on a
 * backend that polls late.
 */
class PendingArmStore(
    private val ttl: Duration = Duration.ofSeconds(60),
) {

    private data class Entry(val arm: PendingArm, val expiresAt: Instant)

    private val slots = mutableMapOf<ServerId, Entry>()

    fun put(serverId: ServerId, arm: PendingArm, now: Instant) {
        slots[serverId] = Entry(arm, now.plus(ttl))
    }

    fun peek(serverId: ServerId, now: Instant): PendingArm? {
        val entry = slots[serverId] ?: return null
        if (now.isAfter(entry.expiresAt)) {
            slots.remove(serverId)
            return null
        }
        return entry.arm
    }

    fun consume(serverId: ServerId, now: Instant): PendingArm? {
        val arm = peek(serverId, now) ?: return null
        slots.remove(serverId)
        return arm
    }

    fun clear(serverId: ServerId) {
        slots.remove(serverId)
    }
}
