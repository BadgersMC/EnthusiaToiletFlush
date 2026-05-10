package com.badgersmc.queuerestart.velocity.infrastructure.command

import com.badgersmc.queuerestart.velocity.application.schedule.SchedCommandResult
import com.badgersmc.queuerestart.velocity.application.schedule.SchedRestartCommandHandler
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.tree.LiteralCommandNode
import com.velocitypowered.api.command.BrigadierCommand
import com.velocitypowered.api.command.CommandSource
import com.velocitypowered.api.proxy.Player
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

/**
 * Brigadier shim for `/schedrestart`. Parses the arg tree, gates on
 * `queuerestart.command.schedrestart`, forwards to
 * [SchedRestartCommandHandler], renders the [SchedCommandResult] back to
 * the source via Adventure.
 *
 * Tree:
 * ```
 *  /schedrestart cancel [server]
 *  /schedrestart status
 *  /schedrestart <minutes>
 *  /schedrestart <minutes> <server>
 * ```
 *
 * Default-server resolution: when no `[server]` arg is supplied, the
 * executor's current backend is used (Player). The console MUST supply
 * an explicit server.
 */
class SchedRestartCommand(
    private val handler: SchedRestartCommandHandler,
) {
    companion object {
        const val PERMISSION = "queuerestart.command.schedrestart"
        const val LITERAL = "schedrestart"
    }

    fun build(): BrigadierCommand {
        val root = BrigadierCommand.literalArgumentBuilder(LITERAL)
            .requires { it.hasPermission(PERMISSION) }
            .executes { ctx ->
                send(ctx, "&cUsage: /$LITERAL <minutes> [server] | cancel [server] | status")
                0
            }
            // /schedrestart status
            .then(
                BrigadierCommand.literalArgumentBuilder("status")
                    .executes { ctx ->
                        renderStatus(ctx, handler.status())
                        1
                    },
            )
            // /schedrestart cancel [server]
            .then(
                BrigadierCommand.literalArgumentBuilder("cancel")
                    .executes { ctx ->
                        val target = resolveTarget(ctx, explicit = null) ?: return@executes 0
                        renderResult(ctx, handler.cancel(target))
                        1
                    }
                    .then(
                        BrigadierCommand.requiredArgumentBuilder<String>(
                            "server",
                            StringArgumentType.word(),
                        )
                            .executes { ctx ->
                                val target = resolveTarget(ctx, explicit = StringArgumentType.getString(ctx, "server"))
                                    ?: return@executes 0
                                renderResult(ctx, handler.cancel(target))
                                1
                            },
                    ),
            )
            // /schedrestart <minutes> [server]
            .then(
                BrigadierCommand.requiredArgumentBuilder<Int>(
                    "minutes",
                    IntegerArgumentType.integer(1),
                )
                    .executes { ctx ->
                        val minutes = IntegerArgumentType.getInteger(ctx, "minutes")
                        val target = resolveTarget(ctx, explicit = null) ?: return@executes 0
                        renderResult(ctx, handler.arm(target, minutes))
                        1
                    }
                    .then(
                        BrigadierCommand.requiredArgumentBuilder<String>(
                            "server",
                            StringArgumentType.word(),
                        )
                            .executes { ctx ->
                                val minutes = IntegerArgumentType.getInteger(ctx, "minutes")
                                val target = resolveTarget(ctx, explicit = StringArgumentType.getString(ctx, "server"))
                                    ?: return@executes 0
                                renderResult(ctx, handler.arm(target, minutes))
                                1
                            },
                    ),
            )

        val node: LiteralCommandNode<CommandSource> = root.build()
        return BrigadierCommand(node)
    }

    private fun resolveTarget(ctx: CommandContext<CommandSource>, explicit: String?): ServerId? {
        if (explicit != null) return ServerId(explicit)
        val source = ctx.source
        if (source is Player) {
            val current = source.currentServer.orElse(null)
            if (current != null) return ServerId(current.serverInfo.name)
        }
        send(ctx, "&cConsole must specify a target server: /$LITERAL <minutes> <server>")
        return null
    }

    private fun renderResult(ctx: CommandContext<CommandSource>, result: SchedCommandResult) {
        when (result) {
            is SchedCommandResult.Armed ->
                send(ctx, "&aArmed restart for &f${result.server.value}&a — countdown ${result.durationSeconds}s.")
            is SchedCommandResult.Cancelled ->
                send(ctx, "&aCancelled restart for &f${result.server.value}&a.")
            is SchedCommandResult.Rejected ->
                send(ctx, "&cRejected: ${result.reason}")
            is SchedCommandResult.Status -> renderStatus(ctx, result)
        }
    }

    private fun renderStatus(ctx: CommandContext<CommandSource>, result: SchedCommandResult) {
        val status = result as? SchedCommandResult.Status ?: return
        if (status.states.isEmpty()) {
            send(ctx, "&7No coordinators armed.")
            return
        }
        ctx.source.sendMessage(
            Component.text("Coordinator states:", NamedTextColor.GRAY),
        )
        status.states.forEach { (server, state) ->
            ctx.source.sendMessage(
                Component.text(" • ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(server.value, NamedTextColor.WHITE))
                    .append(Component.text(" → ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(state.name, NamedTextColor.AQUA)),
            )
        }
    }

    private fun send(ctx: CommandContext<CommandSource>, legacy: String) {
        // Legacy &-codes → MiniMessage-compatible Adventure components.
        val component = legacy
            .replace("&a", "<green>")
            .replace("&c", "<red>")
            .replace("&f", "<white>")
            .replace("&7", "<gray>")
        ctx.source.sendRichMessage(component)
    }
}
