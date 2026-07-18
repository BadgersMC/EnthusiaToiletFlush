# Network restart operations

Velocity is the authoritative scheduler. Configure all recurring restart plans
in the Velocity plugin's `config.yml`; do not configure a second schedule in a
backend or another restart plugin.

## Restart types

- `server` uses the existing ToiletFlush backend drain, hub transfer, Paper
  companion shutdown, CheckHacks gate, and rank-weighted rejoin queue. Only
  players on that backend are affected.
- `proxy` restarts only Velocity. All players are fully disconnected. Backend
  servers remain online.
- `network` restarts the non-hub backends and hubs listed in
  `network-restart.full-network.members`, then sends the proxy restart action
  last. This is not a Pterodactyl-account-wide action.

## Pterodactyl setup

1. Install the Velocity jar on the proxy and the existing Paper companion jar
   on every backend. Do not install the Velocity jar on backends.
2. In Pterodactyl, create a Client API key with access only to the configured
   proxy and Minecraft backend servers.
3. Provide it to the Velocity process as `PTERODACTYL_API_KEY`. Leave
   `api-key: ${PTERODACTYL_API_KEY}` in the config; never paste the key into
   the file or a command.
4. Use the panel origin as `panel-url`, such as `https://panel.example.com`.
   Do not include a `/server/<id>` page URL.
5. Use each Pterodactyl server's identifier from its panel URL as
   `proxy-server-id` or a `network-restart.servers` value. The identifiers are
   allow-listed by the config, so commands cannot select arbitrary panel
   servers.
6. Set `enabled: true` while keeping `executor: DRY_RUN`, then run
   `/qrestart reload`.

The executor uses Pterodactyl's Client API with HTTPS, bounded timeouts, no
redirects, and no retry of an uncertain restart power action. A rejected or
failed request leaves Velocity running, removes the temporary maintenance
lock, and records the failed plan. There is intentionally no local-shutdown
fallback presented as a substitute for an externally managed proxy restart.

## Safe deployment and dry run

1. Fill in Velocity server names, Pterodactyl identifiers, and the explicit
   full-network member list.
2. Set `enabled: true` and `executor: DRY_RUN`; reload the plugin.
3. Test a backend: `/schedrestart SMP 2m`.
4. Test a proxy countdown: `/schedrestart proxy 30s`; confirm the warnings and
   disconnect screen without a real power action.
5. Test a network countdown: `/schedrestart network 30s`; verify only the
   configured members are listed and the maintenance login message appears.
6. Verify `/nextrestart` and `/restartschedule` show only public plans.
7. Set `executor: PTERODACTYL`, reload, and test one non-critical backend
   before proxy or full-network restarts.

`DRY_RUN` executes validation, countdowns, maintenance, transfers, and the
restart sequence, but never sends a Pterodactyl power request.

## Recurring schedules and migration

The default sample schedules SMP at midnight Monday through Saturday and a
full-network restart at midnight Sunday in `America/Indiana/Indianapolis`.
Each has a two-hour warning window, so warnings begin at 10:00 PM.

To migrate from a previous restart plugin, disable its automatic schedules and
remove its Velocity restart commands before enabling ToiletFlush schedules.
Keep the ToiletFlush Paper companion: normal backend drain/rejoin behaviour is
unchanged. Do not leave a duplicate Skript or another proxy scheduler active.

## Recovery and troubleshooting

Plans are persisted in `network-restarts.state` using atomic replacement.
Future plans resume after a proxy restart. Overdue plans are marked missed.
Plans interrupted during preflight, transfer, or dispatch are marked
`NEEDS_REVIEW` and are never replayed automatically, preventing restart loops.
The temporary maintenance lock is cleared on startup and expires automatically
after a failed dispatch.

If a proxy or network plan fails preflight, no power actions are sent. If a
full-network restart has already sent some accepted actions before a later
failure, the plan records the independent target outcomes and does not retry
accepted actions. Check the proxy log for the plan identifier and Pterodactyl
HTTP status; no API key is written to logs or state.
