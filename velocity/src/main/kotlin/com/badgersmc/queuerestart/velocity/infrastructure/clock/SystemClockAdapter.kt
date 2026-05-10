package com.badgersmc.queuerestart.velocity.infrastructure.clock

import com.badgersmc.queuerestart.velocity.application.ports.ClockPort
import java.time.Instant

/** Wall-clock [ClockPort] implementation. */
class SystemClockAdapter : ClockPort {
    override fun now(): Instant = Instant.now()
}
