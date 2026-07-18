# Permissions reference

All permissions live under the `queuerestart.*` namespace.

## Operator commands

| Permission | Grants |
|---|---|
| `queuerestart.command.schedrestart` | Backend, proxy, network, clock-time, and silent restart scheduling/cancellation |
| `queuerestart.command.admin` | `/qrestart reload`, `/qrestart trigger <scheduleName>` |

## Per-player bypass perms

| Permission | Effect |
|---|---|
| `queuerestart.bypass.drain` | Excluded from the drain cohort (REQ-014). Will not be transferred to the hub when a backend restarts; will not be re-queued. |
| `queuerestart.bypass.checkhacks` | Released into the rejoin queue without waiting for a `CheckHacksResult` (REQ-043). Use sparingly. |
| `queuerestart.bypass.maintenance` | May join during a temporary proxy/full-network restart maintenance lock. |

## Rank-ladder perms

The default ladder in `config.yml`:

```yaml
rank-ladder:
  group.owner: 1000
  group.admin: 900
  group.mvp+:  500
  group.mvp:   300
  group.vip+:  150
  group.vip:   100
  default:     0
```

The resolver picks the highest-weight matching permission a player holds
(REQ-033). Tied weights resolve to the entry declared first.

## LuckPerms example tracks

Drop these into `/lp creategroup …` and assign tracks. Every group inherits
the previous one and adds the queuerestart perm.

```text
# admins (full ops)
/lp creategroup admin
/lp group admin permission set queuerestart.command.schedrestart true
/lp group admin permission set queuerestart.command.admin true
/lp group admin permission set group.admin true
/lp group admin permission set queuerestart.bypass.drain true
/lp group admin permission set queuerestart.bypass.checkhacks true

# owner (a step above admin — same perms, higher rank weight)
/lp creategroup owner
/lp group owner parent add admin
/lp group owner permission set group.owner true
/lp group owner permission unset group.admin

# mvp+ → mvp → vip+ → vip → default ladder
/lp creategroup mvp+
/lp group mvp+ permission set group.mvp+ true
/lp group mvp+ parent add mvp

/lp creategroup mvp
/lp group mvp permission set group.mvp true
/lp group mvp parent add vip+

/lp creategroup vip+
/lp group vip+ permission set group.vip+ true
/lp group vip+ parent add vip

/lp creategroup vip
/lp group vip permission set group.vip true
/lp group vip parent add default

/lp creategroup default
/lp group default permission set group.default true   # cosmetic; weight comes from rank-ladder.default
```

Then build a track for promotions:

```text
/lp createtrack ranks
/lp track ranks append default
/lp track ranks append vip
/lp track ranks append vip+
/lp track ranks append mvp
/lp track ranks append mvp+

/lp createtrack staff
/lp track staff append admin
/lp track staff append owner
```

## Verifying

After applying, `/lp user <name> permission info` should show one of the
`group.*` perms set to `true`. Inside the proxy, the rank ladder will
resolve the player's queue weight on rejoin (REQ-033) and the bypass
perms gate drain / CheckHacks behaviour (REQ-014, REQ-043).
