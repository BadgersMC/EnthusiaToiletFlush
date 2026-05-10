package com.badgersmc.queuerestart.common.schedule

import com.badgersmc.queuerestart.common.protocol.RestartMode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

/**
 * REQ-022. SLP poll-back arm encoding mirrors [ScheduleEncoding] but
 * carries (delaySeconds, mode, argument) instead of times+zone+warn.
 * Distinct prefix + marker UUID so a single SLP response can carry both
 * a schedule sample and an arm sample without ambiguity.
 */
class ArmEncodingTest {

    @Test
    fun `round trips a SHUTDOWN arm with empty argument`() {
        val original = PendingArm(
            delaySeconds = 60,
            mode = RestartMode.SHUTDOWN,
            argument = "",
        )
        val encoded = ArmEncoding.encode(original)
        assertThat(encoded).isEqualTo("QR_ARM:60:SHUTDOWN:")
        assertThat(ArmEncoding.decode(encoded)).isEqualTo(original)
    }

    @Test
    fun `round trips a COMMAND arm with argument`() {
        val original = PendingArm(
            delaySeconds = 30,
            mode = RestartMode.COMMAND,
            argument = "stop",
        )
        val encoded = ArmEncoding.encode(original)
        assertThat(encoded).isEqualTo("QR_ARM:30:COMMAND:stop")
        assertThat(ArmEncoding.decode(encoded)).isEqualTo(original)
    }

    @Test
    fun `round trips an EXIT_CODE arm`() {
        val original = PendingArm(
            delaySeconds = 0,
            mode = RestartMode.EXIT_CODE,
            argument = "137",
        )
        assertThat(ArmEncoding.decode(ArmEncoding.encode(original))).isEqualTo(original)
    }

    @Test
    fun `decode rejects missing prefix`() {
        assertThat(ArmEncoding.decode("QR_SCHEDULE:nope")).isNull()
        assertThat(ArmEncoding.decode("not_a_marker")).isNull()
    }

    @Test
    fun `decode rejects malformed payload`() {
        assertThat(ArmEncoding.decode("QR_ARM:notanumber:SHUTDOWN:")).isNull()
        assertThat(ArmEncoding.decode("QR_ARM:30:NOT_A_MODE:")).isNull()
        assertThat(ArmEncoding.decode("QR_ARM:30")).isNull()
    }

    @Test
    fun `decode preserves colon characters in the argument tail`() {
        // The argument is everything after the third colon — preserve any
        // further colons verbatim so commands with their own ":" args survive.
        val decoded = ArmEncoding.decode("QR_ARM:5:COMMAND:say hello:world")
        assertThat(decoded).isEqualTo(
            PendingArm(delaySeconds = 5, mode = RestartMode.COMMAND, argument = "say hello:world")
        )
    }

    @Test
    fun `encode rejects negative delaySeconds`() {
        assertThatThrownBy {
            ArmEncoding.encode(PendingArm(-1, RestartMode.SHUTDOWN, ""))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
