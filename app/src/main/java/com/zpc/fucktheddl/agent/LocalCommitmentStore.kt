package com.zpc.fucktheddl.agent

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class LocalCommitmentStore(context: Context) : SQLiteOpenHelper(context, "fucktheddl.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE events (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                start_at TEXT NOT NULL,
                end_at TEXT NOT NULL,
                timezone TEXT NOT NULL,
                location TEXT NOT NULL,
                notes TEXT NOT NULL,
                tags TEXT NOT NULL,
                status TEXT NOT NULL,
                year_month TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_events_year_month ON events(year_month)")
        db.execSQL(
            """
            CREATE TABLE todos (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                due TEXT NOT NULL,
                timezone TEXT NOT NULL,
                priority TEXT NOT NULL,
                notes TEXT NOT NULL,
                tags TEXT NOT NULL,
                status TEXT NOT NULL,
                year_month TEXT NOT NULL,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_todos_year_month ON todos(year_month)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun listCommitments(): AgentCommitmentsPayload {
        return AgentCommitmentsPayload(
            events = readableDatabase.queryEvents(),
            todos = readableDatabase.queryTodos(),
        )
    }

    fun applyProposal(proposal: AgentProposal): AgentApplyResult {
        return runCatching {
            when (proposal.commitmentType) {
                CommitmentType.Schedule -> {
                    val patch = requireNotNull(proposal.schedulePatch)
                    upsertSchedule(patch)
                }
                CommitmentType.Todo -> {
                    val patch = requireNotNull(proposal.todoPatch)
                    upsertTodo(patch)
                }
                CommitmentType.Delete -> {
                    val target = requireNotNull(proposal.deletePatch)
                    cancelCommitment(target.targetId)
                    target.targetId to target.targetType
                }
                CommitmentType.Update -> updateProposal(proposal)
                else -> error("Proposal is not a local write")
            }
        }.fold(
            onSuccess = { (id, _) -> AgentApplyResult(status = "applied", commitmentId = id, error = null) },
            onFailure = { error -> AgentApplyResult(status = "failed", commitmentId = "", error = error.message) },
        )
    }

    fun deleteCommitment(commitmentId: String): AgentApplyResult {
        return runCatching {
            cancelCommitment(commitmentId)
            AgentApplyResult(status = "undone", commitmentId = commitmentId, error = null)
        }.getOrElse { error ->
            AgentApplyResult(status = "failed", commitmentId = "", error = error.message)
        }
    }

    private fun updateProposal(proposal: AgentProposal): Pair<String, String> {
        val updatePatch = requireNotNull(proposal.updatePatch)
        proposal.schedulePatch?.let { patch ->
            upsertSchedule(patch, id = updatePatch.targetId)
            return updatePatch.targetId to "schedule"
        }
        proposal.todoPatch?.let { patch ->
            upsertTodo(patch, id = updatePatch.targetId)
            return updatePatch.targetId to "todo"
        }
        updatePatch.schedulePatch?.let { patch ->
            upsertSchedule(patch, id = updatePatch.targetId)
            return updatePatch.targetId to "schedule"
        }
        updatePatch.todoPatch?.let { patch ->
            upsertTodo(patch, id = updatePatch.targetId)
            return updatePatch.targetId to "todo"
        }
        error("Update proposal has no replacement patch")
    }

    private fun upsertSchedule(
        patch: AgentSchedulePatch,
        id: String = scheduleId(patch),
    ): Pair<String, String> {
        val now = nowIso()
        val values = ContentValues().apply {
            put("id", id)
            put("title", patch.title)
            put("start_at", patch.start)
            put("end_at", patch.end)
            put("timezone", patch.timezone)
            put("location", patch.location)
            put("notes", patch.notes)
            put("tags", patch.tags.joinToString(","))
            put("status", "confirmed")
            put("year_month", patch.start.take(7))
            put("created_at", now)
            put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return id to "schedule"
    }

    private fun upsertTodo(
        patch: AgentTodoPatch,
        id: String = todoId(patch),
    ): Pair<String, String> {
        val now = nowIso()
        val values = ContentValues().apply {
            put("id", id)
            put("title", patch.title)
            put("due", patch.due)
            put("timezone", patch.timezone)
            put("priority", patch.priority)
            put("notes", patch.notes)
            put("tags", patch.tags.joinToString(","))
            put("status", "active")
            put("year_month", patch.due.take(7))
            put("created_at", now)
            put("updated_at", now)
        }
        writableDatabase.insertWithOnConflict("todos", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return id to "todo"
    }

    private fun cancelCommitment(commitmentId: String) {
        val values = ContentValues().apply {
            put("status", "cancelled")
            put("updated_at", nowIso())
        }
        val eventRows = writableDatabase.update("events", values, "id = ?", arrayOf(commitmentId))
        val todoRows = writableDatabase.update("todos", values, "id = ?", arrayOf(commitmentId))
        check(eventRows + todoRows > 0) { "Commitment not found" }
    }
}

private fun SQLiteDatabase.queryEvents(): List<BackendScheduleEvent> {
    return rawQuery(
        """
        SELECT id, title, start_at, end_at, status, location, notes, tags
        FROM events
        WHERE status = 'confirmed'
        ORDER BY start_at
        """.trimIndent(),
        emptyArray(),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    BackendScheduleEvent(
                        id = cursor.getString(0),
                        title = cursor.getString(1),
                        start = cursor.getString(2),
                        end = cursor.getString(3),
                        status = cursor.getString(4),
                        location = cursor.getString(5),
                        notes = cursor.getString(6),
                        tags = cursor.getString(7).splitTags(),
                    ),
                )
            }
        }
    }
}

private fun SQLiteDatabase.queryTodos(): List<BackendTodoItem> {
    return rawQuery(
        """
        SELECT id, title, due, status, priority, notes, tags
        FROM todos
        WHERE status IN ('active', 'done')
        ORDER BY due, title
        """.trimIndent(),
        emptyArray(),
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    BackendTodoItem(
                        id = cursor.getString(0),
                        title = cursor.getString(1),
                        due = cursor.getString(2),
                        status = cursor.getString(3),
                        priority = cursor.getString(4),
                        notes = cursor.getString(5),
                        tags = cursor.getString(6).splitTags(),
                    ),
                )
            }
        }
    }
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
