package com.badgersmc.queuerestart.velocity.application.schedule

import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import java.util.concurrent.ConcurrentHashMap

class BackendRestartOptions {
    private val silent = ConcurrentHashMap.newKeySet<ServerId>()
    fun setSilent(target: ServerId, value: Boolean) { if (value) silent += target else silent -= target }
    fun isSilent(target: ServerId): Boolean = target in silent
    fun clear(target: ServerId) { silent -= target }
}
