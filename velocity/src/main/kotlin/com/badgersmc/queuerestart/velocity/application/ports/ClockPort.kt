package com.badgersmc.queuerestart.velocity.application.ports

import java.time.Instant

/**
 * Outbound port — wall clock. Implemented by
 * `infrastructure/clock/SystemClockAdapter`. Exists so the application
 * layer can be ticked under a fake clock in tests.
 */
fun interface ClockPort {
    fun now(): Instant
}
