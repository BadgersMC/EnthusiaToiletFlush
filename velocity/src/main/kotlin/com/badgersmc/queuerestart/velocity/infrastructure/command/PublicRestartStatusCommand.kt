package com.badgersmc.queuerestart.velocity.infrastructure.command

import com.badgersmc.queuerestart.velocity.application.network.NetworkRestartService
import com.badgersmc.queuerestart.velocity.application.ports.QueueRestartConfig
import com.badgersmc.queuerestart.velocity.domain.plan.PlanType
import com.badgersmc.queuerestart.velocity.domain.plan.RestartTimes
import com.velocitypowered.api.command.SimpleCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.format.NamedTextColor
import java.time.Duration
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PublicRestartStatusCommand(
    private val service: NetworkRestartService,
    private val config: () -> QueueRestartConfig,
    private val scheduleView: Boolean,
) : SimpleCommand {
    private val time = DateTimeFormatter.ofPattern("h:mm a z")

    override fun execute(invocation: SimpleCommand.Invocation) {
        if (!scheduleView) next(invocation) else schedules(invocation)
    }

    private fun next(invocation: SimpleCommand.Invocation) {
        val now = Instant.now()
        val active = service.activePublicPlans().firstOrNull()
        val recurring = config().takeIf { it.networkRestart.enabled }?.schedules.orEmpty().filter { it.enabled && !it.silent }
            .map { def -> service.nextOccurrence(def, now) to label(PlanType.valueOf(def.type), def.targets.map { it.value }) }
            .minByOrNull { it.first }
        val selected = listOfNotNull(active?.let { it.executionAt to label(it.type, it.targets.map { target -> target.value }) }, recurring).minByOrNull { it.first }
        if (selected == null) {
            invocation.source().sendMessage(Component.text("No restart is scheduled.", NamedTextColor.GRAY))
            return
        }
        invocation.source().sendMessage(
            Component.text("Next restart: ", NamedTextColor.GOLD)
                .append(Component.text("${selected.second} in ${RestartTimes.format(Duration.between(now, selected.first))}.", NamedTextColor.YELLOW))
                .append(Component.text(" Click to view the schedule.", NamedTextColor.GRAY).clickEvent(ClickEvent.runCommand("/restartschedule"))),
        )
    }

    private fun schedules(invocation: SimpleCommand.Invocation) {
        val now = Instant.now()
        val definitions = config().takeIf { it.networkRestart.enabled }?.schedules.orEmpty().filter { it.enabled && !it.silent }
        if (definitions.isEmpty()) {
            invocation.source().sendMessage(Component.text("No public recurring restarts are scheduled.", NamedTextColor.GRAY))
            return
        }
        definitions.forEach { def ->
            val next = service.nextOccurrence(def, now)
            val prefix = scheduleLabel(def, PlanType.valueOf(def.type))
            val zone = ZoneId.of(def.timezone)
            invocation.source().sendMessage(Component.text("$prefix at ${next.atZone(zone).format(time)} (next in ${RestartTimes.format(Duration.between(now, next))}).", NamedTextColor.YELLOW))
        }
    }

    private fun label(type: PlanType, targets: List<String>): String = when (type) {
        PlanType.SERVER -> "${targets.firstOrNull() ?: "Server"} restarts"
        PlanType.PROXY -> "Proxy restarts"
        PlanType.NETWORK -> "Full-network restart"
    }

    private fun scheduleLabel(def: com.badgersmc.queuerestart.velocity.application.ports.ConfiguredRestartSchedule, type: PlanType): String {
        val label = label(type, def.targets.map { it.value })
        if (def.days.isEmpty() || def.days.containsAll(DayOfWeek.entries.map(DayOfWeek::name))) return "Daily $label"
        val days = DayOfWeek.entries.filter { it.name in def.days }
            .joinToString(", ") { it.name.lowercase().replaceFirstChar(Char::uppercase) }
        return "$label every $days"
    }
}
