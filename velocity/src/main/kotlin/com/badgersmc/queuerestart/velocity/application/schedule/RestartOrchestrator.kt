package com.badgersmc.queuerestart.velocity.application.schedule

import com.badgersmc.queuerestart.common.protocol.RestartMode
import com.badgersmc.queuerestart.common.schedule.PendingArm
import com.badgersmc.queuerestart.velocity.application.arm.PendingArmStore
import com.badgersmc.queuerestart.velocity.application.drain.DrainCandidate
import com.badgersmc.queuerestart.velocity.application.drain.DrainPlanner
import com.badgersmc.queuerestart.velocity.application.drain.HubResolver
import com.badgersmc.queuerestart.velocity.application.drain.RejoinService
import com.badgersmc.queuerestart.velocity.application.gate.CheckGate
import com.badgersmc.queuerestart.velocity.application.ports.AudiencePort
import com.badgersmc.queuerestart.velocity.application.ports.MessagingPort
import com.badgersmc.queuerestart.velocity.application.ports.ProxyPort
import com.badgersmc.queuerestart.velocity.application.ports.QueueRestartConfig
import com.badgersmc.queuerestart.velocity.domain.coordinator.RestartState
import com.badgersmc.queuerestart.velocity.domain.countdown.CountdownSchedule
import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import com.badgersmc.queuerestart.velocity.domain.rank.RankLadder
import java.time.Duration
import java.time.Instant

/**
 * REQ-001, REQ-010, REQ-012, REQ-020, REQ-040.
 *
 * Drives every coordinator's state machine forward on each tick.
 * Owns the per-target ephemeral state needed to translate elapsed-time
 * to the right transitions:
 *  - countdownStartedAt — when ARMED→COUNTDOWN happened
 *  - drainStartedAt — when COUNTDOWN→DRAINING happened
 *  - drainBatches / nextBatchAt — pending drain dispatch queue + cadence
 *
 * The orchestrator does NOT arm coordinators itself — that's the
 * `SchedRestartCommandHandler`'s job. Once a coordinator is in
 * [RestartState.ARMED], the next tick promotes it to COUNTDOWN and the
 * cycle proceeds automatically.
 */
class RestartOrchestrator(
    private val registry: CoordinatorRegistry,
    private val proxy: ProxyPort,
    private val messaging: MessagingPort,
    private val audience: AudiencePort,
    private val broadcaster: CountdownBroadcaster,
    private val planner: DrainPlanner,
    private val hubResolver: HubResolver,
    private val rejoin: RejoinService,
    private val gate: CheckGate,
    @Suppress("unused") private val rankLadder: RankLadder,
    private val configSupplier: () -> QueueRestartConfig,
    private val restartMode: RestartMode = RestartMode.SHUTDOWN,
    private val restartArg: String = "",
    private val pendingArmStore: PendingArmStore = PendingArmStore(),
) {

    private data class TargetState(
        var countdownStartedAt: Instant? = null,
        var drainStartedAt: Instant? = null,
        var pendingBatches: ArrayDeque<List<PlayerId>> = ArrayDeque(),
        var nextBatchAt: Instant? = null,
    )

    private val state = mutableMapOf<ServerId, TargetState>()

    /** Wires the inbound subscriptions on [MessagingPort]. Call once. */
    fun start() {
        messaging.onCheckHacksResult { player, outcome ->
            gate.onResult(player, outcome)
        }
    }

    /** Drive every coordinator forward. Caller supplies wall time. */
    fun tick(now: Instant) {
        val cfg = configSupplier()
        for ((target, coord) in registry.all()) {
            val s = state.getOrPut(target) { TargetState() }
            when (coord.state) {
                RestartState.ARMED -> tickArmed(target, coord.durationSeconds, cfg, s, now)
                RestartState.COUNTDOWN -> tickCountdown(target, coord.durationSeconds, cfg, s, now)
                RestartState.DRAINING -> tickDraining(target, cfg, s, now)
                else -> {} // RESTART_SENT/SERVER_DOWN/REJOIN_RELEASE handled by PingPoller
            }
        }
    }

    /** REQ-005. Cancels the countdown for [target] and broadcasts the cancel message. */
    fun cancel(target: ServerId) {
        val coord = registry.all()[target] ?: return
        if (coord.state != RestartState.ARMED && coord.state != RestartState.COUNTDOWN) return
        val cfg = configSupplier()
        coord.cancel()
        broadcaster.cancel(target)
        audience.broadcast(target, cfg.countdown.cancelMessage, mapOf("server" to target.value))
        state.remove(target)
        pendingArmStore.clear(target)
    }

    private fun tickArmed(
        target: ServerId,
        durationSeconds: Int,
        cfg: QueueRestartConfig,
        s: TargetState,
        now: Instant,
    ) {
        val coord = registry.get(target)
        broadcaster.register(
            target,
            CountdownSchedule(cfg.countdown.marksSeconds),
            cfg.hubServer,
        )
        s.countdownStartedAt = now
        coord.beginCountdown()
        // Belt + braces delivery: send RestartNow on the plugin-message
        // channel (REQ-020) AND publish to the SLP poll-back store (REQ-022).
        // Plugin messages need a player on the target backend; the SLP
        // path doesn't, so a console-arm with no players still reaches
        // the companion within one poll interval.
        messaging.sendRestartNow(target, restartMode, restartArg, delaySeconds = durationSeconds)
        pendingArmStore.put(
            target,
            PendingArm(durationSeconds, restartMode, restartArg),
            now = now,
        )
        // Fire the very first mark immediately if the duration matches a mark
        broadcaster.tick(target, durationSeconds)
        // If duration ≤ drain-lead (degenerate case), drop straight to drain
        if (durationSeconds <= cfg.drain.drainLeadSeconds) {
            coord.beginDrain()
            startDrain(target, cfg, s, now)
        }
    }

    private fun tickCountdown(
        target: ServerId,
        durationSeconds: Int,
        cfg: QueueRestartConfig,
        s: TargetState,
        now: Instant,
    ) {
        val coord = registry.get(target)
        val started = s.countdownStartedAt ?: return
        val elapsed = Duration.between(started, now).seconds.toInt()
        val remaining = (durationSeconds - elapsed).coerceAtLeast(0)
        broadcaster.tick(target, remaining)
        if (remaining <= cfg.drain.drainLeadSeconds) {
            coord.beginDrain()
            startDrain(target, cfg, s, now)
        }
    }

    private fun startDrain(target: ServerId, cfg: QueueRestartConfig, s: TargetState, now: Instant) {
        s.drainStartedAt = now
        // Drain whoever's actually on the target right now. Cohort is the
        // arm-time roster (REQ-030) and only governs rejoin — using it for
        // drain would skip players who joined after arming.
        val candidates = proxy.playersOn(target).map { playerId ->
            val perms = proxy.permissionsOf(playerId)
            DrainCandidate(
                playerId = playerId,
                weight = 0, // ordering only, not queue weight
                bypassDrain = "queuerestart.bypass.drain" in perms,
            )
        }
        val batches = planner.plan(candidates, cfg.drain.drainOrder, cfg.drain.batchSize)
        s.pendingBatches = ArrayDeque(batches)
        s.nextBatchAt = now
        // Dispatch first batch immediately so single-tick tests see progress
        dispatchDueBatches(target, cfg, s, now)
        // If the target is already empty (no players, empty cohort) skip straight to RESTART_SENT
        if (proxy.playersOn(target).isEmpty()) {
            sendRestart(target)
        }
    }

    private fun tickDraining(target: ServerId, cfg: QueueRestartConfig, s: TargetState, now: Instant) {
        dispatchDueBatches(target, cfg, s, now)

        val started = s.drainStartedAt ?: now
        val elapsed = Duration.between(started, now).seconds
        val empty = proxy.playersOn(target).isEmpty()
        val timedOut = elapsed >= cfg.drain.forceDrainTimeoutSeconds
        if (empty || timedOut) {
            sendRestart(target)
        }
    }

    private fun dispatchDueBatches(target: ServerId, cfg: QueueRestartConfig, s: TargetState, now: Instant) {
        val intervalMillis = (cfg.drain.batchIntervalTicks * 50L) // 1 tick = 50ms
        while (s.pendingBatches.isNotEmpty() && (s.nextBatchAt == null || !now.isBefore(s.nextBatchAt))) {
            val batch = s.pendingBatches.removeFirst()
            val hub = hubResolver.resolve(cfg.hubServer, cfg.fallbackHubs) ?: cfg.hubServer
            for (pid in batch) proxy.transferPlayer(pid, hub)
            s.nextBatchAt = now.plusMillis(intervalMillis)
        }
    }

    private fun sendRestart(target: ServerId) {
        val coord = registry.get(target)
        if (coord.state != RestartState.DRAINING) return
        // RestartNow was already shipped at countdown start (see tickArmed) —
        // the companion's local timer fires the actual shutdown. Here we just
        // advance the state machine once draining has finished so the ping
        // poller starts watching for the server to come back.
        coord.restartSent()
        state.remove(target)
    }

    /** Called by PingPoller after coord.serverUp(). */
    fun finishRejoin(target: ServerId) {
        val coord = registry.get(target)
        if (coord.state != RestartState.REJOIN_RELEASE) return
        coord.cohort?.let { rejoin.enqueueRejoin(target, it) }
        coord.releaseComplete()
    }
}
