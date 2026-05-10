# CheckHacks-fork PR: add `CheckCompletedEvent`

**Repo:** `D:/CheckHacks-fork`
**Status:** required for queue-restart Phase 4 (T-041 already wires the
companion-side listener via reflection; this PR makes the binding type-safe
and removes the reflection guard).

## Why

`queue-restart` needs a single, reliable signal when CheckHacks finishes a
sign check on a player so it can release / drop them from the rejoin
queue per REQ-040..043. Today `CheckManager.finishCheck(UUID)` aggregates
`anyDetected` / `anyProtected` / `allClean` (see lines 346–348) but exposes
nothing publicly — the side effects are limited to internal alerts +
config-driven console commands.

We want an event API consumers can subscribe to without scraping logs.

## What

Add `me.branduzzo.checkHacks.api.CheckCompletedEvent` (a Bukkit `Event`)
and fire it at the tail of `finishCheck`.

### New file

`src/main/java/me/branduzzo/checkHacks/api/CheckCompletedEvent.java`

```java
package me.branduzzo.checkHacks.api;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;

public final class CheckCompletedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerId;
    private final boolean clean;
    private final boolean detected;
    private final boolean protected_;
    private final Set<String> detectedHacks;

    public CheckCompletedEvent(
            UUID playerId,
            boolean clean,
            boolean detected,
            boolean protected_,
            Set<String> detectedHacks) {
        this.playerId = playerId;
        this.clean = clean;
        this.detected = detected;
        this.protected_ = protected_;
        this.detectedHacks = Collections.unmodifiableSet(detectedHacks);
    }

    public UUID getPlayerId() { return playerId; }
    public boolean isClean() { return clean; }
    public boolean isDetected() { return detected; }
    public boolean isProtected() { return protected_; }
    public Set<String> getDetectedHacks() { return detectedHacks; }

    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
```

### Modified file

`src/main/java/me/branduzzo/checkHacks/managers/CheckManager.java`

After the existing `cfg.isCommandIfCleanEnabled()` branch (~line 414, end
of `finishCheck`), add:

```java
java.util.Set<String> detectedHackNames = allHacks.stream()
        .filter(h -> data.getResults().get(h.getId()) == HackResult.DETECTED)
        .map(HackDefinition::getDisplayName)
        .collect(java.util.stream.Collectors.toSet());

org.bukkit.Bukkit.getPluginManager().callEvent(
        new me.branduzzo.checkHacks.api.CheckCompletedEvent(
                uuid, allClean, anyDetected, anyProtected, detectedHackNames));
```

(Use proper imports at the top of the file — inlined here for clarity.)

## Compatibility

- Pure additive — no existing API changes, no behaviour changes when no
  one subscribes to the event.
- `queue-restart`'s companion (`CheckHacksBridge`) is reflection-safe:
  upgrading CheckHacks-fork is unblocking, not required.

## PR steps

1. `git checkout -b feat/check-completed-event` in `D:/CheckHacks-fork`.
2. Add the new file above.
3. Add the fire site in `CheckManager.finishCheck`.
4. Build (`mvn package`); install to local `~/.m2` if you want the
   queue-restart paper-companion build to pick it up at compile time
   (re-enable the `compileOnly("me.branduzzo:CheckHacks:1.2.0")` line in
   `paper-companion/build.gradle.kts`).
5. Open PR titled "feat(api): expose CheckCompletedEvent for proxy
   integrations".
