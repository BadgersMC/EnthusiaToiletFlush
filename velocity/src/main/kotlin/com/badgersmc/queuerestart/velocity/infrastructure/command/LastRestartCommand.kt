package com.badgersmc.queuerestart.velocity.infrastructure.command

import com.badgersmc.queuerestart.velocity.application.network.NetworkRestartService
import com.badgersmc.queuerestart.velocity.application.ports.QueueRestartConfig
import com.badgersmc.queuerestart.velocity.domain.plan.RestartPlan
import com.badgersmc.queuerestart.velocity.domain.plan.RestartTimes
import com.velocitypowered.api.command.SimpleCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.time.Duration
import java.time.Instant

/** Public summary of the most recently completed proxy and backend restarts. */
class LastRestartCommand(
    private val service: NetworkRestartService,
    private val config: () -> QueueRestartConfig,
) : SimpleCommand {
    override fun execute(invocation: SimpleCommand.Invocation) {
        val now = Instant.now()
        val rows = buildList {
            add("Proxy" to service.lastCompletedProxyRestart())
            config().networkRestart.serverIds.keys
                .sortedBy { it.value.lowercase() }
                .forEach { server -> add(server.value to service.lastCompletedServerRestart(server)) }
        }
        if (rows.all { it.second == null }) {
            invocation.source().sendMessage(Component.text("No completed restarts have been recorded yet.", NamedTextColor.GRAY))
            return
        }

        invocation.source().sendMessage(divider())
        invocation.source().sendMessage(Component.text("LAST RESTARTS", NamedTextColor.GOLD).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD))
        rows.forEach { (name, plan) -> invocation.source().sendMessage(row(name, plan, now)) }
        invocation.source().sendMessage(divider())
    }

    private fun row(name: String, plan: RestartPlan?, now: Instant): Component {
        val value = plan?.completedAt?.let { "${RestartTimes.format(Duration.between(it, now).coerceAtLeast(Duration.ZERO))} ago" }
            ?: "No recorded restart"
        val color = if (plan == null) NamedTextColor.DARK_GRAY else NamedTextColor.YELLOW
        return Component.text("• ", NamedTextColor.GOLD)
            .append(Component.text("$name: ", NamedTextColor.GRAY))
            .append(Component.text(value, color))
    }

    private fun divider(): Component = Component.text("--------------------------------------------------", NamedTextColor.DARK_GRAY)
}
