# Tasks

Status legend: `[ ]` not started, `[~]` in progress, `[x]` done, `[!]` blocked.
Tags: `TDD` (test-first), `DOC` (docs/spec), `INFRA` (build/scaffolding).
Each task has `References:` pointing to REQ-IDs / impl sections and an
`Evidence:` block to be filled by the implementer.

## Phase 0 — scaffolding

- [x] T-001 [INFRA] Create Gradle root with `common`, `velocity`, `paper-companion` subprojects; toolchain Java 21; configure repos (papermc, velocityctd-snapshots).
  References: tech-stack.md
  Evidence: `settings.gradle.kts`, `build.gradle.kts`, `common/build.gradle.kts`, `velocity/build.gradle.kts`, `paper-companion/build.gradle.kts` present at repo root.

- [x] T-002 [INFRA] Add Konsist `LayerRulesTest.kt` under `velocity/src/test/kotlin/architecture/` with `__BASE_PACKAGE__` substituted to `com.badgersmc.queuerestart.velocity`.
  References: implementation.md §2
  Evidence: file created; Konsist dep added in velocity/build.gradle.kts (Phase 6).

- [x] T-003 [DOC] Author `README.md` covering install, build, deploy, and config reference.
  References: implementation.md §8, §9
  Evidence: `README.md` authored — modules, build, install, config, commands, CheckHacks integration, verification, architecture, doc index.

## Phase 1 — wire protocol & domain primitives

- [x] T-010 [TDD] `Codec` round-trip tests for every message type (DrainRequest, DrainAck, RestartNow, CheckHacksResult).
  References: REQ-020, implementation.md §6
  Evidence: `com.badgersmc.queuerestart.common.protocol.CodecTest` (12 tests, green); impl `common/src/main/java/com/badgersmc/queuerestart/common/protocol/{Codec,Messages}.kt`; commit pending.

- [x] T-011 [TDD] `RankLadder` resolver: highest-weight matching permission wins; missing → default; ties stable.
  References: REQ-033, implementation.md §4 domain/rank
  Evidence: `com.badgersmc.queuerestart.velocity.domain.rank.RankLadderTest` (7 tests, green); impl `velocity/.../domain/rank/RankLadder.kt`; Konsist `LayerRulesTest` green; commit pending.

- [x] T-012 [TDD] `CountdownSchedule` emits exactly the configured marks given a tick driver, including T-0.
  References: REQ-003, REQ-004
  Evidence: `com.badgersmc.queuerestart.velocity.domain.countdown.CountdownScheduleTest` (9 tests, green); impl `velocity/.../domain/countdown/CountdownSchedule.kt`; Konsist green; commit pending.

- [x] T-013 [TDD] `RestartCoordinator` state machine: legal transitions, illegal rejected, cancel valid only in ARMED/COUNTDOWN.
  References: REQ-001, REQ-002, REQ-005, REQ-061, implementation.md §5
  Evidence: `com.badgersmc.queuerestart.velocity.domain.coordinator.RestartCoordinatorTest` (16 tests, green); impl `velocity/.../domain/{coordinator/RestartCoordinator.kt, cohort/Cohort.kt, id/Ids.kt}`; Konsist green; commit pending.

## Phase 2 — application services

- [x] T-020 [TDD] `DrainPlanner` produces batches honoring `batch-size`, `drain-order`, and bypass perm exclusion (REQ-014).
  References: REQ-010, REQ-011, REQ-014
  Evidence: `com.badgersmc.queuerestart.velocity.application.drain.DrainPlannerTest` (8 tests, green); impl `velocity/.../application/drain/DrainPlanner.kt`; Konsist green; commit pending.

- [x] T-021 [TDD] `RejoinService` snapshots cohort on arm, drops offline members at enqueue (REQ-034), enqueues with rank weight (REQ-033).
  References: REQ-030, REQ-032, REQ-033, REQ-034
  Evidence: `com.badgersmc.queuerestart.velocity.application.drain.RejoinServiceTest` (4 tests, green); impl `velocity/.../application/drain/RejoinService.kt` + ports `ProxyPort.kt`, `QueuePort.kt`; Konsist green; commit pending.

- [x] T-022 [TDD] `CheckGate`: holds until clean, releases on timeout per `release-on-timeout`, drops on DETECTED, instant-release on bypass perm.
  References: REQ-040, REQ-041, REQ-042, REQ-043
  Evidence: `com.badgersmc.queuerestart.velocity.application.gate.CheckGateTest` (11 tests, green); impl `velocity/.../application/gate/CheckGate.kt`; Konsist green; commit pending.

- [x] T-023 [TDD] `ScheduleService` cron parsing + trigger emission; `/qrestart reload` rebuilds without aborting in-flight countdowns (REQ-050).
  References: REQ-002, REQ-050, REQ-051
  Evidence: `com.badgersmc.queuerestart.velocity.application.schedule.ScheduleServiceTest` (8 tests, green); impl `velocity/.../application/schedule/ScheduleService.kt` + port `SchedulerPort.kt`. Cron parsing itself stays in infrastructure (T-033). Konsist green; commit pending.

- [x] T-024 [TDD] Hub fallback: when primary unreachable, iterates `fallback-hubs` in order.
  References: REQ-013
  Evidence: `com.badgersmc.queuerestart.velocity.application.drain.HubResolverTest` (6 tests, green); impl `velocity/.../application/drain/HubResolver.kt`; Konsist green; commit pending.

## Phase 3 — infrastructure adapters

- [x] T-030 [TDD] `PluginMessageAdapter` integration test using a fake Velocity messaging stub: encode/decode parity vs `Codec`.
  References: implementation.md §6
  Evidence: `com.badgersmc.queuerestart.velocity.infrastructure.messaging.PluginMessageAdapterTest` (7 tests, green); impl `PluginMessageAdapter.kt` + `PluginMessageTransport` SAM + `MessagingPort.kt`. Velocity binding deferred to a thin `VelocityChannelTransport` shim (TBD when wiring entrypoint). Konsist green; commit pending.

- [x] T-031 [TDD] `QueueAdapter` against a mocked `QueueManager`: enqueue ordering matches injected weights.
  References: REQ-032, REQ-033
  Evidence: `com.badgersmc.queuerestart.velocity.infrastructure.velocity.QueueAdapterTest` (3 tests, green); impl `QueueAdapter.kt` + `QueueManagerBackend` interface. CTD `QueueManager` binding deferred to a thin `VelocityQueueManagerBackend` shim (TBD when wiring entrypoint). Konsist green; commit pending.

- [x] T-032 [TDD] `ConfigurateConfigAdapter` loads valid sample, rejects sound volume >1.0, warns >0.8.
  References: REQ-006
  Evidence: `com.badgersmc.queuerestart.velocity.infrastructure.config.ConfigurateConfigAdapterTest` (6 tests, green); impl `ConfigurateConfigAdapter.kt`, port `ConfigPort.kt`, default `velocity/src/main/resources/config.yml`. Konsist green; commit pending.

- [x] T-033 [TDD] `CronUtilsScheduler` fires triggers at expected cron instants under a fake clock.
  References: REQ-002
  Evidence: `com.badgersmc.queuerestart.velocity.infrastructure.schedule.CronUtilsSchedulerTest` (5 tests, green); impl `CronUtilsScheduler.kt` (UNIX cron, 60s first-tick grace). Konsist green; commit pending.

## Phase 4 — Paper companion

- [x] T-040 [TDD] `RestartExecutor` dispatches the correct action per `mode` (SHUTDOWN/COMMAND/EXIT_CODE) using a mocked Bukkit/Server.
  References: REQ-021
  Evidence: `com.badgersmc.queuerestart.paper.RestartExecutorTest` (4 tests, green); impl `paper-companion/src/main/kotlin/.../paper/RestartExecutor.kt` + `ServerControl` interface. Bukkit binding (`BukkitServerControl`) deferred to entrypoint wiring. CheckHacks compile-only dep removed from `paper-companion/build.gradle.kts` (bridge uses reflection); commit pending.

- [x] T-041 [TDD] `CheckHacksBridge` translates `CheckCompletedEvent` → `CheckHacksResult` plugin message; soft-depend absence does not crash plugin enable.
  References: REQ-040, implementation.md §7
  Evidence: `com.badgersmc.queuerestart.paper.CheckHacksBridgeTest` (6 tests, green); impl `paper-companion/.../paper/CheckHacksBridge.kt`. Outcome precedence: DETECTED > PROTECTED > CLEAN, fallback TIMEOUT. Reflection-safe — when `me.branduzzo.checkHacks.api.CheckCompletedEvent` class is missing, `isCheckHacksAvailable()` returns false and `installListenerIfAvailable` is a safe no-op. Commit pending.

- [x] T-042 [DOC] Document required additive change in CheckHacks (`CheckCompletedEvent`) and open a separate PR there.
  References: implementation.md §7
  Evidence: `docs/checkhacks-fork-pr.md` authored — full Java source for `CheckCompletedEvent` + fire-site diff for `CheckManager.finishCheck` (~line 414). PR against `D:/CheckHacks-fork` deferred to user.

## Phase 5 — commands & end-to-end

- [x] T-050 [TDD] `SchedRestartCommand`: arm, cancel, status; rejects hub target (REQ-060) and double-arm (REQ-061); refuses without companion (REQ-062).
  References: REQ-001, REQ-005, REQ-052, REQ-060, REQ-061, REQ-062
  Evidence: `com.badgersmc.queuerestart.velocity.application.schedule.SchedRestartCommandHandlerTest` (8 tests, green); impl `SchedRestartCommandHandler.kt` + `CoordinatorRegistry.kt`. Brigadier shim deferred to entrypoint wiring. Konsist green; commit pending.

- [x] T-051 [TDD] `QRestartAdminCommand`: reload + trigger.
  References: REQ-050, REQ-051
  Evidence: `com.badgersmc.queuerestart.velocity.application.schedule.QRestartAdminCommandHandlerTest` (3 tests, green); impl `QRestartAdminCommandHandler.kt`. Brigadier shim deferred to entrypoint wiring. Konsist green; commit pending.

- [x] T-052 [DOC] End-to-end manual verification runbook on dev proxy (2 backends + companion).
  References: implementation.md §1, §5
  Evidence: `docs/e2e-runbook.md` authored — covers boot smoke test, full state cycle, CheckHacks gate, negative paths (REQ-005/013/014/043/050/051/060/061/062), rollback.

## Phase 6 — polish

- [x] T-060 [INFRA] Konsist run wired into `check`; CI fails on layer violations.
  References: implementation.md §2
  Evidence: dedicated `:velocity:konsistCheck` Test task added in `velocity/build.gradle.kts`, attached to `:check` via `dependsOn`. Filter targets `architecture.LayerRulesTest`. CI now gets a clearly-named layer-violation gate. Verified green; commit pending.

- [x] T-061 [DOC] Permissions reference table + LuckPerms example tracks.
  References: implementation.md §9
  Evidence: `docs/permissions.md` authored — operator commands, bypass perms (REQ-014, REQ-043), rank-ladder defaults (REQ-033), full LuckPerms group/track scaffolding.

## Phase 7 — application glue (missing ports + orchestrator)

These tasks close gaps the original spec papered over. Without them the
domain state machine has no driver and the chat/sound side of the
countdown is unreachable. Each is still testable with fakes — no
framework dep needed.

- [x] T-070 [TDD] Define `AudiencePort` (broadcast text + play sound to the
  audience for a target server) and `ClockPort` (current `Instant`).
  References: implementation.md §4 application/ports
  Evidence: `velocity/.../application/ports/{AudiencePort.kt, ClockPort.kt}`. Validated in-situ by T-071/T-072 tests using fake impls. Konsist green.

- [x] T-071 [TDD] `CountdownBroadcaster` application service: given a
  ticking [`ClockPort`] and the coordinator's `durationSeconds`, drives
  `CountdownSchedule.fireAt` and dispatches the configured countdown
  message + sound via [`AudiencePort`] at every mark including T-0.
  References: REQ-003, REQ-004
  Evidence: `com.badgersmc.queuerestart.velocity.application.schedule.CountdownBroadcasterTest` (8 tests, green); impl `CountdownBroadcaster.kt`. Idempotent on repeated tick at same second; per-target state; placeholders `<server>`, `<time>` (formats `Nm` if seconds%60==0 else `Ns`), `<hub>`. Konsist green.

- [x] T-072 [TDD] `RestartOrchestrator` application service: ties together
  `RestartCoordinator`, `CountdownBroadcaster`, `DrainPlanner`,
  `MessagingPort`, `RejoinService`, `CheckGate`, `HubResolver`. Drives
  the full IDLE→ARMED→COUNTDOWN→DRAINING→RESTART_SENT→SERVER_DOWN→
  REJOIN_RELEASE→IDLE state cycle on a tick driver. Force-drain timeout
  (REQ-012). Hooks `MessagingPort.onCheckHacksResult` (REQ-040).
  References: REQ-001, REQ-010, REQ-012, REQ-020, REQ-031, REQ-032, REQ-040, implementation.md §5
  Evidence: `com.badgersmc.queuerestart.velocity.application.schedule.RestartOrchestratorTest` (10 tests, green); impl `RestartOrchestrator.kt`. Per-target ephemeral state (countdown start, drain start, pending batches, next-batch-at). Cancel broadcasts cancel-message + cleans broadcaster + cleans state. ProxyPort extended with `transferPlayer`. Konsist green; commit pending.

- [x] T-073 [TDD] `PingPoller` application service: REQ-031 — polls
  `ProxyPort.isReachable` at the configured interval after RESTART_SENT,
  flips coordinator to REJOIN_RELEASE on first success.
  References: REQ-031
  Evidence: `com.badgersmc.queuerestart.velocity.application.drain.PingPollerTest` (5 tests, green); impl `PingPoller.kt`. Honours pingPollSeconds; ignores other states; calls `onReady(target)` (orchestrator.finishRejoin) on first successful ping. Konsist green; commit pending.

## Phase 8 — Velocity infrastructure bindings + entrypoint

Each binding is a thin shim. The unit suite tests adapter cores against
fakes; these classes are validated by the e2e runbook (§5 of T-052).

- [x] T-080 [INFRA] `SystemClockAdapter` (`ClockPort` impl wrapping
  `Instant.now()`) + `AdventureAudienceAdapter` + `MiniMessageRenderer`
  for `<server>` / `<time>` / `<hub>` placeholders. Bind to
  `ProxyServer.getAllPlayers().filter(server)` for broadcasts.
  References: REQ-003, REQ-004, implementation.md §4
  Evidence: `velocity/.../infrastructure/{clock/SystemClockAdapter.kt, audience/{MiniMessageRenderer.kt, AdventureAudienceAdapter.kt}}`. `MiniMessageRenderer` uses `Placeholder.parsed` so `<server>`/`<time>`/`<hub>` substitution is injection-safe. Adventure `Sound.Source.MASTER`. Compiles green; behaviour validated under T-100 e2e.

- [x] T-081 [INFRA] `VelocityChannelTransport` — implements
  `PluginMessageTransport` over `RegisteredServer.sendPluginMessage` on
  channel `qrestart:v1`. Register
  `MinecraftChannelIdentifier.from("qrestart:v1")` on plugin enable.
  Subscribe to `PluginMessageEvent` and forward to
  `PluginMessageAdapter.handleInbound`.
  References: REQ-020, implementation.md §6
  Evidence: `velocity/.../infrastructure/messaging/VelocityChannelTransport.kt`. Outbound: `RegisteredServer.sendPluginMessage`. Inbound: `@Subscribe` on `PluginMessageEvent` filters to `qrestart:v1`, forwards bytes via `adapter.handleInbound`, sets event result to `handled()` to suppress vanilla forwarding. Compiles green; behaviour validated in T-100.

- [x] T-082 [INFRA] `ProxyAdapter` — implements `ProxyPort` over the
  Velocity API (`ProxyServer.getAllPlayers`, `Player.hasPermission`,
  `RegisteredServer.ping`, `RegisteredServer.getPlayersConnected`).
  References: REQ-014, REQ-031, REQ-033, REQ-034, REQ-043
  Evidence: `com.badgersmc.queuerestart.velocity.infrastructure.velocity.ProxyAdapterTest` (6 tests, green); impl `ProxyAdapter.kt` + `VelocityProxyBackend` SAM. Velocity-API binding (`VelocityProxyServerBackend`) deferred to entrypoint wiring (T-085). Konsist green; commit pending.

- [x] T-083 [INFRA] `VelocityQueueManagerBackend` — implements
  `QueueManagerBackend` over CTD's `QueueManager` (resolve via
  `ProxyServer.getPluginManager().getPlugin("velocityctd")`-style lookup
  if needed). Falls back to a logging no-op when CTD queue API is
  unavailable so the plugin still enables.
  References: REQ-032, REQ-033
  Evidence: `velocity/.../infrastructure/velocity/VelocityQueueManagerBackend.kt`. Direct CTD bind via `ProxyServer.isQueueEnabled` + `getQueueManager()` (CTD's velocity-api 3.5 superset is the sole compileOnly dep — upstream 3.3 line dropped to stop it shadowing CTD's ProxyServer additions). Three-layer fallback: `NoSuchMethodError` (vanilla Velocity at runtime) → no-op + warn; `isQueueEnabled=false` → no-op + warn; per-call null queue/offline-player → debug + skip. Builds `QueueEntryData(uuid, username, weight, false, false)`. Konsist green; behaviour validated under T-100. Commit pending.

- [x] T-084 [INFRA] Brigadier shims `SchedRestartCommand` +
  `QRestartAdminCommand`. Parse arg trees, perm-gate via
  `CommandSource.hasPermission`, forward to
  `SchedRestartCommandHandler` / `QRestartAdminCommandHandler`. Render
  results via the audience.
  References: implementation.md §9
  Evidence: `velocity/.../infrastructure/command/{SchedRestartCommand,QRestartAdminCommand}.kt`. Both build `BrigadierCommand` trees gated on `queuerestart.command.{schedrestart,admin}`. SchedRestart resolves default target from the player's current backend (console must specify); routes `cancel`/`status`/`<minutes> [server]` arms to `SchedRestartCommandHandler`. QRestart admin routes `reload` and `trigger <name>` to `QRestartAdminCommandHandler`. Results rendered via `sendRichMessage` (MiniMessage). Konsist green; behaviour validated under T-100.

- [x] T-085 [INFRA] `QueueRestartPlugin` (`@Plugin`) entrypoint. On
  `ProxyInitializeEvent`: load config, build all adapters, wire ports,
  register channel, register commands, schedule the proxy-side tick task
  (1 Hz feeding `CountdownBroadcaster`, `CheckGate.tick`,
  `CronUtilsScheduler.tick`, `PingPoller`). Logs version + integration
  status.
  References: implementation.md §1, §4
  Evidence: `velocity/.../QueueRestartPlugin.kt`. `@Plugin(id="queue-restart")` entrypoint, Guice-injected `ProxyServer`/`Logger`/`@DataDirectory Path`. On `ProxyInitializeEvent`: materialises default `config.yml` from jar resources; constructs SystemClock, AdventureAudience, Configurate config, Velocity-bound Proxy/Queue backends, channel transport (channel↔adapter cycle broken via `PluginMessageTransport` lambda forwarder); wires RankLadder, CoordinatorRegistry, CountdownBroadcaster, DrainPlanner, HubResolver, RejoinService, CheckGate, RestartOrchestrator, PingPoller, ScheduleService; registers Brigadier shims; starts 1 Hz proxy scheduler tick driving `cronScheduler.tick`/`orchestrator.tick`/`pingPoller.tick`. Probe set for the rank ladder seeded via `VelocityProxyServerBackend.withRankLadder` so `permissionsOf` surfaces ladder nodes (REQ-033). `:velocity:shadowJar` produces deployable `velocity-0.1.0-SNAPSHOT.jar` (2.9 MB). Konsist + 17 velocity test classes green. Behaviour validated under T-100.

## Phase 9 — Paper companion bindings + entrypoint

- [x] T-090 [INFRA] `BukkitServerControl` — `ServerControl` impl over
  `Bukkit.shutdown()`, `Bukkit.dispatchCommand(consoleSender, …)`,
  `System.exit(code)`.
  References: REQ-021
  Evidence: `paper-companion/.../BukkitServerControl.kt`. Direct passthrough to `Bukkit.shutdown()` / `Bukkit.dispatchCommand(consoleSender, cmd)` / `System.exit(code)`. Behaviour validated under T-100.

- [x] T-091 [INFRA] `ProxyMessageListener` — Bukkit
  `PluginMessageListener` registered on channel `qrestart:v1`. Decodes
  via `Codec`, dispatches `RestartNow` to `RestartExecutor`, forwards
  outbound `DrainAck` and `CheckHacksResult` frames upstream.
  References: REQ-021, implementation.md §6
  Evidence: `paper-companion/.../ProxyMessageListener.kt`. Implements `org.bukkit.plugin.messaging.PluginMessageListener`; decodes inbound bytes via `Codec`; routes `RestartNowMessage` to the executor (other inbound types ignored). Outbound `sendDrainAck(int)` + `sendCheckHacksResult(msg)` use `Player.sendPluginMessage` on any online player (Bukkit requires a Player to pin the channel) — drops with a warning on an empty server. Malformed frames + executor failures logged at WARNING/SEVERE. Behaviour validated under T-100.

- [x] T-092 [INFRA] CheckHacks listener registration: on `onEnable`, call
  `CheckHacksBridge.installListenerIfAvailable` with a Bukkit
  `EventExecutor` lambda that pulls `getPlayerId` / `isClean` /
  `isDetected` / `isProtected` reflectively from the event instance,
  passes to `bridge.translate`, and ships the resulting
  `CheckHacksResultMessage` to the proxy via the plugin-message channel.
  References: REQ-040, implementation.md §7
  Evidence: implemented inside `CompanionPlugin.installCheckHacksListener()` (paper-companion/.../CompanionPlugin.kt). Bridge is invoked at `onEnable`; when CheckHacks is present, reflective getters for `getPlayerId`/`isClean`/`isDetected`/`isProtected` are resolved (with `isProtected_` fallback for the Kotlin-clash spelling), wrapped in an `EventExecutor` lambda registered at `EventPriority.MONITOR` against an `EmptyListener` token, and the translated `CheckHacksResultMessage` is shipped via `ProxyMessageListener.sendCheckHacksResult`. Soft-depend declared in `plugin.yml`. Reflection bridge migrates to typed binding under T-101 once the CheckHacks-fork PR ships. Behaviour validated under T-100.

- [x] T-093 [INFRA] `CompanionPlugin` (`@JavaPlugin` main). On
  `onEnable`: register channel, attach `ProxyMessageListener`, build
  `RestartExecutor` over `BukkitServerControl`, run T-092.
  References: implementation.md §1
  Evidence: `paper-companion/.../CompanionPlugin.kt` + `paper-companion/src/main/resources/plugin.yml`. `JavaPlugin` main wires `RestartExecutor(BukkitServerControl())` → `ProxyMessageListener`, registers `qrestart:v1` incoming + outgoing channels via `server.messenger`, runs T-092 listener install, logs `checkhacks=<bool>` integration status. `onDisable` unregisters both channels. `:paper-companion:shadowJar` produces `paper-companion-0.1.0-SNAPSHOT.jar` (1.8 MB). Behaviour validated under T-100.

## Phase 9.5 — outbound arm via SLP poll-back (REQ-022)

Closes the "console-arm with empty target" gap. The plugin-message
channel needs a player on the target backend; SLP doesn't. Companion
polls proxy on a magic hostname, proxy publishes pending arms in the
poll response.

- [x] T-110 [TDD] `ArmEncoding` (`:common`). Encode/decode
  `QR_ARM:<delaySeconds>:<mode>:<argument>` with marker UUID
  `00000000-0000-0000-0000-0000005152A0`. Mirrors `ScheduleEncoding`
  (different prefix + UUID).
  References: REQ-022
  Evidence: `common/.../schedule/ArmEncoding.kt` + `ArmEncodingTest.kt`. Round-trips SHUTDOWN/COMMAND/EXIT_CODE; rejects malformed payloads, missing prefix, non-numeric delay, unknown mode; preserves trailing `:` in argument tail; rejects negative delay. 6 tests green. Plus `ProxyPollHandshake` (format/parse `QR_POLL:<server-id>` hostname; tolerates Forge ` FML ` suffix). 5 tests green.

- [x] T-111 [TDD] `PendingArmStore` (`:velocity`). Per-`ServerId` slot
  holding the most recent arm with TTL (default 60s). `put`, `consume`
  (read+clear), `peek`. Coordinator writes on `tickArmed`; consumer
  reads on SLP poll-back response.
  References: REQ-022
  Evidence: `velocity/.../application/arm/PendingArmStore.kt` + `PendingArmStoreTest.kt`. put/peek/consume/clear, TTL expiry, overwrite-on-rePut, ServerId partitioning. 6 tests green.

- [x] T-112 [INFRA] `ProxyPingArmResponder`. `@Subscribe` on
  `ProxyPingEvent`, parses `event.connection.rawVirtualHost` for
  `QR_POLL:<serverId>` via `ProxyPollHandshake`, calls
  `PendingArmStore.consume`, builds replacement `ServerPing` via
  `ping.asBuilder().clearSamplePlayers().samplePlayers([encoded])`.
  Non-QR_POLL pings pass through untouched.
  References: REQ-022, implementation.md §6
  Evidence: `velocity/.../infrastructure/velocity/ProxyPingArmResponder.kt`. Logs `SLP poll-back delivering arm to lobby2 (delay=60s, mode=SHUTDOWN)` on each successful delivery; silent on empty polls. Behaviour validated under T-100 e2e.

- [x] T-113 [INFRA] Companion `ProxyArmPoller`. Raw SLP client opens
  TCP to `proxy-host:proxy-port`, sends handshake (packet 0x00, varint
  protocol -1, `serverAddress = QR_POLL:<server-id>`, port, next
  state 1) + status request (0x00). Reads response packet, regex-finds
  `QR_ARM:[^"]*` in JSON body, decodes via `ArmEncoding`, hops to
  Bukkit main thread, dispatches to `RestartExecutor`. Idempotent on
  identical encoded payloads. Async-scheduled task, single in-flight,
  failure backoff (logs at FINE; throttled 1×, then every 12 polls).
  References: REQ-022, implementation.md §6
  Evidence: `paper-companion/.../ProxyArmPoller.kt`. Logs `arm poller started (proxy=127.0.0.1:25565, server-id=lobby2, every 5s)` on enable; `SLP poll-back delivered arm (delay=60s, mode=SHUTDOWN); scheduling shutdown` on round-trip. Behaviour validated under T-100 e2e — proxy + companion exchanged arm in <1s with no player on target.

- [x] T-114 [INFRA] Wire both sides + config. Velocity entrypoint
  builds `PendingArmStore`, passes to `RestartOrchestrator`, registers
  `ProxyPingArmResponder` via `proxy.eventManager.register`. Orch
  publishes arm in `tickArmed` (belt + braces with existing
  `messaging.sendRestartNow`) and clears on `cancel`. Companion
  `config.yml` gains `proxy-host` / `proxy-port` / `server-id` /
  `arm-poll-seconds`. `CompanionPlugin.onEnable` constructs + starts
  the poller; `onDisable` stops it. Test suite green; shadow jars
  built. Behaviour confirmed end-to-end on test_net.
  References: REQ-022
  Evidence: `velocity/.../QueueRestartPlugin.kt` (PendingArmStore wired into orchestrator + responder registered); `paper-companion/.../CompanionPlugin.kt` (`startArmPoller` reads config keys, skips if `server-id` empty, starts on enable, stops on disable). End-to-end on test_net 2026-05-09 21:12: console-armed `/schedrestart 1 lobby2` with 0 players on target → SLP-poll round-trip in <1s → companion scheduled shutdown.

## Phase 10 — release verification

- [ ] T-100 [INFRA] Build both shadow jars, deploy to a local dev proxy
  per `docs/e2e-runbook.md`, walk the whole runbook, capture log
  excerpts as Evidence.
  References: docs/e2e-runbook.md
  Evidence:

- [ ] T-101 [DOC] Open the CheckHacks-fork PR per
  `docs/checkhacks-fork-pr.md`. Re-enable the
  `compileOnly("me.branduzzo:CheckHacks:1.2.0")` line in
  `paper-companion/build.gradle.kts` once installed locally; replace the
  reflection bridge with a typed Bukkit listener and migrate T-092 to
  the typed path.
  References: implementation.md §7, docs/checkhacks-fork-pr.md
  Evidence:
