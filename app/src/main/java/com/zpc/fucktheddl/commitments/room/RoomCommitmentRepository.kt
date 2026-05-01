package com.zpc.fucktheddl.commitments.room

import com.zpc.fucktheddl.agent.AgentApplyResult
import com.zpc.fucktheddl.agent.AgentCommitmentsPayload
import com.zpc.fucktheddl.agent.AgentProposal
import com.zpc.fucktheddl.agent.AgentSchedulePatch
import com.zpc.fucktheddl.agent.AgentTodoPatch
import com.zpc.fucktheddl.agent.BackendScheduleEvent
import com.zpc.fucktheddl.agent.BackendTodoItem
import com.zpc.fucktheddl.agent.CommitmentType
import com.zpc.fucktheddl.commitments.CommitmentRepository
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class RoomCommitmentRepository(
    private val database: CommitmentDatabase,
) : CommitmentRepository {
    private val dao: CommitmentDao = database.commitmentDao()

    override fun listCommitments(ownerUserId: String): AgentCommitmentsPayload {
        return AgentCommitmentsPayload(
            events = dao.listSchedules(ownerUserId).map { it.toBackendEvent() },
            todos = dao.listTodos(ownerUserId).map { it.toBackendTodo() },
        )
    }

    override fun applyProposal(ownerUserId: String, proposal: AgentProposal): AgentApplyResult {
        return runCatching {
            when (proposal.commitmentType) {
                CommitmentType.Schedule -> {
                    val patch = requireNotNull(proposal.schedulePatch)
                    upsertSchedule(ownerUserId, patch)
                }
                CommitmentType.Todo -> {
                    val patch = requireNotNull(proposal.todoPatch)
                    upsertTodo(ownerUserId, patch)
                }
                CommitmentType.Delete -> {
                    val target = requireNotNull(proposal.deletePatch)
                    cancelCommitment(ownerUserId, target.targetId)
                    target.targetId to target.targetType
                }
                CommitmentType.Update -> updateProposal(ownerUserId, proposal)
                else -> error("Proposal is not a local write")
            }
        }.fold(
            onSuccess = { (id, _) -> AgentApplyResult(status = "applied", commitmentId = id, error = null) },
            onFailure = { error -> AgentApplyResult(status = "failed", commitmentId = "", error = error.message) },
        )
    }

    override fun deleteCommitment(ownerUserId: String, commitmentId: String): AgentApplyResult {
        return runCatching {
            cancelCommitment(ownerUserId, commitmentId)
            AgentApplyResult(status = "undone", commitmentId = commitmentId, error = null)
        }.getOrElse { error ->
            AgentApplyResult(status = "failed", commitmentId = "", error = error.message)
        }
    }

    private fun updateProposal(ownerUserId: String, proposal: AgentProposal): Pair<String, String> {
        val updatePatch = requireNotNull(proposal.updatePatch)
        proposal.schedulePatch?.let { patch ->
            upsertSchedule(ownerUserId, patch, id = updatePatch.targetId)
            return updatePatch.targetId to "schedule"
        }
        proposal.todoPatch?.let { patch ->
            upsertTodo(ownerUserId, patch, id = updatePatch.targetId)
            return updatePatch.targetId to "todo"
        }
        updatePatch.schedulePatch?.let { patch ->
            upsertSchedule(ownerUserId, patch, id = updatePatch.targetId)
            return updatePatch.targetId to "schedule"
        }
        updatePatch.todoPatch?.let { patch ->
            upsertTodo(ownerUserId, patch, id = updatePatch.targetId)
            return updatePatch.targetId to "todo"
        }
        error("Update proposal has no replacement patch")
    }

    private fun upsertSchedule(
        ownerUserId: String,
        patch: AgentSchedulePatch,
        id: String = scheduleId(patch),
    ): Pair<String, String> {
        val now = nowIso()
        dao.upsertSchedule(
            ScheduleEntity(
                id = id,
                ownerUserId = ownerUserId,
                title = patch.title,
                startAt = patch.start,
                endAt = patch.end,
                timezone = patch.timezone,
                location = patch.location,
                notes = patch.notes,
                tags = patch.tags.joinToString(","),
                status = "confirmed",
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id to "schedule"
    }

    private fun upsertTodo(
        ownerUserId: String,
        patch: AgentTodoPatch,
        id: String = todoId(patch),
    ): Pair<String, String> {
        val now = nowIso()
        dao.upsertTodo(
            TodoEntity(
                id = id,
                ownerUserId = ownerUserId,
                title = patch.title,
                due = patch.due,
                timezone = patch.timezone,
                priority = patch.priority,
                notes = patch.notes,
                tags = patch.tags.joinToString(","),
                status = "active",
                createdAt = now,
                updatedAt = now,
            ),
        )
        return id to "todo"
    }

    private fun cancelCommitment(ownerUserId: String, commitmentId: String) {
        val updatedAt = nowIso()
        val eventRows = dao.cancelSchedule(ownerUserId, commitmentId, updatedAt)
        val todoRows = dao.cancelTodo(ownerUserId, commitmentId, updatedAt)
        check(eventRows + todoRows > 0) { "Commitment not found" }
    }
}

private fun ScheduleEntity.toBackendEvent(): BackendScheduleEvent {
    return BackendScheduleEvent(
        id = id,
        title = title,
        start = startAt,
        end = endAt,
        status = status,
        location = location,
        notes = notes,
        tags = tags.splitTags(),
    )
}

private fun TodoEntity.toBackendTodo(): BackendTodoItem {
    return BackendTodoItem(
        id = id,
        title = title,
        due = due,
        status = status,
        priority = priority,
        notes = notes,
        tags = tags.splitTags(),
    )
}

private fun scheduleId(patch: AgentSchedulePatch): String {
    return "evt_${patch.start.take(10).replace("-", "")}_${patch.start.substring(11, 19).replace(":", "")}_${slug(patch.title)}"
}

private fun todoId(patch: AgentTodoPatch): String {
    return "todo_${patch.due.replace("-", "")}_${slug(patch.title)}"
}

private fun slug(text: String): String {
    return text.trim()
        .lowercase(Locale.ROOT)
        .map { char -> if (char.isLetterOrDigit()) char else '_' }
        .joinToString("")
        .split('_')
        .filter { it.isNotBlank() }
        .joinToString("_")
        .take(32)
        .ifBlank { "item" }
}

private fun String.splitTags(): List<String> {
    return split(",").map { it.trim() }.filter { it.isNotBlank() }
}

private fun nowIso(): String = OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
