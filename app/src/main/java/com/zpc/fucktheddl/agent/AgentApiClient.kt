package com.zpc.fucktheddl.agent

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class AgentApiClient(
    private val config: AgentApiConfig,
) {
    fun propose(text: String, sessionId: String = "android"): AgentSubmitResult {
        return try {
            val body = JSONObject()
                .put("text", text)
                .put("session_id", sessionId)
                .put("timezone", "Asia/Shanghai")
            val response = postJson("agent/propose", body)
            AgentSubmitResult(
                proposal = parseProposal(response.getJSONObject("proposal")),
                error = null,
            )
        } catch (error: Exception) {
            AgentSubmitResult(proposal = null, error = error.message ?: "请求失败")
        }
    }

    fun editProposal(proposal: AgentProposal): AgentSubmitResult {
        return try {
            val body = JSONObject()
                .put("title", proposal.title)
                .put("summary", proposal.summary)
            proposal.schedulePatch?.let { patch ->
                body.put(
                    "schedule_patch",
                    JSONObject()
                        .put("title", patch.title)
                        .put("start", patch.start)
                        .put("end", patch.end)
                        .put("timezone", patch.timezone)
                        .put("location", patch.location)
                        .put("notes", patch.notes)
                        .put("tags", patch.tags),
                )
            }
            proposal.todoPatch?.let { patch ->
                body.put(
                    "todo_patch",
                    JSONObject()
                        .put("title", patch.title)
                        .put("due", patch.due)
                        .put("timezone", patch.timezone)
                        .put("priority", patch.priority)
                        .put("notes", patch.notes)
                        .put("tags", patch.tags),
                )
            }
            AgentSubmitResult(
                proposal = parseProposal(postJson("agent/proposal/${proposal.id}/edit", body)),
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
        val text = connection.inputStream.bufferedReader().readText()
        return parseCommitments(JSONObject(text))
    }

    fun asrSession(): JSONObject {
        val connection = URL(config.normalizedBaseUrl + "asr/session").openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 5000
        connection.readTimeout = 10000
        return JSONObject(connection.inputStream.bufferedReader().readText())
    }

    private fun postJson(path: String, body: JSONObject): JSONObject {
        val connection = URL(config.normalizedBaseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 5000
        connection.readTimeout = 20000
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
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
