package com.badgersmc.queuerestart.velocity.application.arm

import com.badgersmc.queuerestart.common.protocol.RestartMode
import com.badgersmc.queuerestart.common.schedule.PendingArm
import com.badgersmc.queuerestart.velocity.domain.id.ServerId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

/**
 * REQ-022. Per-server ephemeral slot for the most recent pending arm.
 *
 * `put` overwrites; `peek` reads without clearing; `consume` reads + clears.
 * Entries expire after a TTL so a forgotten arm doesn't shut a backend down
 * an hour later when it finally polls. The companion-side poll cadence is
 * seconds, so a 60s default TTL is generous.
 */
class PendingArmStoreTest {

    private val lobby2 = ServerId("lobby2")
    private val survival = ServerId("survival")
    private val arm = PendingArm(60, RestartMode.SHUTDOWN, "")
    private val t0 = Instant.parse("2026-05-09T12:00:00Z")

    @Test
    fun `put then peek returns the arm without clearing`() {
        val store = PendingArmStore(ttl = Duration.ofSeconds(60))
        store.put(lobby2, arm, now = t0)

        assertThat(store.peek(lobby2, now = t0)).isEqualTo(arm)
        assertThat(store.peek(lobby2, now = t0)).isEqualTo(arm)
    }

    @Test
    fun `consume returns the arm and clears the slot`() {
        val store = PendingArmStore(ttl = Duration.ofSeconds(60))
        store.put(lobby2, arm, now = t0)

        assertThat(store.consume(lobby2, now = t0)).isEqualTo(arm)
        assertThat(store.peek(lobby2, now = t0)).isNull()
    }

    @Test
    fun `peek and consume return null after TTL expiry`() {
        val store = PendingArmStore(ttl = Duration.ofSeconds(60))
        store.put(lobby2, arm, now = t0)

        val later = t0.plusSeconds(61)
        assertThat(store.peek(lobby2, now = later)).isNull()
        assertThat(store.consume(lobby2, now = later)).isNull()
    }

    @Test
    fun `put on a server with an existing entry overwrites`() {
        val store = PendingArmStore(ttl = Duration.ofSeconds(60))
        store.put(lobby2, arm, now = t0)
        val newer = PendingArm(120, RestartMode.SHUTDOWN, "")
        store.put(lobby2, newer, now = t0.plusSeconds(5))

        assertThat(store.consume(lobby2, now = t0.plusSeconds(5))).isEqualTo(newer)
    }

    @Test
    fun `entries are partitioned by ServerId`() {
        val store = PendingArmStore(ttl = Duration.ofSeconds(60))
        store.put(lobby2, arm, now = t0)

        assertThat(store.peek(survival, now = t0)).isNull()
        assertThat(store.consume(lobby2, now = t0)).isEqualTo(arm)
        assertThat(store.peek(survival, now = t0)).isNull()
    }

    @Test
    fun `clear removes the slot manually`() {
        val store = PendingArmStore(ttl = Duration.ofSeconds(60))
        store.put(lobby2, arm, now = t0)
        store.clear(lobby2)
        assertThat(store.peek(lobby2, now = t0)).isNull()
    }

    @Test
    fun `cancel replaces an undelivered arm with a tombstone`() {
        val store = PendingArmStore(ttl = Duration.ofSeconds(60))
        store.put(lobby2, arm, now = t0)

        store.cancel(lobby2, now = t0.plusSeconds(1))

        assertThat(store.consumeDelivery(lobby2, now = t0.plusSeconds(2)))
            .isEqualTo(PendingArmStore.Delivery.Cancel)
    }

    @Test
    fun `a new arm replaces an older cancellation tombstone`() {
        val store = PendingArmStore(ttl = Duration.ofSeconds(60))
        store.cancel(lobby2, now = t0)

        store.put(lobby2, arm, now = t0.plusSeconds(1))

        assertThat(store.consumeDelivery(lobby2, now = t0.plusSeconds(2)))
            .isEqualTo(PendingArmStore.Delivery.Arm(arm))
    }
}
