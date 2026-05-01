package com.zpc.fucktheddl.agent

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
            assertEquals("服务可达", result.label)
            assertTrue(result.detail.contains("deepseek-v4-flash"))
            assertTrue(result.detail.contains("思考: 关闭"))
        }
    }

    @Test
    fun testServiceDoesNotReportHealthyWithoutModelKey() {
        var proposeCalled = false
        withHealthServer(statusCode = 200, body = "{\"status\":\"ok\"}") { baseUrl, server ->
            server.createContext("/agent/propose") { exchange ->
                proposeCalled = true
                val bytes = """{"error":"unexpected"}""".toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(500, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }

            val result = AgentApiClient(
                AgentApiConfig(baseUrl = baseUrl),
            ).testService(AgentConnectionSettings(baseUrl = baseUrl, deepseekApiKey = ""))

            assertFalse(result.healthy)
            assertEquals("服务未就绪", result.label)
            assertFalse(proposeCalled)
        }
    }

    @Test
    fun testServiceValidatesModelKeyThroughAgentRequest() {
        var requestBody = ""
        withHealthServer(statusCode = 200, body = "{\"status\":\"ok\"}") { baseUrl, server ->
            server.createContext("/agent/propose") { exchange ->
                requestBody = exchange.requestBody.bufferedReader().readText()
                val bytes = """{"job_id":"job-service-test","status":"queued"}""".toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(202, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.createContext("/agent/jobs/job-service-test") { exchange ->
                val bytes = """
                    {
                      "job_id": "job-service-test",
                      "status": "succeeded",
                      "response": {
                        "session_id": "android-service-test",
                        "write_policy": "proposal_required",
                        "chain": [],
                        "proposal": {
                          "id": "proposal-service-test",
                          "commitment_type": "query",
                          "title": "连接测试",
                          "summary": "服务可用",
                          "impact": "",
                          "requires_confirmation": false,
                          "schedule_patch": null,
                          "todo_patch": null,
                          "delete_patch": null,
                          "update_patch": null,
                          "candidates": []
                        }
                      },
                      "error": null
                    }
                """.trimIndent().toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }

            val result = AgentApiClient(
                AgentApiConfig(baseUrl = baseUrl, accessToken = "token-1"),
            ).testService(
                AgentConnectionSettings(
                    baseUrl = baseUrl,
                    accessToken = "token-1",
                    deepseekApiKey = "sk-test",
                    deepseekBaseUrl = "https://api.deepseek.com/v1",
                    deepseekModel = "deepseek-v4-flash",
                ),
            )

            assertTrue(result.toString(), result.healthy)
            assertEquals("服务正常", result.label)
            assertTrue(requestBody.contains("\"model_api_key\":\"sk-test\""))
            assertTrue(requestBody.contains("\"disable_thinking\":true"))
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

    @Test
    fun proposePollsQueuedAgentJobUntilProposalIsReady() {
        var proposeToken = ""
        var jobToken = ""
        withHealthServer(statusCode = 200, body = "{\"status\":\"ok\"}") { baseUrl, server ->
            server.createContext("/agent/propose") { exchange ->
                proposeToken = exchange.requestHeaders.getFirst("X-Agent-Token")
                    ?: exchange.requestHeaders.getFirst("Authorization").orEmpty().removePrefix("Bearer ")
                val bytes = """{"job_id":"job-1","status":"queued"}""".toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(202, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            server.createContext("/agent/jobs/job-1") { exchange ->
                jobToken = exchange.requestHeaders.getFirst("X-Agent-Token")
                    ?: exchange.requestHeaders.getFirst("Authorization").orEmpty().removePrefix("Bearer ")
                val bytes = """
                    {
                      "job_id": "job-1",
                      "status": "succeeded",
                      "response": {
                        "session_id": "android-test",
                        "write_policy": "proposal_required",
                        "chain": [],
                        "proposal": {
                          "id": "proposal-1",
                          "commitment_type": "todo",
                          "title": "测试待办",
                          "summary": "准备创建待办：测试待办。",
                          "impact": "",
                          "requires_confirmation": true,
                          "schedule_patch": null,
                          "todo_patch": {
                            "title": "测试待办",
                            "due": "2026-05-01",
                            "timezone": "Asia/Shanghai",
                            "priority": "medium",
                            "notes": "备注",
                            "tags": []
                          },
                          "delete_patch": null,
                          "update_patch": null,
                          "candidates": [
                            {
                              "id": "evt_1",
                              "target_type": "schedule",
                              "title": "候选日程",
                              "when": "今天 09:00",
                              "detail": "备注",
                              "resolution_text": "删除 #evt_1",
                              "action_label": "删除"
                            }
                          ]
                        }
                      },
                      "error": null
                    }
                """.trimIndent().toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }

            val result = AgentApiClient(
                AgentApiConfig(baseUrl = baseUrl, accessToken = "token-1"),
            ).propose(text = "明天完成测试", sessionId = "android-test")

            assertEquals(null, result.error)
            assertEquals("token-1", proposeToken)
            assertEquals("token-1", jobToken)
            assertEquals("测试待办", result.proposal?.title)
            assertEquals("备注", result.proposal?.todoPatch?.notes)
            assertEquals("删除", result.proposal?.candidates?.firstOrNull()?.actionLabel)
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
