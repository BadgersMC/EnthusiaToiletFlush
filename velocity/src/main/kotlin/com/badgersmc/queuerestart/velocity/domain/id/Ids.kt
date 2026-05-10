package com.badgersmc.queuerestart.velocity.domain.id

import java.util.UUID

/** A backend server name as registered with the Velocity proxy. */
@JvmInline
value class ServerId(val value: String) {
    init {
        require(value.isNotBlank()) { "ServerId must be non-blank" }
    }
}

/** A player's UUID — opaque to domain logic. */
@JvmInline
value class PlayerId(val uuid: UUID)
