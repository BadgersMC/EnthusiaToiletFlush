package com.badgersmc.queuerestart.paper

import com.badgersmc.queuerestart.common.protocol.CheckHacksResultMessage
import com.badgersmc.queuerestart.common.protocol.CheckOutcome
import java.util.UUID

/**
 * REQ-040, implementation.md §7.
 *
 * Translates the additive `me.branduzzo.checkHacks.api.CheckCompletedEvent`
 * (added in a separate PR against CheckHacks-fork — see T-042) into a
 * [CheckHacksResultMessage] suitable for the proxy. CheckHacks remains a
 * soft-depend: when the event class is absent from the classpath the
 * bridge reports unavailable and the plugin enables without it.
 */
class CheckHacksBridge(
    private val classLoader: ClassLoader = CheckHacksBridge::class.java.classLoader,
) {

    private val eventClassName = "me.branduzzo.checkHacks.api.CheckCompletedEvent"

    /** True iff CheckHacks-fork with the additive event API is installed. */
    fun isCheckHacksAvailable(): Boolean = try {
        Class.forName(eventClassName, false, classLoader)
        true
    } catch (_: ClassNotFoundException) {
        false
    }

    /**
     * Map raw CheckHacks aggregate flags to a [CheckHacksResultMessage].
     *
     * Precedence: detected > protected > clean. If none of the flags are
     * set the player neither completed nor faulted — emit a [CheckOutcome.TIMEOUT]
     * so the proxy's gate makes a deterministic decision.
     */
    fun translate(
        playerId: UUID,
        clean: Boolean,
        detected: Boolean,
        protectedFlag: Boolean,
    ): CheckHacksResultMessage {
        val outcome = when {
            detected -> CheckOutcome.DETECTED
            protectedFlag -> CheckOutcome.PROTECTED
            clean -> CheckOutcome.CLEAN
            else -> CheckOutcome.TIMEOUT
        }
        return CheckHacksResultMessage(playerId, outcome)
    }

    /**
     * Install the Bukkit listener if CheckHacks is present. Pass [register]
     * to actually wire the listener — keeps Bukkit out of unit tests.
     */
    fun installListenerIfAvailable(register: (eventClass: Class<*>) -> Unit) {
        if (!isCheckHacksAvailable()) return
        val eventClass = Class.forName(eventClassName, false, classLoader)
        register(eventClass)
    }
}
