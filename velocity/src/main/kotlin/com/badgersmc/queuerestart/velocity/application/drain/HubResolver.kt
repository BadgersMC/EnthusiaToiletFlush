package com.badgersmc.queuerestart.velocity.application.drain

import com.badgersmc.queuerestart.velocity.application.ports.ProxyPort
import com.badgersmc.queuerestart.velocity.domain.id.ServerId

/**
 * REQ-013.
 *
 * Picks the first reachable hub from `[primary, ...fallbacks]`. Returns
 * null when nothing is reachable — the caller decides how to surface that
 * (typically: abort the drain and log).
 */
class HubResolver(private val proxy: ProxyPort) {

    fun resolve(primary: ServerId, fallbacks: List<ServerId>): ServerId? {
        if (proxy.isReachable(primary)) return primary
        for (fb in fallbacks) {
            if (proxy.isReachable(fb)) return fb
        }
        return null
    }
}
