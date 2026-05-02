package com.zpc.fucktheddl.agent

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAgentClientTest {
    @Test
    fun proposeCallsUserModelApiAndBuildsScheduleProposal() {
        var requestBody = ""
        var authHeader = ""
        withModelServer { baseUrl, server ->
            server.createContext("/chat/completions") { exchange ->
                authHeader = exchange.requestHeaders.getFirst("Authorization").orEmpty()
                requestBody = exchange.requestBody.bufferedReader().readText()
                val bytes = """
                    {
                      "choices": [
                        {
                          "message": {
                            "content": "{\"commitment_type\":\"schedule\",\"title\":\"项目会\",\"date\":\"明天\",\"time\":\"下午三点\",\"due\":\"\",\"priority\":\"medium\",\"notes\":\"带电脑\"}"
                          }
                        }
                      ]
                    }
                """.trimIndent().toByteArray(Charsets.UTF_8)
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }

            val result = LocalAgentClient().propose(
                text = "明天下午三点项目会，记得带电脑",
                sessionId = "local-test",
                commitments = AgentCommitmentsPayload(emptyList(), emptyList()),
                settings = AgentConnectionSettings(
                    deepseekApiKey = "sk-local",
                    deepseekBaseUrl = baseUrl,
                    deepseekModel = "deepseek-v4-flash",
                ),
            )

            assertEquals(null, result.error)
            assertEquals("Bearer sk-local", authHeader)
            assertTrue(requestBody.contains("\"model\":\"deepseek-v4-flash\""))
            assertEquals(CommitmentType.Schedule, result.proposal?.commitmentType)
            assertEquals("项目会", result.proposal?.title)
            assertEquals("带电脑", result.proposal?.schedulePatch?.notes)
            assertTrue(result.proposal?.schedulePatch?.start.orEmpty().contains("15:00:00"))
        }
    }

    @Test
    fun localAliyunSessionComesFromConnectionSettings() {
        val session = localAliyunAsrSession(
            AgentConnectionSettings(
                aliyunApiKey = "aliyun-user-key",
                aliyunAsrUrl = "wss://aliyun.example/asr",
            ),
        )

        assertEquals("aliyun-user-key", session.getString("api_key"))
        assertEquals("wss://aliyun.example/asr", session.getString("url"))
        assertEquals("fun-asr-realtime-2025-09-15", session.getString("model"))
        assertEquals(16000, session.getInt("sample_rate"))
        assertEquals(4, session.getInt("service_type"))
    }

    @Test
    fun shortTrailingCarryPhraseBecomesScheduleNotesNotTitle() {
        val proposal = LocalAgentEngine(
            todayProvider = { java.time.LocalDate.of(2026, 5, 2) },
        ).draftProposal(
            text = "明天下午三点项目会，带电脑",
            sessionId = "notes-test",
            modelExtraction = JSONObject(
                """
                    {
                      "commitment_type": "schedule",
                      "title": "项目会带电脑",
                      "date": "明天",
                      "time": "下午三点",
                      "notes": ""
                    }
                """.trimIndent(),
            ),
            commitments = AgentCommitmentsPayload(emptyList(), emptyList()),
        )

        assertEquals("项目会", proposal.title)
        assertEquals("带电脑", proposal.schedulePatch?.notes)
    }

    @Test
    fun shortTrailingMaterialPhraseBecomesTodoNotesNotTitle() {
        val proposal = LocalAgentEngine(
            todayProvider = { java.time.LocalDate.of(2026, 5, 2) },
        ).draftProposal(
            text = "周五前交报告，材料放在群文件",
            sessionId = "todo-notes-test",
            modelExtraction = JSONObject(
                """
                    {
                      "commitment_type": "todo",
                      "title": "交报告材料放在群文件",
                      "date": "",
                      "time": "",
                      "due": "周五",
                      "priority": "medium",
                      "notes": ""
                    }
                """.trimIndent(),
            ),
            commitments = AgentCommitmentsPayload(emptyList(), emptyList()),
        )

        assertEquals("交报告", proposal.title)
        assertEquals("材料放在群文件", proposal.todoPatch?.notes)
    }

    @Test
    fun deadlineQueryIncludesSchedulesAndTodosForToday() {
        val proposal = LocalAgentEngine(
            todayProvider = { LocalDate.of(2026, 5, 2) },
        ).draftProposal(
            text = "今天有哪些活动要截止",
            sessionId = "deadline-query-today",
            modelExtraction = JSONObject("""{"commitment_type":"query"}"""),
            commitments = AgentCommitmentsPayload(
                events = listOf(
                    BackendScheduleEvent(
                        id = "schedule-today",
                        title = "项目会",
                        start = "2026-05-02T15:00:00+08:00",
                        end = "2026-05-02T16:00:00+08:00",
                        status = "active",
                        location = "",
                        notes = "",
                        tags = emptyList(),
                    ),
                ),
                todos = listOf(
                    BackendTodoItem(
                        id = "todo-today",
                        title = "交报告",
                        due = "2026-05-02",
                        status = "active",
                        priority = "high",
                        notes = "",
                        tags = emptyList(),
                    ),
                    BackendTodoItem(
                        id = "todo-tomorrow",
                        title = "整理资料",
                        due = "2026-05-03",
                        status = "active",
                        priority = "medium",
                        notes = "",
                        tags = emptyList(),
                    ),
                ),
            ),
        )

        assertEquals(CommitmentType.Query, proposal.commitmentType)
        assertTrue(proposal.summary.contains("日程：15:00 项目会"))
        assertTrue(proposal.summary.contains("待办：交报告"))
        assertTrue(!proposal.summary.contains("整理资料"))
    }

    @Test
    fun futureDeadlineQueryUsesInclusiveDeadlineWindow() {
        val proposal = LocalAgentEngine(
            todayProvider = { LocalDate.of(2026, 5, 2) },
        ).draftProposal(
            text = "明天和后天截止的有哪些",
            sessionId = "deadline-query-window",
            modelExtraction = JSONObject("""{"commitment_type":"query"}"""),
            commitments = AgentCommitmentsPayload(
                events = listOf(
                    BackendScheduleEvent(
                        id = "schedule-today",
                        title = "今天复盘",
                        start = "2026-05-02T09:00:00+08:00",
                        end = "2026-05-02T10:00:00+08:00",
                        status = "active",
                        location = "",
                        notes = "",
                        tags = emptyList(),
                    ),
                    BackendScheduleEvent(
                        id = "schedule-after-window",
                        title = "下周会",
                        start = "2026-05-05T09:00:00+08:00",
                        end = "2026-05-05T10:00:00+08:00",
                        status = "active",
                        location = "",
                        notes = "",
                        tags = emptyList(),
                    ),
                ),
                todos = listOf(
                    BackendTodoItem(
                        id = "todo-tomorrow",
                        title = "交作业",
                        due = "2026-05-03",
                        status = "active",
                        priority = "high",
                        notes = "",
                        tags = emptyList(),
                    ),
                    BackendTodoItem(
                        id = "todo-after-tomorrow",
                        title = "提交申请",
                        due = "2026-05-04",
                        status = "active",
                        priority = "medium",
                        notes = "",
                        tags = emptyList(),
                    ),
                    BackendTodoItem(
                        id = "todo-after-window",
                        title = "准备答辩",
                        due = "2026-05-05",
                        status = "active",
                        priority = "medium",
                        notes = "",
                        tags = emptyList(),
                    ),
                ),
            ),
        )

        assertEquals("后天前截止", proposal.title)
        assertTrue(proposal.summary.contains("今天复盘"))
        assertTrue(proposal.summary.contains("交作业"))
        assertTrue(proposal.summary.contains("提交申请"))
        assertTrue(!proposal.summary.contains("下周会"))
        assertTrue(!proposal.summary.contains("准备答辩"))
    }

    private fun withModelServer(block: (baseUrl: String, server: HttpServer) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            block("http://127.0.0.1:${server.address.port}", server)
        } finally {
            server.stop(0)
        }
    }
}
