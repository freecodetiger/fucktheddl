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
                timeRange = "${event.start.toTimeLabel()} - ${event.end.toTimeLabel()}",
                title = event.title,
                detail = event.notes.ifBlank {
                    event.location.ifBlank { "智能体创建的日程" }
                },
                tag = event.tags.firstOrNull().orEmpty().ifBlank { "智能体" },
                risk = ScheduleRisk.Normal,
            )
        },
        todos = payload.todos.map { todo ->
            TodoItem(
                title = todo.title,
                dueLabel = todo.due.toDueLabel(),
                detail = todo.notes.ifBlank { "智能体创建的待办" },
                tag = todo.tags.firstOrNull().orEmpty().ifBlank { "智能体" },
                priority = todo.priority.toTodoPriority(),
                done = todo.status == "done",
            )
        },
    )
}

private fun String.toTimeLabel(): String {
    return if (length >= 16 && this[10] == 'T') substring(11, 16) else this
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
