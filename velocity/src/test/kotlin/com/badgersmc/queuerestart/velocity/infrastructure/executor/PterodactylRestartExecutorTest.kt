package com.badgersmc.queuerestart.velocity.infrastructure.executor

import com.badgersmc.queuerestart.velocity.application.ports.NetworkRestartConfig
import com.sun.net.httpserver.HttpServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class PterodactylRestartExecutorTest {
    @Test fun `sends an authenticated restart request`() {
        val calls = AtomicInteger()
        var authorization = ""
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/api/client/servers/test1234/power") { exchange ->
            calls.incrementAndGet(); authorization = exchange.requestHeaders.getFirst("Authorization")
            assertThat(exchange.requestMethod).isEqualTo("POST")
            exchange.sendResponseHeaders(204, -1); exchange.close()
        }
        server.start()
        try {
            val apiKey = "test-${UUID.randomUUID()}"
            val cfg = NetworkRestartConfig.disabled().copy(enabled = true, executorType = "PTERODACTYL", panelUrl = "http://127.0.0.1:${server.address.port}", apiKey = apiKey, allowInsecureHttp = true)
            val executor = PterodactylRestartExecutor(cfg)
            assertThat(executor.restart("plan:target", "test1234").toCompletableFuture().join().accepted).isTrue()
            assertThat(calls.get()).isEqualTo(1)
            assertThat(authorization).isEqualTo("Bearer $apiKey")
        } finally { server.stop(0) }
    }
}
