package com.badgersmc.queuerestart.paper

import com.badgersmc.queuerestart.common.schedule.ArmEncoding
import com.badgersmc.queuerestart.common.schedule.ProxyPollHandshake
import org.bukkit.plugin.Plugin
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.logging.Level

/**
 * REQ-022. Polls the proxy via Server-List-Ping using a magic
 * `QR_POLL:<server-id>` handshake hostname, parses any pending-arm
 * sample player from the response and dispatches it to [RestartExecutor].
 *
 * Why SLP and not plugin messages: plugin-message channels need an
 * online player on this backend; SLP doesn't. A console-armed restart
 * with no players on the target couldn't reach us via the channel; this
 * inverse SLP path closes that gap.
 *
 * Cadence is conservative ([pollIntervalSeconds] default 5). One in-flight
 * poll at a time. Failures are logged at FINE and don't surface unless
 * they persist (we don't want a noisy log when the proxy is briefly down).
 */
class ProxyArmPoller(
    private val plugin: Plugin,
    private val proxyHost: String,
    private val proxyPort: Int,
    private val serverId: String,
    private val executor: RestartExecutor,
    private val pollIntervalSeconds: Int = 5,
    private val socketTimeoutMillis: Int = 3_000,
) {

    @Volatile private var taskId: Int = -1
    @Volatile private var inFlight: Boolean = false
    @Volatile private var consecutiveFailures: Int = 0
    @Volatile private var lastArmEncoded: String? = null

    fun start() {
        if (taskId != -1) return
        // runTaskTimerAsynchronously: SLP I/O on Bukkit main thread would
        // freeze tick. The poll itself is read-only network — handing it
        // to an async worker is safe.
        val periodTicks = pollIntervalSeconds.toLong() * 20L
        taskId = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
            pollOnce()
        }, periodTicks, periodTicks).taskId
        plugin.logger.info(
            "queue-restart: arm poller started (proxy=$proxyHost:$proxyPort, server-id=$serverId, every ${pollIntervalSeconds}s)"
        )
    }

    fun stop() {
        if (taskId == -1) return
        plugin.server.scheduler.cancelTask(taskId)
        taskId = -1
    }

    private fun pollOnce() {
        if (inFlight) return
        inFlight = true
        try {
            val response = fetchStatusJson()
            val match = ARM_REGEX.find(response) ?: run {
                consecutiveFailures = 0
                lastArmEncoded = null
                return
            }
            val encoded = match.value
            // Same arm shouldn't fire twice if the proxy somehow re-emits
            // it; rely on consume() proxy-side, but defend in depth here.
            if (encoded == lastArmEncoded) return
            lastArmEncoded = encoded

            val arm = ArmEncoding.decode(encoded) ?: run {
                plugin.logger.warning("queue-restart: ignoring undecodable arm payload from proxy: $encoded")
                return
            }
            plugin.logger.info(
                "queue-restart: SLP poll-back delivered arm (delay=${arm.delaySeconds}s, mode=${arm.mode}); scheduling shutdown"
            )
            // Hop to main thread so the executor's Bukkit scheduler hand-off works.
            plugin.server.scheduler.runTask(plugin, Runnable {
                try {
                    executor.execute(arm.mode, arm.argument, arm.delaySeconds)
                } catch (t: Throwable) {
                    plugin.logger.log(Level.SEVERE, "queue-restart: executor failed on SLP-delivered arm", t)
                }
            })
            consecutiveFailures = 0
        } catch (t: Throwable) {
            consecutiveFailures++
            // Throttle the warning so a transiently unreachable proxy
            // doesn't spam the log every 5s.
            if (consecutiveFailures == 1 || consecutiveFailures % 12 == 0) {
                plugin.logger.log(
                    Level.FINE,
                    "queue-restart: arm poll failed (#$consecutiveFailures): ${t.javaClass.simpleName}: ${t.message}",
                )
            }
        } finally {
            inFlight = false
        }
    }

    /** Open TCP, send handshake + status request, read JSON, return as String. */
    private fun fetchStatusJson(): String {
        Socket().use { socket ->
            socket.soTimeout = socketTimeoutMillis
            socket.connect(InetSocketAddress(proxyHost, proxyPort), socketTimeoutMillis)
            val out = DataOutputStream(socket.getOutputStream())
            val `in` = DataInputStream(socket.getInputStream())

            val hostname = ProxyPollHandshake.formatHostname(serverId)
            // Handshake packet (0x00):
            //   protocol_version (varint, -1 for "any")
            //   server_address   (string)
            //   server_port      (unsigned short)
            //   next_state       (varint, 1 = status)
            val hs = java.io.ByteArrayOutputStream()
            val hsOut = DataOutputStream(hs)
            writeVarInt(hsOut, 0x00)               // packet id
            writeVarInt(hsOut, -1)                 // protocol version
            writeString(hsOut, hostname)
            hsOut.writeShort(proxyPort)
            writeVarInt(hsOut, 1)                  // next state: status
            writePacket(out, hs.toByteArray())

            // Status Request packet (0x00, empty payload).
            val req = java.io.ByteArrayOutputStream()
            writeVarInt(DataOutputStream(req), 0x00)
            writePacket(out, req.toByteArray())
            out.flush()

            // Read Status Response packet:
            //   length (varint)
            //   packet_id (varint, expect 0x00)
            //   json_string (string)
            readVarInt(`in`) // length — we read remainder by JSON length
            val packetId = readVarInt(`in`)
            require(packetId == 0x00) { "unexpected status packet id $packetId" }
            return readString(`in`)
        }
    }

    companion object {
        // Stops at a quote so we don't hoover up surrounding JSON. The
        // sample-player name is JSON-escaped, but neither QR_ARM:'s
        // delimiter ':' nor RestartMode names need escaping, so a literal
        // match works.
        private val ARM_REGEX = Regex("QR_ARM:[^\"]*")

        private fun writeVarInt(out: DataOutputStream, valueIn: Int) {
            var value = valueIn
            while (true) {
                if ((value and 0x7F.inv()) == 0) { out.writeByte(value); return }
                out.writeByte((value and 0x7F) or 0x80)
                value = value ushr 7
            }
        }

        private fun readVarInt(`in`: DataInputStream): Int {
            var result = 0
            var shift = 0
            while (true) {
                val b = `in`.readByte().toInt()
                result = result or ((b and 0x7F) shl shift)
                if ((b and 0x80) == 0) return result
                shift += 7
                if (shift >= 35) error("VarInt too big")
            }
        }

        private fun writeString(out: DataOutputStream, s: String) {
            val bytes = s.toByteArray(StandardCharsets.UTF_8)
            writeVarInt(out, bytes.size)
            out.write(bytes)
        }

        private fun readString(`in`: DataInputStream): String {
            val len = readVarInt(`in`)
            val buf = ByteArray(len)
            `in`.readFully(buf)
            return String(buf, StandardCharsets.UTF_8)
        }

        private fun writePacket(out: DataOutputStream, payload: ByteArray) {
            writeVarInt(out, payload.size)
            out.write(payload)
        }
    }
}
