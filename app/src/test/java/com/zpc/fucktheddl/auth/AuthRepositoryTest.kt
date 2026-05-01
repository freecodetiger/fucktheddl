package com.zpc.fucktheddl.auth

import com.sun.net.httpserver.HttpServer
import com.zpc.fucktheddl.agent.AgentApiClient
import com.zpc.fucktheddl.agent.AgentApiConfig
import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun requestCodePostsEmail() {
        var receivedBody = ""
        withServer { baseUrl, server ->
            server.createContext("/auth/code/request") { exchange ->
                receivedBody = exchange.requestBody.bufferedReader().readText()
                exchange.sendResponseHeaders(204, -1)
            }

            val result = AuthRepository(AgentApiClient(AgentApiConfig(baseUrl)))
                .requestCode("user@example.com")

            assertNull(result)
            assertEquals(true, receivedBody.contains("\"email\":\"user@example.com\""))
        }
    }

    @Test
    fun verifyCodeParsesSession() {
        withServer { baseUrl, server ->
            server.createContext("/auth/code/verify") { exchange ->
                val response = """
                    {
                      "user_id": "usr_1",
                      "email": "user@example.com",
                      "access_token": "token-1",
                      "newly_created": true
                    }
                """.trimIndent()
                val bytes = response.toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }

            val result = AuthRepository(AgentApiClient(AgentApiConfig(baseUrl)))
                .verifyCode("user@example.com", "123456")

            assertEquals("usr_1", result.userId)
            assertEquals("user@example.com", result.email)
            assertEquals("token-1", result.accessToken)
            assertEquals(true, result.newlyCreated)
            assertNull(result.error)
        }
    }

    private fun withServer(block: (baseUrl: String, server: HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}", server)
        } finally {
            server.stop(0)
        }
    }
}
