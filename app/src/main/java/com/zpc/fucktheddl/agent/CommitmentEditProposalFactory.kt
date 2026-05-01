package com.zpc.fucktheddl.agent

import com.zpc.fucktheddl.schedule.ScheduleEvent
import com.zpc.fucktheddl.schedule.TodoItem
import com.zpc.fucktheddl.schedule.TodoPriority
import java.time.LocalDate
import java.util.Locale

fun createScheduleProposal(
    title: String,
    date: String,
    startTime: String,
    endTime: String,
    notes: String,
): AgentProposal {
    val normalizedTitle = title.trim().ifBlank { "新日程" }
    val normalizedDate = date.trim().ifBlank { LocalDate.now().toString() }
    val normalizedStart = startTime.normalizedClock("09:00")
    val normalizedEnd = endTime.normalizedClock("10:00")
    val patch = AgentSchedulePatch(
        title = normalizedTitle,
        start = "${normalizedDate}T${normalizedStart}:00+08:00",
        end = "${normalizedDate}T${normalizedEnd}:00+08:00",
        timezone = "Asia/Shanghai",
        location = "",
        notes = notes.trim(),
        tags = emptyList(),
    )
    return AgentProposal(
        id = "local_create_schedule_${normalizedDate}_${normalizedStart}_${normalizedTitle.hashCode()}",
        commitmentType = CommitmentType.Schedule,
        title = normalizedTitle,
        summary = "$normalizedTitle $normalizedDate $normalizedStart-$normalizedEnd",
        impact = "创建日程",
        requiresConfirmation = true,
        schedulePatch = patch,
    )
}

fun createTodoProposal(
    title: String,
    due: String,
    notes: String,
    priority: String,
): AgentProposal {
    val normalizedTitle = title.trim().ifBlank { "新待办" }
    val normalizedDue = due.trim()
    val normalizedPriority = priority.normalizedPriority(fallback = "q2")
    val patch = AgentTodoPatch(
        title = normalizedTitle,
        due = normalizedDue,
        timezone = "Asia/Shanghai",
        priority = normalizedPriority,
        notes = notes.trim(),
        tags = emptyList(),
    )
    return AgentProposal(
        id = "local_create_todo_${normalizedDue}_${normalizedTitle.hashCode()}",
        commitmentType = CommitmentType.Todo,
        title = normalizedTitle,
        summary = if (normalizedDue.isBlank()) normalizedTitle else "$normalizedTitle $normalizedDue",
        impact = "创建待办",
        requiresConfirmation = true,
        todoPatch = patch,
    )
}

fun ScheduleEvent.toScheduleUpdateProposal(
    title: String,
    date: String,
    startTime: String,
    endTime: String,
    notes: String,
): AgentProposal {
    val normalizedTitle = title.trim().ifBlank { this.title }
    val normalizedDate = date.trim().ifBlank { this.date.ifBlank { LocalDate.now().toString() } }
    val normalizedStart = startTime.normalizedClock(timeRange.substringBefore(" - ").ifBlank { "09:00" })
    val normalizedEnd = endTime.normalizedClock(timeRange.substringAfter(" - ", "").ifBlank { "10:00" })
    val patch = AgentSchedulePatch(
        title = normalizedTitle,
        start = "${normalizedDate}T${normalizedStart}:00+08:00",
        end = "${normalizedDate}T${normalizedEnd}:00+08:00",
        timezone = "Asia/Shanghai",
        location = "",
        notes = notes.trim(),
        tags = tag.preservedTags(defaultTag = "日程"),
    )
    return AgentProposal(
        id = "local_update_${id.ifBlank { normalizedTitle.hashCode().toString() }}",
        commitmentType = CommitmentType.Update,
        title = normalizedTitle,
        summary = "$normalizedTitle $normalizedDate $normalizedStart-$normalizedEnd",
        impact = "更新日程",
        requiresConfirmation = true,
        schedulePatch = patch,
        updatePatch = AgentUpdatePatch(
            targetId = id,
            targetType = "schedule",
            targetTitle = this.title,
            schedulePatch = patch,
        ),
    )
}

fun TodoItem.toTodoUpdateProposal(
    title: String,
    due: String,
    notes: String,
    priority: String,
): AgentProposal {
    val normalizedTitle = title.trim().ifBlank { this.title }
    val normalizedDue = due.trim().ifBlank { dueDate }
    val normalizedPriority = priority.normalizedPriority(fallback = this.priority.toBackendPriority())
    val patch = AgentTodoPatch(
        title = normalizedTitle,
        due = normalizedDue,
        timezone = "Asia/Shanghai",
        priority = normalizedPriority,
        notes = notes.trim(),
        tags = tag.preservedTags(defaultTag = "待办"),
    )
    return AgentProposal(
        id = "local_update_${id.ifBlank { normalizedTitle.hashCode().toString() }}",
        commitmentType = CommitmentType.Update,
        title = normalizedTitle,
        summary = "$normalizedTitle $normalizedDue",
        impact = "更新待办",
        requiresConfirmation = true,
        todoPatch = patch,
        updatePatch = AgentUpdatePatch(
            targetId = id,
            targetType = "todo",
            targetTitle = this.title,
            todoPatch = patch,
        ),
    )
}

private fun String.normalizedClock(fallback: String): String {
    val match = Regex("""^(\d{1,2})(?::(\d{1,2}))?(?::\d{1,2})?$""").matchEntire(trim())
        ?: return fallback.take(5).ifBlank { "09:00" }
    val hour = match.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: return fallback.take(5)
    val minute = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return "%02d:%02d".format(hour, minute)
}

private fun String.normalizedPriority(fallback: String): String {
    return when (trim().lowercase(Locale.ROOT)) {
        "high", "q1", "高", "高优先级", "重要且紧急" -> "q1"
        "medium", "q2", "中", "中优先级", "重要不紧急" -> "q2"
        "low", "q3", "低", "低优先级", "紧急不重要" -> "q3"
        "q4", "不重要不紧急" -> "q4"
        else -> fallback
    }
}

private fun TodoPriority.toBackendPriority(): String {
    return when (this) {
        TodoPriority.UrgentImportant -> "q1"
        TodoPriority.ImportantNotUrgent -> "q2"
        TodoPriority.UrgentNotImportant -> "q3"
        TodoPriority.NotUrgentNotImportant -> "q4"
    }
}

private fun String.preservedTags(defaultTag: String): List<String> {
    val trimmed = trim()
    return if (trimmed.isBlank() || trimmed == defaultTag) emptyList() else listOf(trimmed)
}
