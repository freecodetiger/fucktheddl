package com.zpc.fucktheddl.agent

import com.zpc.fucktheddl.schedule.ScheduleEvent
import com.zpc.fucktheddl.schedule.ScheduleRisk
import com.zpc.fucktheddl.schedule.TodoItem
import com.zpc.fucktheddl.schedule.TodoPriority

data class AgentCommitmentsUiState(
    val events: List<ScheduleEvent>,
    val todos: List<TodoItem>,
)

fun mapCommitmentsToScheduleState(payload: AgentCommitmentsPayload): AgentCommitmentsUiState {
    return AgentCommitmentsUiState(
        events = payload.events.map { event ->
            ScheduleEvent(
                id = event.id,
                date = event.start.toDateLabel(),
                timeRange = "${event.start.toTimeLabel()} - ${event.end.toTimeLabel()}",
                title = event.title,
                detail = event.notes,
                tag = event.tags.firstOrNull().orEmpty().ifBlank { "日程" },
                risk = ScheduleRisk.Normal,
            )
        },
        todos = payload.todos.map { todo ->
            TodoItem(
                id = todo.id,
                dueDate = todo.due,
                title = todo.title,
                dueLabel = todo.due.toDueLabel(),
                detail = todo.notes,
                tag = todo.tags.firstOrNull().orEmpty().ifBlank { "待办" },
                priority = todo.priority.toTodoPriority(),
                done = todo.status == "done",
            )
        },
    )
}

private fun String.toTimeLabel(): String {
    return if (length >= 16 && this[10] == 'T') substring(11, 16) else this
}

private fun String.toDateLabel(): String {
    return if (length >= 10) substring(0, 10) else ""
}

private fun String.toDueLabel(): String {
    return if (isBlank()) "未设截止" else "$this 截止"
}

private fun String.toTodoPriority(): TodoPriority {
    return when (this) {
        "high" -> TodoPriority.High
        "low" -> TodoPriority.Low
        else -> TodoPriority.Medium
    }
}
