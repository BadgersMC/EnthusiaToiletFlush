package com.badgersmc.queuerestart.velocity.domain.rank

/**
 * Maps Minecraft/LuckPerms permission nodes to integer queue weights.
 *
 * Resolution rule (REQ-033): highest-weight matching permission wins;
 * missing → [default]; on ties, the entry declared first in [entries] wins
 * (stable). Pass a [LinkedHashMap] to preserve declaration order.
 *
 * Pure domain — no framework imports. Permissions are opaque strings; the
 * caller is responsible for evaluating which permissions a player holds.
 */
class RankLadder(
    entries: Map<String, Int>,
    val default: Int,
) {

    /** Permission → weight, keyed in declaration order for stable ties. */
    private val ordered: List<RankEntry> =
        entries.entries.map { RankEntry(it.key, it.value) }

    /** All entries in declaration order. */
    val allEntries: List<RankEntry> get() = ordered

    /**
     * Returns the weight of the highest-ranked matching permission, or
     * [default] if no permission in [held] matches a ladder entry.
     */
    fun resolve(held: Set<String>): Int =
        resolveEntry(held)?.weight ?: default

    /**
     * Same as [resolve], but returns the matching [RankEntry] for callers
     * that need the permission node — null when nothing matches.
     */
    fun resolveEntry(held: Set<String>): RankEntry? {
        var best: RankEntry? = null
        for (entry in ordered) {
            if (entry.permission !in held) continue
            if (best == null || entry.weight > best.weight) {
                best = entry
            }
        }
        return best
    }
}

data class RankEntry(val permission: String, val weight: Int)
