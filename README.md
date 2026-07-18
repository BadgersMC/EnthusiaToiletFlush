# EnthusiaToiletFlush

Velocity-CTD plugin + Paper companion for graceful, scheduled backend
restarts on BadgersMC. Drains players to the hub, restarts the backend,
auto-rejoins them in rank-weighted order behind a CheckHacks gate.

(Internal codename: `queue-restart` — Gradle subproject names, package
paths, plugin-message channel `qrestart:v1`, and SLP markers retain
that identifier for compatibility with running deployments.)

Built with the [SPEAR methodology](docs/) — every feature traces a
`REQ-NNN` in `docs/requirements.md`.

## Modules

| Module | Target | Role |
|---|---|---|
| `common/` | JVM 21 | Wire DTOs + `Codec` for channel `qrestart:v1`. No framework deps. |
| `velocity/` | Velocity-CTD 3.5.x | Proxy plugin: countdown, drain, restart trigger, rejoin queue. Hexagonal: domain ← application ← infrastructure. |
| `paper-companion/` | Paper 1.21.x | Backend agent: executes restart, bridges CheckHacks events. |

## Build

Java 21, Gradle Kotlin DSL. Bundled wrapper.

```bash
./gradlew check          # unit tests + Konsist layer rules
./gradlew :velocity:konsistCheck   # explicit layer-rules gate (CI)
./gradlew :velocity:shadowJar      # plugin jar
./gradlew :paper-companion:shadowJar
```

Outputs:

```
velocity/build/libs/queue-restart-velocity-0.1.0-SNAPSHOT.jar
paper-companion/build/libs/queue-restart-paper-companion-0.1.0-SNAPSHOT.jar
```

## Install

1. Drop the velocity jar into the proxy's `plugins/` directory.
2. Drop the companion jar into **every** backend's `plugins/` directory.
3. Start the proxy first, then the backends.

The Velocity jar does **not** go on backend servers. The existing Paper
companion jar does **not** go on Velocity. Keep both components installed:
Velocity owns schedules, maintenance mode, external restart actions, and the
rejoin queue; the companion performs a normal backend's local shutdown after
Velocity drains it.
4. Default config materialises at `plugins/queue-restart/config.yml` —
   edit and run `/qrestart reload`.

## Configuration

Canonical sample lives at
[`velocity/src/main/resources/config.yml`](velocity/src/main/resources/config.yml).
See `docs/implementation.md` §8 for the field reference. Sound volumes
above 0.8 emit a startup warning; above 1.0 are rejected (REQ-006).

## Commands

| Command | Permission | Purpose |
|---|---|---|
| `/schedrestart <minutes> [server]` | `queuerestart.command.schedrestart` | Arm an ad-hoc restart |
| `/schedrestart <server> <duration> [--silent] [reason...]` | `queuerestart.command.schedrestart` | Schedule a configured backend by name |
| `/schedrestart proxy <duration> [--silent] [reason...]` | `queuerestart.command.schedrestart` | Restart only Velocity and disconnect everyone |
| `/schedrestart network <duration> [--silent] [reason...]` | `queuerestart.command.schedrestart` | Restart configured network members, then Velocity |
| `/schedrestart at server <server> <HH:mm> [--silent] [reason...]` | `queuerestart.command.schedrestart` | Schedule a backend at a clock time |
| `/schedrestart at proxy\|network <HH:mm> [--silent] [reason...]` | `queuerestart.command.schedrestart` | Schedule a proxy or full-network restart at a clock time |
| `/schedrestart cancel [server]` | `queuerestart.command.schedrestart` | Cancel armed/counting-down |
| `/schedrestart cancel <plan-id\|proxy\|network>` | `queuerestart.command.schedrestart` | Cancel a network restart plan |
| `/schedrestart status` | `queuerestart.command.schedrestart` | Inspect coordinator states |
| `/nextrestart` | none | Show the next public restart concisely |
| `/restartschedule` | none | Show public recurring restarts concisely |
| `/qrestart reload` | `queuerestart.command.admin` | Reload Velocity configuration |
| `/qrestart trigger <name>` | `queuerestart.command.admin` | Run named schedule on demand |

Per-player bypass perms and rank-ladder details: see
[`docs/permissions.md`](docs/permissions.md).

## CheckHacks integration

## Network-wide restart extension

`network-restart` in the proxy configuration is the authoritative source for
daily/weekly schedules and Pterodactyl target mappings. Keep the executor at
`DRY_RUN` until message, transfer, and target validation has been tested. Set
`PTERODACTYL_API_KEY` in the Velocity process environment before enabling
`PTERODACTYL`; never add that key to `config.yml`.

`/schedrestart proxy <duration>` restarts only Velocity and disconnects all
players. `/schedrestart network <duration>` restarts only explicitly configured
network members and dispatches the proxy action last. Both forms accept
`--silent` to suppress player announcements. `/nextrestart` and
`/restartschedule` are public, concise status commands and do not reveal
silent plans.

See [`docs/network-restarts.md`](docs/network-restarts.md) for Pterodactyl
setup, dry-run testing, migration, recovery, and troubleshooting.

The companion translates `me.branduzzo.checkHacks.api.CheckCompletedEvent`
into `CheckHacksResult` plugin messages. CheckHacks remains a
soft-depend — the plugin enables fine without it. To wire it up, install
the additive PR described in [`docs/checkhacks-fork-pr.md`](docs/checkhacks-fork-pr.md).

## Verification

Unit suite: `./gradlew test` — covers wire codec, rank ladder, countdown
schedule, restart state machine, drain planner, rejoin service, check gate,
hub fallback, plugin-message adapter, queue adapter, Configurate adapter,
network restart plans, Pterodactyl executor, both command handlers, and the
CheckHacks bridge.

End-to-end runbook: [`docs/e2e-runbook.md`](docs/e2e-runbook.md).

## Architecture

```
domain  ← application ← infrastructure
            ↑              ↑
         (ports)       (adapters)
```

Konsist (`velocity/src/test/kotlin/architecture/LayerRulesTest.kt`)
enforces:

- `domain.*` imports nothing from application or infrastructure.
- `application.*` depends only on domain.
- `domain.*` is free of `com.velocitypowered`, `com.velocityctd`,
  `org.bukkit`, `io.papermc`, `net.kyori`, `org.spongepowered.configurate`,
  `com.cronutils`.

`./gradlew :velocity:konsistCheck` is the layer-violation gate for CI.

## Docs

- [`docs/tech-stack.md`](docs/tech-stack.md) — languages, libraries, versions.
- [`docs/requirements.md`](docs/requirements.md) — EARS spec (REQ-001..REQ-062).
- [`docs/implementation.md`](docs/implementation.md) — architecture blueprint.
- [`docs/tasks.md`](docs/tasks.md) — task ledger with evidence per item.
- [`docs/permissions.md`](docs/permissions.md) — perms + LuckPerms tracks.
- [`docs/e2e-runbook.md`](docs/e2e-runbook.md) — manual verification.
- [`docs/checkhacks-fork-pr.md`](docs/checkhacks-fork-pr.md) — additive
  CheckHacks PR (separate repo).

## Licence

Internal BadgersMC project. Contact Badger before redistributing.
