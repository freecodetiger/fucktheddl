package com.zpc.fucktheddl.agent

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentApiClientTest {
    @Test
    fun testConnectionReportsHealthyBackend() {
        withHealthServer(
            statusCode = 200,
            body = """
                {"status":"ok","agent_framework":"langgraph","model":{"model":"deepseek-v4-flash","disable_thinking":true}}
            """.trimIndent(),
        ) { baseUrl, _ ->
            val result = AgentApiClient(AgentApiConfig(baseUrl = baseUrl)).testConnection()

            assertEquals(result.toString(), true, result.healthy)
            assertEquals("后端连接正常", result.label)
            assertTrue(result.detail.contains("deepseek-v4-flash"))
            assertTrue(result.detail.contains("思考: 关闭"))
        }
    }

    @Test
    fun testConnectionSendsAccessToken() {
        var receivedToken = ""
        withHealthServer(statusCode = 200, body = "{\"status\":\"ok\"}") { baseUrl, server ->
            server.removeContext("/health")
            server.createContext("/health") { exchange ->
                receivedToken = exchange.requestHeaders.getFirst("X-Agent-Token").orEmpty()
                val bytes = "{\"status\":\"ok\"}".toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }

            AgentApiClient(
                AgentApiConfig(baseUrl = baseUrl, accessToken = "token-1"),
            ).testConnection()

            assertEquals("token-1", receivedToken)
        }
    }

    private fun withHealthServer(
        statusCode: Int,
        body: String,
        block: (baseUrl: String, server: HttpServer) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/health") { exchange ->
            val bytes = body.toByteArray(Charsets.UTF_8)
            exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}", server)
        } finally {
            server.stop(0)
        }
    }
}
