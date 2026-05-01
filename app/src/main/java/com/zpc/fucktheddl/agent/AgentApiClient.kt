package com.zpc.fucktheddl.agent

import com.zpc.fucktheddl.auth.LoginCodeVerifyResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class AgentApiClient(
    private val config: AgentApiConfig,
) {
    fun testConnection(): AgentConnectionTestResult {
        return try {
            val connection = URL(config.normalizedBaseUrl + "health").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 8000
            connection.applyAuth()
            val responseCode = connection.responseCode
            val text = connection.responseText()
            if (responseCode in 200..299) {
                val status = text.extractJsonString("status")
                val framework = text.extractJsonString("agent_framework")
                val modelName = text.extractJsonString("model")
                val thinkingDisabled = text.extractJsonBoolean("disable_thinking")
                AgentConnectionTestResult(
                    healthy = true,
                    label = if (status == "ok") "服务可达" else "服务已响应",
                    detail = listOfNotNull(
                        framework.takeIf { it.isNotBlank() }?.let { "Agent: $it" },
                        modelName.takeIf { it.isNotBlank() }?.let { "模型: $it" },
                        thinkingDisabled?.let { if (it) "思考: 关闭" else "思考: 开启" },
                    ).joinToString(" · "),
                )
            } else {
                AgentConnectionTestResult(
                    healthy = false,
                    label = "连接失败",
                    detail = "HTTP $responseCode${text.safeErrorSummary()}",
                )
            }
        } catch (error: Exception) {
            AgentConnectionTestResult(
                healthy = false,
                label = "无法连接后端",
                detail = error.message?.take(120).orEmpty(),
            )
        }
    }

    fun testService(settings: AgentConnectionSettings): AgentConnectionTestResult {
        val health = testConnection()
        if (!health.healthy) {
            return health.copy(label = "服务不可用")
        }
        if (settings.deepseekApiKey.isBlank()) {
            return AgentConnectionTestResult(
                healthy = false,
                label = "服务未就绪",
            )
        }
        val result = propose(
            text = "今天有哪些安排？",
            sessionId = "android-service-test-${UUID.randomUUID()}",
            commitments = AgentCommitmentsPayload(events = emptyList(), todos = emptyList()),
            settings = settings,
        )
        return if (result.error == null) {
            AgentConnectionTestResult(
                healthy = true,
                label = "服务正常",
                detail = health.detail,
            )
        } else {
            AgentConnectionTestResult(
                healthy = false,
                label = "服务不可用",
                detail = result.error.safeErrorSummary().removePrefix(" · "),
            )
        }
    }

    fun requestLoginCode(email: String): String? {
        return try {
            postJsonAllowEmpty(
                "auth/code/request",
                JSONObject().put("email", email.trim()),
            )
            null
        } catch (error: Exception) {
            error.message ?: "验证码发送失败"
        }
    }

    fun verifyLoginCode(email: String, code: String): LoginCodeVerifyResult {
        return try {
            val response = postJson(
                "auth/code/verify",
                JSONObject()
                    .put("email", email.trim())
                    .put("code", code.trim()),
            )
            LoginCodeVerifyResult(
                userId = response.optString("user_id", ""),
                email = response.optString("email", ""),
                accessToken = response.optString("access_token", ""),
                newlyCreated = response.optBoolean("newly_created", false),
                error = null,
            )
        } catch (error: Exception) {
            LoginCodeVerifyResult(error = error.message ?: "登录失败")
        }
    }

    fun propose(
        text: String,
        sessionId: String = "android-${UUID.randomUUID()}",
        commitments: AgentCommitmentsPayload? = null,
        settings: AgentConnectionSettings? = null,
    ): AgentSubmitResult {
        return try {
            val body = JSONObject()
                .put("text", text)
                .put("session_id", sessionId)
                .put("timezone", "Asia/Shanghai")
            commitments?.let { body.put("commitments", it.toJson()) }
            settings?.takeIf { it.deepseekApiKey.isNotBlank() }?.let {
                body.put("model_api_key", it.deepseekApiKey.trim())
                    .put("model_base_url", it.deepseekBaseUrl.trim())
                    .put("model", it.deepseekModel.trim())
                    .put("disable_thinking", true)
            }
            val response = postJson("agent/propose", body)
            if (response.has("job_id")) {
                return pollAgentJob(response.getString("job_id"))
            }
            AgentSubmitResult(
                proposal = parseProposal(response.getJSONObject("proposal")),
                error = null,
            )
        } catch (error: Exception) {
            AgentSubmitResult(proposal = null, error = error.message ?: "请求失败")
        }
    }

    private fun pollAgentJob(jobId: String): AgentSubmitResult {
        repeat(60) {
            val body = getJson("agent/jobs/$jobId")
            when (body.optString("status")) {
                "succeeded" -> {
                    val response = body.optJSONObject("response")
                        ?: return AgentSubmitResult(proposal = null, error = "任务结果为空")
                    return AgentSubmitResult(
                        proposal = parseProposal(response.getJSONObject("proposal")),
                        error = null,
                    )
                }
                "failed" -> {
                    return AgentSubmitResult(
                        proposal = null,
                        error = body.optString("error", "AI 任务失败"),
                    )
                }
            }
            Thread.sleep(500)
        }
        return AgentSubmitResult(proposal = null, error = "AI 任务超时")
    }

    fun editProposal(proposal: AgentProposal): AgentSubmitResult {
        return try {
            AgentSubmitResult(
                proposal = parseProposal(postJson("agent/proposal/${proposal.id}/edit", proposal.toEditRequestJson())),
                error = null,
            )
        } catch (error: Exception) {
            AgentSubmitResult(proposal = null, error = error.message ?: "编辑失败")
        }
    }

    fun confirm(proposalId: String): AgentApplyResult {
        return apply("agent/confirm/$proposalId")
    }

    fun undo(commitmentId: String): AgentApplyResult {
        return apply("agent/undo/$commitmentId")
    }

    fun commitments(): AgentCommitmentsPayload {
        val connection = URL(config.normalizedBaseUrl + "commitments").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 10000
        connection.applyAuth()
        val text = connection.inputStream.bufferedReader().readText()
        return parseCommitments(JSONObject(text))
    }

    fun asrSession(): JSONObject {
        val connection = URL(config.normalizedBaseUrl + "asr/session").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 10000
        connection.applyAuth()
        return JSONObject(connection.inputStream.bufferedReader().readText())
    }

    private fun postJson(path: String, body: JSONObject): JSONObject {
        val connection = URL(config.normalizedBaseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 20000
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.applyAuth()
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body.toString())
        }
        val stream = if (connection.responseCode in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }
        val text = stream.bufferedReader().readText()
        if (connection.responseCode !in 200..299) {
            error(text)
        }
        return JSONObject(text)
    }

    private fun postJsonAllowEmpty(path: String, body: JSONObject) {
        val connection = URL(config.normalizedBaseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 20000
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.applyAuth()
        OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
            writer.write(body.toString())
        }
        val text = connection.responseText()
        if (connection.responseCode !in 200..299) {
            error(text)
        }
    }

    private fun getJson(path: String): JSONObject {
        val connection = URL(config.normalizedBaseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 10000
        connection.applyAuth()
        val text = connection.responseText()
        if (connection.responseCode !in 200..299) {
            error(text)
        }
        return JSONObject(text)
    }

    private fun apply(path: String): AgentApplyResult {
        return try {
            val body = postJson(path, JSONObject())
            AgentApplyResult(
                status = body.getString("status"),
                commitmentId = body.getString("commitment_id"),
                error = null,
            )
        } catch (error: Exception) {
            AgentApplyResult(status = "failed", commitmentId = "", error = error.message)
        }
    }

    private fun HttpURLConnection.applyAuth() {
        if (config.accessToken.isNotBlank()) {
            setRequestProperty("X-Agent-Token", config.accessToken)
            setRequestProperty("Authorization", "Bearer ${config.accessToken}")
        }
    }

    private fun HttpURLConnection.responseText(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.bufferedReader()?.readText().orEmpty()
    }
}

private fun String.safeErrorSummary(): String {
    val summary = trim()
        .replace(Regex("\"model_api_key\"\\s*:\\s*\"[^\"]*\""), "\"model_api_key\":\"***\"")
        .replace(Regex("\"api_key\"\\s*:\\s*\"[^\"]*\""), "\"api_key\":\"***\"")
        .take(140)
    return if (summary.isBlank()) "" else " · $summary"
}

private fun String.extractJsonString(field: String): String {
    val pattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*\"([^\"]*)\"")
    return pattern.find(this)?.groupValues?.getOrNull(1).orEmpty()
}

private fun String.extractJsonBoolean(field: String): Boolean? {
    val pattern = Regex("\"${Regex.escape(field)}\"\\s*:\\s*(true|false)")
    return pattern.find(this)?.groupValues?.getOrNull(1)?.toBooleanStrictOrNull()
}

private fun AgentCommitmentsPayload.toJson(): JSONObject {
    return JSONObject()
        .put(
            "events",
            JSONArray(
                events.map { event ->
                    JSONObject()
                        .put("id", event.id)
                        .put("title", event.title)
                        .put("start", event.start)
                        .put("end", event.end)
                        .put("timezone", "Asia/Shanghai")
                        .put("status", event.status)
                        .put("location", event.location)
                        .put("notes", event.notes)
                        .put("tags", JSONArray(event.tags))
                },
            ),
        )
        .put(
            "todos",
            JSONArray(
                todos.map { todo ->
                    JSONObject()
                        .put("id", todo.id)
                        .put("title", todo.title)
                        .put("due", todo.due)
                        .put("timezone", "Asia/Shanghai")
                        .put("status", todo.status)
                        .put("priority", todo.priority)
                        .put("notes", todo.notes)
                        .put("tags", JSONArray(todo.tags))
                },
            ),
        )
}

internal fun AgentProposal.toEditRequestJson(): JSONObject {
    val body = JSONObject()
        .put("title", title)
        .put("summary", summary)
    schedulePatch?.let { patch ->
        body.put(
            "schedule_patch",
            JSONObject()
                .put("title", patch.title)
                .put("start", patch.start)
                .put("end", patch.end)
                .put("timezone", patch.timezone)
                .put("location", patch.location)
                .put("notes", patch.notes)
                .put("tags", JSONArray(patch.tags)),
        )
    }
    todoPatch?.let { patch ->
        body.put(
            "todo_patch",
            JSONObject()
                .put("title", patch.title)
                .put("due", patch.due)
                .put("timezone", patch.timezone)
                .put("priority", patch.priority)
                .put("notes", patch.notes)
                .put("tags", JSONArray(patch.tags)),
        )
    }
    return body
}

internal fun shouldEditProposalBeforeConfirm(
    proposal: AgentProposal,
    edited: Boolean,
): Boolean {
    return edited && (proposal.schedulePatch != null || proposal.todoPatch != null)
}

private fun parseProposal(proposalJson: JSONObject): AgentProposal {
    return AgentProposal(
        id = proposalJson.getString("id"),
        commitmentType = proposalJson.getString("commitment_type").toCommitmentType(),
        title = proposalJson.getString("title"),
        summary = proposalJson.getString("summary"),
        impact = proposalJson.getString("impact"),
        requiresConfirmation = proposalJson.getBoolean("requires_confirmation"),
        schedulePatch = proposalJson.optSchedulePatch("schedule_patch"),
        todoPatch = proposalJson.optTodoPatch("todo_patch"),
        deletePatch = proposalJson.optDeletePatch("delete_patch"),
        updatePatch = proposalJson.optUpdatePatch("update_patch"),
        candidates = proposalJson.optCandidateList("candidates"),
    )
}

private fun parseCommitments(response: JSONObject): AgentCommitmentsPayload {
    val eventsJson = response.getJSONArray("events")
    val events = List(eventsJson.length()) { index ->
        val item = eventsJson.getJSONObject(index)
        BackendScheduleEvent(
            id = item.optString("id", ""),
            title = item.getString("title"),
            start = item.getString("start"),
            end = item.getString("end"),
            status = item.optString("status", "confirmed"),
            location = item.optString("location", ""),
            notes = item.optString("notes", ""),
            tags = item.optStringList("tags"),
        )
    }
    val todosJson = response.getJSONArray("todos")
    val todos = List(todosJson.length()) { index ->
        val item = todosJson.getJSONObject(index)
        BackendTodoItem(
            id = item.optString("id", ""),
            title = item.getString("title"),
            due = item.getString("due"),
            status = item.optString("status", "active"),
            priority = item.optString("priority", "medium"),
            notes = item.optString("notes", ""),
            tags = item.optStringList("tags"),
        )
    }
    return AgentCommitmentsPayload(events = events, todos = todos)
}

private fun JSONObject.optSchedulePatch(name: String): AgentSchedulePatch? {
    val item = optJSONObject(name) ?: return null
    return AgentSchedulePatch(
        title = item.optString("title", ""),
        start = item.optString("start", ""),
        end = item.optString("end", ""),
        timezone = item.optString("timezone", "Asia/Shanghai"),
        location = item.optString("location", ""),
        notes = item.optString("notes", ""),
        tags = item.optStringList("tags"),
    )
}

private fun JSONObject.optTodoPatch(name: String): AgentTodoPatch? {
    val item = optJSONObject(name) ?: return null
    return AgentTodoPatch(
        title = item.optString("title", ""),
        due = item.optString("due", ""),
        timezone = item.optString("timezone", "Asia/Shanghai"),
        priority = item.optString("priority", "medium"),
        notes = item.optString("notes", ""),
        tags = item.optStringList("tags"),
    )
}

private fun JSONObject.optDeletePatch(name: String): AgentDeletePatch? {
    val item = optJSONObject(name) ?: return null
    return AgentDeletePatch(
        targetId = item.optString("target_id", ""),
        targetType = item.optString("target_type", ""),
        targetTitle = item.optString("target_title", ""),
    )
}

private fun JSONObject.optUpdatePatch(name: String): AgentUpdatePatch? {
    val item = optJSONObject(name) ?: return null
    return AgentUpdatePatch(
        targetId = item.optString("target_id", ""),
        targetType = item.optString("target_type", ""),
        targetTitle = item.optString("target_title", ""),
        schedulePatch = item.optSchedulePatch("schedule_patch"),
        todoPatch = item.optTodoPatch("todo_patch"),
    )
}

private fun JSONObject.optStringList(name: String): List<String> {
    val array = optJSONArray(name) ?: return emptyList()
    return List(array.length()) { index -> array.optString(index) }
        .filter { it.isNotBlank() }
}

private fun JSONObject.optCandidateList(name: String): List<AgentProposalCandidate> {
    val array = optJSONArray(name) ?: return emptyList()
    return List(array.length()) { index ->
        val item = array.getJSONObject(index)
        AgentProposalCandidate(
            id = item.optString("id", ""),
            targetType = item.optString("target_type", ""),
            title = item.optString("title", ""),
            whenLabel = item.optString("when", ""),
            detail = item.optString("detail", ""),
            resolutionText = item.optString("resolution_text", ""),
            actionLabel = item.optString("action_label", "选择"),
        )
    }.filter { it.resolutionText.isNotBlank() && it.title.isNotBlank() }
}

private fun String.toCommitmentType(): CommitmentType {
    return when (this) {
        "schedule" -> CommitmentType.Schedule
        "todo" -> CommitmentType.Todo
        "delete" -> CommitmentType.Delete
        "update" -> CommitmentType.Update
        "query" -> CommitmentType.Query
        "suggestion" -> CommitmentType.Suggestion
        else -> CommitmentType.Clarify
    }
}
