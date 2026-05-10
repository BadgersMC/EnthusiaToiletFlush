package com.badgersmc.queuerestart.velocity.infrastructure.messaging

import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.PluginMessageEvent
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.ServerConnection
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier

/**
 * implementation.md §6.
 *
 * Wires Velocity's plugin-message API to the [PluginMessageAdapter].
 * - outbound: `RegisteredServer.sendPluginMessage(channel, payload)`
 * - inbound: `PluginMessageEvent` from a backend → forward bytes to
 *   the adapter, then `forwarded()` so vanilla doesn't see them.
 *
 * Register an instance via `proxyServer.eventManager.register(plugin, this)`.
 */
class VelocityChannelTransport(
    private val proxyServer: ProxyServer,
    private val adapter: PluginMessageAdapter,
) : PluginMessageTransport {

    val channelIdentifier: MinecraftChannelIdentifier =
        MinecraftChannelIdentifier.from(CHANNEL)

    override fun send(target: ServerId, payload: ByteArray) {
        val server = proxyServer.getServer(target.value).orElse(null) ?: return
        server.sendPluginMessage(channelIdentifier, payload)
    }

    @Subscribe
    fun onPluginMessage(event: PluginMessageEvent) {
        if (event.identifier != channelIdentifier) return
        val source = event.source as? ServerConnection ?: return
        val serverId = ServerId(source.serverInfo.name)
        adapter.handleInbound(serverId, event.data)
        event.result = PluginMessageEvent.ForwardResult.handled()
    }

    companion object {
        const val CHANNEL: String = "qrestart:v1"
    }
}
