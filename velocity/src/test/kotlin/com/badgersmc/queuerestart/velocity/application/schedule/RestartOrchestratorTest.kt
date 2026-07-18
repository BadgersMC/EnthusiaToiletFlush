package com.badgersmc.queuerestart.velocity.application.schedule

import com.badgersmc.queuerestart.common.protocol.CheckOutcome
import com.badgersmc.queuerestart.common.protocol.RestartMode
import com.badgersmc.queuerestart.velocity.application.drain.DrainOrder
import com.badgersmc.queuerestart.velocity.application.drain.DrainPlanner
import com.badgersmc.queuerestart.velocity.application.drain.HubResolver
import com.badgersmc.queuerestart.velocity.application.drain.RejoinService
import com.badgersmc.queuerestart.velocity.application.gate.CheckGate
import com.badgersmc.queuerestart.velocity.application.ports.AudiencePort
import com.badgersmc.queuerestart.velocity.application.ports.CountdownConfig
import com.badgersmc.queuerestart.velocity.application.ports.DrainConfig
import com.badgersmc.queuerestart.velocity.application.ports.MessagingPort
import com.badgersmc.queuerestart.velocity.application.ports.ProxyPort
import com.badgersmc.queuerestart.velocity.application.ports.QueuePort
import com.badgersmc.queuerestart.velocity.application.ports.QueueRestartConfig
import com.badgersmc.queuerestart.velocity.application.ports.RejoinConfig
import com.badgersmc.queuerestart.velocity.application.ports.SoundCue
import com.badgersmc.queuerestart.velocity.domain.cohort.Cohort
import com.badgersmc.queuerestart.velocity.domain.cohort.CohortMember
import com.badgersmc.queuerestart.velocity.domain.coordinator.RestartState
import com.badgersmc.queuerestart.velocity.domain.id.PlayerId
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import com.badgersmc.queuerestart.velocity.domain.rank.RankLadder
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

/**
 * REQ-001, REQ-010, REQ-012, REQ-020, REQ-040.
 *
 * Drives every coordinator's state machine forward on each tick.
 */
class RestartOrchestratorTest {

    private val survival = ServerId("survival")
    private val hub = ServerId("lobby")

    private fun pid(name: String) = PlayerId(UUID.nameUUIDFromBytes(name.toByteArray()))

    private class FakeProxy(
        var playersOnTarget: MutableSet<PlayerId> = mutableSetOf(),
        var reachable: MutableSet<ServerId> = mutableSetOf(),
        val perms: MutableMap<PlayerId, Set<String>> = mutableMapOf(),
        val transfers: MutableList<Pair<PlayerId, ServerId>> = mutableListOf(),
    ) : ProxyPort {
        override fun isOnline(playerId: PlayerId) = true
        override fun permissionsOf(playerId: PlayerId) = perms[playerId] ?: emptySet()
        override fun isReachable(serverId: ServerId) = serverId in reachable
        override fun playersOn(serverId: ServerId) = playersOnTarget.toSet()
        override fun transferPlayer(playerId: PlayerId, target: ServerId) {
            transfers += playerId to target
            playersOnTarget.remove(playerId)
        }
        override fun registeredServerIds(): Set<ServerId> = reachable.toSet()
        override fun pingForSchedule(serverId: ServerId): com.badgersmc.queuerestart.common.schedule.BackendSchedule? = null
    }

    private class FakeMessaging : MessagingPort {
        data class RestartCall(val server: ServerId, val mode: RestartMode, val arg: String, val delaySeconds: Int)
        val drainSent = mutableListOf<ServerId>()
        val restartSent = mutableListOf<RestartCall>()
        val cancelSent = mutableListOf<ServerId>()
        var checkResultHandler: ((ServerId, PlayerId, CheckOutcome) -> Unit)? = null
        var drainAckHandler: ((ServerId, Int) -> Unit)? = null
        override fun sendDrainRequest(target: ServerId) { drainSent += target }
        override fun sendRestartNow(target: ServerId, mode: RestartMode, argument: String, delaySeconds: Int) {
            restartSent += RestartCall(target, mode, argument, delaySeconds)
        }
        override fun sendRestartCancel(target: ServerId) { cancelSent += target }
        override fun onDrainAck(handler: (ServerId, Int) -> Unit) { drainAckHandler = handler }
        override fun onCheckHacksResult(handler: (ServerId, PlayerId, CheckOutcome) -> Unit) {
            checkResultHandler = handler
        }
    }

    private class FakeAudience : AudiencePort {
        val broadcasts = mutableListOf<String>()
        override fun broadcast(target: ServerId, miniMessage: String, placeholders: Map<String, String>) {
            broadcasts += miniMessage
        }
        override fun playSound(target: ServerId, cue: SoundCue) {}
    }

    private class FakeQueue : QueuePort {
        override fun enqueue(serverId: ServerId, playerId: PlayerId, weight: Int) {}
        override fun remove(playerId: PlayerId) {}
    }

    private fun config(
        drainLead: Int = 30,
        forceTimeout: Int = 120,
        batchSize: Int = 10,
        batchInterval: Int = 40,
    ) = QueueRestartConfig(
        hubServer = hub,
        fallbackHubs = emptyList(),
        drain = DrainConfig(batchSize, batchInterval, drainLead, forceTimeout, DrainOrder.PRIORITY_ASC),
        rejoin = RejoinConfig(true, true, true, 60, true, 3),
        countdown = CountdownConfig(listOf(60, 30, 10, 5, 1), "<gold>warn", "<red>now", "<green>cancel"),
        sounds = emptyMap(),
        rankLadder = emptyMap(),
        rankDefault = 0,
    )

    private fun setup(
        cfg: QueueRestartConfig = config(),
        proxy: FakeProxy = FakeProxy(reachable = mutableSetOf(hub)),
        messaging: FakeMessaging = FakeMessaging(),
        audience: FakeAudience = FakeAudience(),
        pendingArmStore: com.badgersmc.queuerestart.velocity.application.arm.PendingArmStore =
            com.badgersmc.queuerestart.velocity.application.arm.PendingArmStore(),
    ): Pair<RestartOrchestrator, Bundle> {
        val registry = CoordinatorRegistry()
        val broadcaster = CountdownBroadcaster(
            audience = audience,
            messageTemplate = cfg.countdown.message,
            t0Template = cfg.countdown.messageT0,
            soundResolver = { null },
        )
        val gate = CheckGate(timeoutSeconds = 60, releaseOnTimeout = true)
        val rejoin = RejoinService(proxy, FakeQueue(), RankLadder(emptyMap(), 0), gate)
        val orch = RestartOrchestrator(
            registry = registry,
            proxy = proxy,
            messaging = messaging,
            audience = audience,
            broadcaster = broadcaster,
            planner = DrainPlanner(),
            hubResolver = HubResolver(proxy),
            rejoin = rejoin,
            gate = gate,
            rankLadder = RankLadder(emptyMap(), 0),
            configSupplier = { cfg },
            restartMode = RestartMode.SHUTDOWN,
            restartArg = "",
            pendingArmStore = pendingArmStore,
        )
        orch.start()
        return orch to Bundle(registry, proxy, messaging, audience, gate, pendingArmStore)
    }

    private data class Bundle(
        val registry: CoordinatorRegistry,
        val proxy: FakeProxy,
        val messaging: FakeMessaging,
        val audience: FakeAudience,
        val gate: CheckGate,
        val pendingArmStore: com.badgersmc.queuerestart.velocity.application.arm.PendingArmStore,
    )

    private fun cohort(vararg names: String) = Cohort(names.map { CohortMember(pid(it)) }.toSet())

    @Test
    fun `armed tick publishes pending arm to store for SLP poll-back (REQ-022)`() {
        val (orch, b) = setup()
        b.registry.get(survival).arm(cohort("alice"), durationSeconds = 60)

        val now = Instant.parse("2026-01-01T00:00:00Z")
        orch.tick(now)

        assertThat(b.pendingArmStore.peek(survival, now = now))
            .isEqualTo(com.badgersmc.queuerestart.common.schedule.PendingArm(60, RestartMode.SHUTDOWN, ""))
    }

    @Test
    fun `cancel replaces the pending arm with a poll-back tombstone (REQ-022)`() {
        val (orch, b) = setup()
        b.registry.get(survival).arm(cohort("alice"), durationSeconds = 60)
        val now = Instant.parse("2026-01-01T00:00:00Z")
        orch.tick(now)
        assertThat(b.pendingArmStore.peek(survival, now = now)).isNotNull

        orch.cancel(survival, now)

        assertThat(b.pendingArmStore.peek(survival, now = now)).isNull()
        assertThat(b.pendingArmStore.consumeDelivery(survival, now = now))
            .isEqualTo(com.badgersmc.queuerestart.velocity.application.arm.PendingArmStore.Delivery.Cancel)
        assertThat(b.messaging.cancelSent).containsExactly(survival)
    }

    @Test
    fun `tick advances ARMED to COUNTDOWN and registers broadcaster`() {
        val (orch, b) = setup()
        b.registry.get(survival).arm(cohort("alice"), durationSeconds = 60)

        val now = Instant.parse("2026-01-01T00:00:00Z")
        orch.tick(now)

        assertThat(b.registry.get(survival).state).isEqualTo(RestartState.COUNTDOWN)
    }

    @Test
    fun `COUNTDOWN tick fires broadcaster at marks`() {
        val (orch, b) = setup()
        b.registry.get(survival).arm(cohort("alice"), durationSeconds = 60)

        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        orch.tick(t0) // ARMED→COUNTDOWN, broadcaster registered, secondsRemaining=60 broadcast
        assertThat(b.audience.broadcasts).hasSize(1) // 60s mark

        orch.tick(t0.plusSeconds(30)) // secondsRemaining=30 → mark
        assertThat(b.audience.broadcasts).hasSize(2)

        orch.tick(t0.plusSeconds(31)) // secondsRemaining=29 → not a mark
        assertThat(b.audience.broadcasts).hasSize(2)
    }

    @Test
    fun `COUNTDOWN transitions to DRAINING at drain-lead-seconds (REQ-010)`() {
        // alice + lurker are on the target. lurker holds the drain-bypass
        // permission so drain leaves them behind — DRAINING doesn't
        // collapse straight to RESTART_SENT.
        val proxy = FakeProxy(
            playersOnTarget = mutableSetOf(pid("alice"), pid("lurker")),
            reachable = mutableSetOf(hub),
        )
        proxy.perms[pid("lurker")] = setOf("queuerestart.bypass.drain")
        val (orch, b) = setup(cfg = config(drainLead = 30), proxy = proxy)
        b.registry.get(survival).arm(cohort("alice"), durationSeconds = 60)

        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        orch.tick(t0) // ARMED→COUNTDOWN
        assertThat(b.registry.get(survival).state).isEqualTo(RestartState.COUNTDOWN)

        orch.tick(t0.plusSeconds(30)) // secondsRemaining=30 ≤ drainLead → DRAINING
        assertThat(b.registry.get(survival).state).isEqualTo(RestartState.DRAINING)
    }

    @Test
    fun `DRAINING transfers batches of players to the hub`() {
        val proxy = FakeProxy(
            playersOnTarget = mutableSetOf(pid("a"), pid("b"), pid("c")),
            reachable = mutableSetOf(hub),
        )
        val (orch, b) = setup(cfg = config(drainLead = 30, batchSize = 2), proxy = proxy)
        b.registry.get(survival).arm(cohort("a", "b", "c"), durationSeconds = 60)

        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        orch.tick(t0) // ARMED→COUNTDOWN
        orch.tick(t0.plusSeconds(30)) // COUNTDOWN→DRAINING + first batch (2 players)

        assertThat(proxy.transfers).hasSize(2)
        assertThat(proxy.transfers.map { it.second }).allMatch { it == hub }
    }

    @Test
    fun `DRAINING sends RestartNow when target is empty (REQ-020)`() {
        val proxy = FakeProxy(reachable = mutableSetOf(hub))
        val (orch, b) = setup(cfg = config(drainLead = 30), proxy = proxy)
        b.registry.get(survival).arm(Cohort(emptySet()), durationSeconds = 60)

        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        orch.tick(t0)
        orch.tick(t0.plusSeconds(30)) // → DRAINING, no players → restart immediately

        assertThat(b.messaging.restartSent).hasSize(1)
        assertThat(b.messaging.restartSent[0].server).isEqualTo(survival)
        assertThat(b.registry.get(survival).state).isEqualTo(RestartState.RESTART_SENT)
    }

    @Test
    fun `DRAINING force-timeout fires RestartNow even with players still present (REQ-012)`() {
        val proxy = FakeProxy(
            playersOnTarget = mutableSetOf(pid("stuck")),
            reachable = mutableSetOf(hub),
        )
        // Keep stuck player on the target by intercepting transfer
        val noTransferProxy = object : ProxyPort by proxy {
            override fun transferPlayer(playerId: PlayerId, target: ServerId) {
                // simulate failed transfer — player stays
            }
        }
        val (orch, b) = setup(
            cfg = config(drainLead = 30, forceTimeout = 60),
            proxy = FakeProxy(
                playersOnTarget = mutableSetOf(pid("stuck")),
                reachable = mutableSetOf(hub),
            ).also { it.transfers.clear() },
        )
        // Manually keep player on target
        b.proxy.playersOnTarget.add(pid("stuck"))
        b.registry.get(survival).arm(cohort("stuck"), durationSeconds = 60)

        val t0 = Instant.parse("2026-01-01T00:00:00Z")
        orch.tick(t0)              // → COUNTDOWN
        orch.tick(t0.plusSeconds(30)) // → DRAINING + dispatch batch (player removed)

        // Re-add the stuck player to simulate no-transfer
        b.proxy.playersOnTarget.add(pid("stuck"))

        // 60 seconds after drain start → force timeout
        orch.tick(t0.plusSeconds(30 + 60))

        assertThat(b.messaging.restartSent).hasSize(1)
        assertThat(b.registry.get(survival).state).isEqualTo(RestartState.RESTART_SENT)
    }

    @Test
    fun `cancel during ARMED returns to IDLE and broadcasts cancel-message`() {
        val (orch, b) = setup()
        b.registry.get(survival).arm(cohort("a"), durationSeconds = 60)

        orch.cancel(survival)

        assertThat(b.registry.get(survival).state).isEqualTo(RestartState.IDLE)
        assertThat(b.audience.broadcasts).anyMatch { it.contains("cancel") }
    }

    @Test
    fun `cancel during COUNTDOWN returns to IDLE`() {
        val (orch, b) = setup()
        b.registry.get(survival).arm(cohort("a"), durationSeconds = 60)
        orch.tick(Instant.parse("2026-01-01T00:00:00Z"))

        orch.cancel(survival)

        assertThat(b.registry.get(survival).state).isEqualTo(RestartState.IDLE)
    }

    @Test
    fun `cancel during IDLE is a no-op`() {
        val (orch, b) = setup()
        orch.cancel(survival) // should not throw
        assertThat(b.registry.get(survival).state).isEqualTo(RestartState.IDLE)
    }

    @Test
    fun `start subscribes onCheckHacksResult so verdicts reach the gate (REQ-040)`() {
        val (_, b) = setup()
        b.gate.register(pid("alice"), hasBypass = false, nowSeconds = 0)
        assertThat(b.gate.isPending(pid("alice"))).isTrue()

        // The orchestrator now forwards inbound verdicts to RejoinService,
        // which guards on the player actually being on the source backend
        // (REQ-090 finding B). The fake proxy has no players on survival,
        // so the source-bound verdict is dropped before reaching the
        // gate — re-bind alice into the FakeProxy's roster first.
        b.proxy.playersOnTarget.add(pid("alice"))
        b.messaging.checkResultHandler!!.invoke(survival, pid("alice"), CheckOutcome.CLEAN)

        assertThat(b.gate.isPending(pid("alice"))).isFalse()
    }
}
