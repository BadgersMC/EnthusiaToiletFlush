# Phase 2 security design — shared-secret + HMAC across channels

**Status:** draft / proposal.
**Owners:** BadgersMC engineering.
**Closes:** audit findings A (SLP poll-answer trust), B-deep (full cryptographic source auth), E (replay), G (schedule poisoning); REQ-090.

---

## 1. Threat model recap

The two communication channels between proxy and companion are
unauthenticated after phase 1:

1. **`qrestart:v1` plugin-message channel.** Velocity multiplexes
   `RegisteredServer.sendPluginMessage` over a player's backend
   connection. Backend's `ProxyMessageListener` receives bytes via any
   `Player`'s plugin-message hook. We trust the source server identity
   today by reading `event.source` (proxy-side) and trust that the
   companion only sees frames Velocity forwarded (backend-side, with
   phase 1's client-origin drop). Neither side has any cryptographic
   evidence the bytes came from the *intended* peer.

2. **`QR_POLL` SLP poll-back.** Companion opens raw TCP to
   `proxy-host:proxy-port`, sends an SLP handshake with hostname
   `QR_POLL:<server-id>`. Proxy answers with an embedded arm. There's
   no authentication on either direction — anyone bound to that port
   (whoever answers first wins) can return an arm; anyone who knows
   the server-id can drain a pending arm before the real companion
   polls.

Phase 1 closed every gap that allowed an *outside* actor to drive these
paths. Phase 2 must also close the gaps that assume the peer process
is the one you expect.

## 2. Goals

- **Message-level authentication** on every `qrestart:v1` frame in
  both directions.
- **Message-level authentication** on `QR_POLL` handshakes and SLP
  responses carrying `QR_ARM` / `QR_SCHEDULE`.
- **Replay protection** on every frame that triggers an action (arm,
  shutdown, verdict).
- **Zero new ports, zero new dependencies, zero external services.**
- **Operator UX that doesn't fight the panel host.** A Bloom egg owner
  copy-pasting one secret into one config field per backend is the
  hard ceiling.

Non-goals:

- End-to-end encryption. The data flowing both directions is not
  sensitive — restart times, drain ACKs, verdicts. Authenticity is
  the only property we need.
- Per-message confidentiality.
- Forward secrecy. The static-key model is acceptable for this trust
  domain (proxy operator == backend operator); rotation is a manual
  config edit.

## 3. Cryptographic primitive

**HMAC-SHA-256.** Standard library (`javax.crypto.Mac`), no
dependency adds, 256-bit security margin, plenty of headroom over the
~64-byte tags we'd otherwise consider.

Truncation: 16 bytes (128 bits) per tag. Saves space on the SLP
sample-name path where every byte counts; still well above the brute
force horizon for the per-message threat we care about (any
adversary capable of 2⁶⁴ trials is already past us).

## 4. Secret distribution

A single 32-byte secret shared between the proxy and every backend
companion. Encoded as 64 hex chars or 44 base64 chars in config.

### 4.1 Storage

- **Proxy:** `plugins/queue-restart/secret.txt` (chmod 600 on Linux).
  Plugin reads on enable; never logs the contents. Missing file →
  proxy auto-generates a random secret and writes it, then logs a
  one-time WARNING with the install path so the operator knows where
  to copy from.
- **Companion:** same file path under
  `plugins/queue-restart-companion/secret.txt`. Same auto-generation
  fallback **but** with a startup banner that recommends copying the
  proxy's secret if the operator forgot.

A separate file (not in `config.yml`) keeps secrets out of the git
repo most operators check the config into.

### 4.2 Mismatch behavior

- Proxy receives a frame with bad MAC: drop silently, increment a
  per-source metric, and log at INFO once per minute per source.
- Companion receives a poll response with bad MAC: log WARNING, no
  action.
- Two-direction asymmetry is intentional: a misconfigured backend
  spamming bad frames shouldn't paper over a real network issue with
  noisy logs.

### 4.3 Rotation

- Operator edits both files, restarts proxy + every backend.
- Optional: support two active secrets per install for one release
  cycle (`secret.txt` + `secret.previous.txt`) so rotation can be
  rolling rather than synchronous. Punted to phase 2.1 unless ops
  asks for it.

## 5. Plugin-message frame format

### 5.1 Current wire format

Each frame on `qrestart:v1` is an opaque `ByteArray` decoded by
`Codec.decode` (one of `RestartNow`, `DrainRequest`, `DrainAck`,
`CheckHacksResult`).

### 5.2 New envelope

```
+---------+----------------+----+----------+-----+
| version | seq (8 bytes)  | id |  body    | mac |
| (1 byte)| big-endian u64 |(1) |          | (16)|
+---------+----------------+----+----------+-----+
```

- `version` = `0x02` (current implicit = 0x01, framed; we'll keep
  decoding 0x01 frames for one release behind a config flag so
  rolling-upgrade installs don't break).
- `seq` = monotonic per-direction-per-peer counter. Wraps after 2⁶⁴
  (never, in practice). Recipient remembers `lastSeen[peer, direction]`
  and rejects `seq <= lastSeen`. Sender persists `seq` per-direction in
  `plugins/queue-restart{,-companion}/seq.txt` so a restart doesn't
  silently reset to zero.
- `id` = message-id byte (existing `Codec` `MSG_*` constants).
- `body` = existing message encoding.
- `mac` = `HMAC-SHA256(secret, version || seq || id || body || peerName)`
  truncated to 16 bytes. `peerName` binds the tag to the source server
  on the proxy side and to a single sentinel string ("proxy") on the
  backend side.

### 5.3 Verification

Recipient:
1. Strip + verify mac (constant-time compare).
2. Parse `seq`, reject if `<= lastSeen[peer, direction]`.
3. Update `lastSeen`.
4. Decode body via existing `Codec`.

If steps 1-3 fail: drop, metric, throttled log.

## 6. SLP poll-back format

### 6.1 Handshake (companion → proxy)

Today: `QR_POLL:<server-id>`.

New: `QR_POLL:<server-id>:<nonce>:<mac>` where

- `nonce` = 16 random hex chars (companion-generated, per-poll).
- `mac` = `HMAC-SHA256(secret, "QR_POLL" || server-id || nonce)`
  truncated to 16 hex chars.

`ProxyPollHandshake.parseHostname` (renamed
`parseHandshake`) returns a verified `PollHandshake(serverId, nonce)`
or null.

Proxy stores `seenNonces[serverId]` as a small LRU (say 256 entries
per server) to reject duplicates from the same backend within a short
window. Cross-server replay is already blocked by the server-id field
in the MAC input.

### 6.2 Arm response (proxy → companion)

Today: `QR_ARM:<delay>:<mode>:<argument>` as a sample-player name.

New: same sample-player slot, but format becomes
`QR_ARM:<delay>:<mode>:<argument>:<nonce>:<mac>` where

- `nonce` = the value the proxy received from the companion's
  handshake. Binds this response to that exact poll.
- `mac` = `HMAC-SHA256(secret, "QR_ARM" || delay || mode || argument
  || serverId || nonce)` truncated to 16 hex chars.

Companion's `ProxyArmPoller`:
1. Reads `QR_ARM:` from the response.
2. Verifies nonce matches the one it sent.
3. Verifies mac.
4. Verifies server-id (against its own config).
5. Only then schedules the shutdown.

Result: the SHUTDOWN-only whitelist from phase 1 can be relaxed back
to "any mode" once the MAC path is in place, because we now know the
arm came from the real proxy.

### 6.3 Schedule announce (companion → proxy)

Today: `QR_SCHEDULE:<times>:<zone>:<warn>` in the backend's SLP
sample, unauthenticated.

New: `QR_SCHEDULE:<times>:<zone>:<warn>:<mac>` where `mac =
HMAC-SHA256(secret, "QR_SCHEDULE" || times || zone || warn ||
serverId)`. Server-id is bound so an attacker who can rewrite a
backend's SLP response (MITM on plaintext TCP) can't cross-attribute
a stolen `QR_SCHEDULE` line to a different backend.

Proxy verifies on read in `pingForSchedule`. Bad MAC → log + ignore.

Replay protection isn't strictly necessary here (announce is
idempotent and proxy re-reads it every poll), but if we wanted it
we'd bake in a per-day counter. Skip for now.

## 7. Implementation order

1. **`:common/security/Hmac.kt`** — HMAC-SHA-256 helpers, hex
   encoding, constant-time compare, secret loading. Pure JDK.
   Reusable from both sides.
2. **Secret-file loader** on both modules. Auto-generate on missing.
3. **Plugin-message envelope codec** — wraps existing `Codec` rather
   than replacing it. New `Codec.encodeFramed(seq, msg, secret,
   peerName)` and `decodeFramed(bytes, secret, peerName)` returning
   `(seq, message)` or null on MAC failure.
4. **Sequence counter persistence** — `plugins/.../seq.txt` per side,
   read on enable, write-on-send (debounced).
5. **Wire envelope into `PluginMessageAdapter` + `ProxyMessageListener`.**
   One feature flag `auth.require-mac: true` (default true after phase
   2 ships; default false in the rollout release for one cycle).
6. **`QR_POLL` handshake nonce + MAC** — extend
   `ProxyPollHandshake`, add `seenNonces` LRU in
   `ProxyPingArmResponder`.
7. **`QR_ARM` MAC** — extend `ArmEncoding`; arm-poller verifies before
   dispatch. Drop the SHUTDOWN-only whitelist.
8. **`QR_SCHEDULE` MAC** — extend `ScheduleEncoding`; proxy verifies
   in `pingForSchedule`.
9. **Tests** — new vectors for each MAC, replay rejection, nonce
   reuse, mismatched secret, missing secret file behavior.
10. **Operational docs** — `docs/permissions.md` gets a section, plus
    a new `docs/security.md` covering secret install + rotation.

## 8. Backwards-compat rollout

The audit-respond release ships with `auth.require-mac: false` and
**both** sides accept legacy unmacked frames AND new envelope frames.
Logs WARNING per legacy frame so the operator knows which side hasn't
upgraded yet.

The release after flips the default to `true`. Operators who haven't
updated all backends get a friendly hard failure pointing at the
config knob.

This is the only sane rollout for a system where the operator can't
atomically restart proxy + every backend.

## 9. What this doesn't fix

- A backend operator who has root on a backend host can read the
  secret file. MAC then becomes worthless from that backend.
  Mitigation: separate secret per backend, but then secret distribution
  scales linearly with backends. Phase 3 if it ever matters.
- An on-path attacker between proxy and backend (panel internal LAN)
  who can also read the secret file from any party. mTLS would help;
  we don't have a CA story and this is intra-panel traffic. Out of
  scope.
- A panel operator with access to both filesystems. They're the
  trust root by definition.

## 10. Effort estimate

3–5 days for one engineer to ship phase 2 end-to-end with tests,
docs, and the rollout flag dance.

- Day 1: `:common/security` + secret loading + tests.
- Day 2: plugin-message envelope + adapter wiring + sequence file.
- Day 3: SLP-side MACs + handshake nonces + arm verification.
- Day 4: schedule-announce MAC + rollout flag + integration tests.
- Day 5: docs, operational runbook, e2e against test_net.
