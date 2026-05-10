package com.badgersmc.queuerestart.velocity.application.arm

import com.badgersmc.queuerestart.common.schedule.PendingArm
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

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

    // SECURITY (REQ-090): written by the orchestrator on the proxy tick
    // thread, read+cleared by ProxyPingArmResponder on the Velocity event
    // thread. A plain HashMap risks CME, lost arms, and a double-fire
    // race where two threads consume the same entry.
    private val slots = ConcurrentHashMap<ServerId, Entry>()

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
        // Atomic remove so two concurrent consumers can't both read the
        // same arm and both schedule a shutdown (#4 race).
        val entry = slots.remove(serverId) ?: return null
        return if (now.isAfter(entry.expiresAt)) null else entry.arm
    }

    fun clear(serverId: ServerId) {
        slots.remove(serverId)
    }
}
