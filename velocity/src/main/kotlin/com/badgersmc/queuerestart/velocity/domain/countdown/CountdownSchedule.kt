package com.badgersmc.queuerestart.velocity.domain.countdown

/**
 * A point in the countdown at which the proxy broadcasts + plays a sound.
 * `secondsRemaining == 0` is T-0 (the moment the restart fires).
 */
data class MarkSecond(val secondsRemaining: Int) {
    init {
        require(secondsRemaining >= 0) { "secondsRemaining must be ≥ 0, got $secondsRemaining" }
    }

    val isT0: Boolean get() = secondsRemaining == 0
}

/**
 * REQ-003, REQ-004.
 *
 * Decides whether a given seconds-remaining value is a configured warn mark.
 * T-0 is always a mark — operators may omit it; it's added implicitly.
 *
 * Pure domain. The infrastructure layer is responsible for driving ticks
 * and dispatching the actual chat / sound side-effects when [fireAt]
 * returns non-null.
 */
class CountdownSchedule(rawMarks: Collection<Int>) {

    /** All marks (configured + implicit T-0) in descending order. */
    val configuredMarks: List<MarkSecond>

    private val markSet: Set<Int>

    init {
        require(rawMarks.none { it < 0 }) { "marks must be ≥ 0: $rawMarks" }
        val deduped = (rawMarks.toSet() + 0).sortedDescending()
        configuredMarks = deduped.map { MarkSecond(it) }
        markSet = deduped.toSet()
    }

    /** Returns a [MarkSecond] when [secondsRemaining] is a mark, else null. */
    fun fireAt(secondsRemaining: Int): MarkSecond? =
        if (secondsRemaining >= 0 && secondsRemaining in markSet) MarkSecond(secondsRemaining) else null
}
