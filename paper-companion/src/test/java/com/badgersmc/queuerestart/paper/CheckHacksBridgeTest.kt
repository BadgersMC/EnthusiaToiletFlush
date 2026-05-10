package com.badgersmc.queuerestart.paper

import com.badgersmc.queuerestart.common.protocol.CheckHacksResultMessage
import com.badgersmc.queuerestart.common.protocol.CheckOutcome
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * REQ-040, implementation.md §7.
 *
 * Translates a `CheckCompletedEvent` (from CheckHacks-fork) into a
 * `CheckHacksResultMessage`. The bridge stays reflection-safe: when the
 * CheckHacks event class is absent on the classpath the bridge reports
 * unavailable and the rest of the plugin still enables.
 */
class CheckHacksBridgeTest {

    private val bridge = CheckHacksBridge()

    @Test
    fun `clean only maps to CLEAN`() {
        val pid = UUID.randomUUID()
        val msg = bridge.translate(pid, clean = true, detected = false, protectedFlag = false)
        assertThat(msg).isEqualTo(CheckHacksResultMessage(pid, CheckOutcome.CLEAN))
    }

    @Test
    fun `detected wins over everything else`() {
        val pid = UUID.randomUUID()
        val msg = bridge.translate(pid, clean = false, detected = true, protectedFlag = true)
        assertThat(msg.outcome).isEqualTo(CheckOutcome.DETECTED)
    }

    @Test
    fun `protected without detected maps to PROTECTED`() {
        val pid = UUID.randomUUID()
        val msg = bridge.translate(pid, clean = false, detected = false, protectedFlag = true)
        assertThat(msg.outcome).isEqualTo(CheckOutcome.PROTECTED)
    }

    @Test
    fun `neither clean nor detected nor protected maps to TIMEOUT`() {
        val pid = UUID.randomUUID()
        val msg = bridge.translate(pid, clean = false, detected = false, protectedFlag = false)
        assertThat(msg.outcome).isEqualTo(CheckOutcome.TIMEOUT)
    }

    @Test
    fun `bridge reports unavailable when CheckCompletedEvent class is missing`() {
        // Real classpath here doesn't have CheckHacks installed.
        assertThat(bridge.isCheckHacksAvailable()).isFalse()
    }

    @Test
    fun `unavailable bridge does not throw on optional listener install`() {
        // Must be a safe no-op so plugin enable doesn't crash (REQ §7).
        bridge.installListenerIfAvailable(register = { _ ->
            throw AssertionError("listener install should not be invoked when class is absent")
        })
    }
}
