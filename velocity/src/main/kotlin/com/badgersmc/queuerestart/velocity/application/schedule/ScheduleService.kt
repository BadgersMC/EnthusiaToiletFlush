package com.badgersmc.queuerestart.velocity.application.schedule

import com.badgersmc.queuerestart.velocity.application.ports.SchedulerPort
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import java.time.ZoneId

/**
 * One configured cron schedule. Cron parsing happens in the infrastructure
 * adapter ([SchedulerPort] impl) — the application layer keeps the
 * expression as an opaque string.
 *
 * [zone] disambiguates the cron expression — defaults to the proxy's
 * system zone, but the SLP-discovered schedules pass the backend's
 * advertised zone so cron matching honours backend-local time.
 */
data class ScheduleDefinition(
    val name: String,
    val target: ServerId,
    val cronExpression: String,
    val warnMinutes: Int,
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    init {
        require(name.isNotBlank()) { "schedule name must be non-blank" }
        require(warnMinutes > 0) { "warnMinutes must be > 0, got $warnMinutes" }
        require(cronExpression.isNotBlank()) { "cronExpression must be non-blank" }
    }
}

/**
 * REQ-002, REQ-050, REQ-051.
 *
 * Owns the registry of named cron schedules. Reload (REQ-050) cancels
 * existing scheduler bindings and replaces them — it does NOT touch
 * RestartCoordinator state, so an in-flight countdown survives a reload.
 *
 * Lookup by name (REQ-051) supports `/qrestart trigger <name>` flows.
 */
class ScheduleService(
    private val scheduler: SchedulerPort,
    private val onTrigger: (ScheduleDefinition) -> Unit,
) {

    private val byName: MutableMap<String, ScheduleDefinition> = linkedMapOf()

    fun loadAll(definitions: List<ScheduleDefinition>) {
        val seen = mutableSetOf<String>()
        for (def in definitions) {
            require(seen.add(def.name)) { "duplicate schedule name: ${def.name}" }
        }
        byName.clear()
        for (def in definitions) {
            byName[def.name] = def
            scheduler.schedule(def, onTrigger)
        }
    }

    fun reload(definitions: List<ScheduleDefinition>) {
        scheduler.cancelAll()
        loadAll(definitions)
    }

    fun findByName(name: String): ScheduleDefinition? = byName[name]

    fun all(): Collection<ScheduleDefinition> = byName.values
}
