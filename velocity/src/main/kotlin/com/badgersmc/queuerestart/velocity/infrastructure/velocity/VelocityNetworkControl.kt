package com.badgersmc.queuerestart.velocity.infrastructure.velocity

import com.badgersmc.queuerestart.velocity.application.ports.NetworkControlPort
import com.badgersmc.queuerestart.velocity.application.ports.RestartNotice
import com.badgersmc.queuerestart.velocity.application.ports.TransferSummary
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.ResultedEvent
import com.velocitypowered.api.event.connection.LoginEvent
import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.server.RegisteredServer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class VelocityNetworkControl(private val proxy: ProxyServer) : NetworkControlPort {
    @Volatile private var maintenanceUntil: Instant? = null

    override fun broadcast(notice: RestartNotice) {
        val component = noticeComponent(notice)
        proxy.allPlayers.forEach { it.sendMessage(component) }
    }

    override fun disconnectAll(notice: RestartNotice) {
        val component = disconnectComponent(notice)
        proxy.allPlayers.forEach { it.disconnect(component) }
    }

    override fun transferAll(from: ServerId, destinations: List<ServerId>): CompletionStage<TransferSummary> {
        val players = proxy.getServer(from.value).orElse(null)?.playersConnected?.toList().orEmpty()
        val targets = destinations.mapNotNull { proxy.getServer(it.value).orElse(null) }
        val work = players.map { player -> move(player, targets) }
        return CompletableFuture.allOf(*work.toTypedArray()).thenApply {
            val results = work.map { it.getNow(TransferResult(false, true)) }
            TransferSummary(results.count { it.moved }, results.count { it.disconnected }, results.count { !it.moved && !it.disconnected })
        }
    }

    override fun setMaintenance(enabled: Boolean, duration: Duration) {
        maintenanceUntil = if (enabled) Instant.now().plus(duration) else null
    }

    override fun maintenanceActive(): Boolean = maintenanceUntil?.isAfter(Instant.now()) == true

    @Subscribe fun onLogin(event: LoginEvent) {
        if (!maintenanceActive()) return
        if (event.player.hasPermission("queuerestart.bypass.maintenance")) return
        event.result = ResultedEvent.ComponentResult.denied(
            Component.text("Network restart in progress", NamedTextColor.RED)
                .append(Component.newline())
                .append(Component.text("Please reconnect shortly.", NamedTextColor.GRAY)),
        )
    }

    private fun move(player: Player, targets: List<RegisteredServer>): CompletableFuture<TransferResult> {
        fun next(index: Int): CompletableFuture<TransferResult> {
            if (index >= targets.size) {
                player.disconnect(Component.text("Server restarting", NamedTextColor.RED).append(Component.newline()).append(Component.text("Please reconnect shortly.", NamedTextColor.GRAY)))
                return CompletableFuture.completedFuture(TransferResult(false, true))
            }
            return player.createConnectionRequest(targets[index]).connect().toCompletableFuture().thenCompose { result ->
                if (result.isSuccessful) CompletableFuture.completedFuture(TransferResult(true, false)) else next(index + 1)
            }
        }
        return next(0)
    }

    private fun noticeComponent(notice: RestartNotice): Component {
        if (notice.urgent) return prefix(notice).append(Component.text(notice.detail, NamedTextColor.RED, TextDecoration.BOLD))
        var output = Component.text("--------------------------------------------------", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH)
            .append(Component.newline())
            .append(prefix(notice)).append(Component.text(notice.heading, NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.newline())
            .append(prefix(notice)).append(Component.text(notice.detail, NamedTextColor.GRAY))
            .append(Component.newline())
            .append(prefix(notice)).append(Component.text(notice.warning, NamedTextColor.GRAY))
        if (notice.reason.isNotBlank()) output = output.append(Component.newline()).append(prefix(notice)).append(Component.text("Reason: ${notice.reason}", NamedTextColor.YELLOW))
        return output.append(Component.newline()).append(Component.text("--------------------------------------------------", NamedTextColor.DARK_GRAY, TextDecoration.STRIKETHROUGH))
    }

    private fun disconnectComponent(notice: RestartNotice): Component {
        var output = Component.text(notice.heading, NamedTextColor.RED).append(Component.newline())
            .append(Component.text(notice.detail, NamedTextColor.GRAY)).append(Component.newline())
            .append(Component.text(notice.warning, NamedTextColor.GRAY))
        if (notice.reason.isNotBlank()) output = output.append(Component.newline()).append(Component.text("Reason: ${notice.reason}", NamedTextColor.YELLOW))
        return output
    }

    private fun prefix(notice: RestartNotice): Component = Component.text(if (notice.type == "SERVER") "[Server] " else "[Network] ", NamedTextColor.GOLD)
    private data class TransferResult(val moved: Boolean, val disconnected: Boolean)
}
