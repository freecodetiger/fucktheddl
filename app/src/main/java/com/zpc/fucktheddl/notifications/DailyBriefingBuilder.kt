package com.zpc.fucktheddl.notifications

import com.zpc.fucktheddl.agent.AgentCommitmentsPayload
import com.zpc.fucktheddl.agent.BackendScheduleEvent
import com.zpc.fucktheddl.agent.BackendTodoItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DailyBriefing(
    val title: String,
    val body: String,
    val itemCount: Int,
)

class DailyBriefingBuilder(
    private val zoneId: ZoneId = ZoneId.of("Asia/Shanghai"),
) {
    fun build(
        commitments: AgentCommitmentsPayload,
        now: ZonedDateTime = ZonedDateTime.now(zoneId),
    ): DailyBriefing {
        val today = now.toLocalDate()
        val events = commitments.events
            .asSequence()
            .filter { it.status == "confirmed" }
            .mapNotNull { event -> event.toBriefingEvent(now, today) }
            .sortedBy { it.start }
            .toList()

        val todos = commitments.todos
            .asSequence()
            .filter { it.status == "active" }
            .mapNotNull { todo -> todo.toBriefingTodo(today) }
            .sortedWith(compareBy<BriefingTodo> { it.due }.thenBy { it.label })
            .toList()

        val sections = buildList {
            if (events.isNotEmpty()) add("日程 " + events.take(MaxItemsPerSection).joinToString("；") { it.label })
            if (todos.isNotEmpty()) add("待办 " + todos.take(MaxItemsPerSection).joinToString("；") { it.label })
        }
        val totalCount = events.size + todos.size
        return if (sections.isEmpty()) {
            DailyBriefing(
                title = "今天没有即将截止的事项",
                body = "当前没有需要提醒的日程或待办。",
                itemCount = 0,
            )
        } else {
            DailyBriefing(
                title = "今天还有 $totalCount 项需要处理",
                body = sections.joinToString("\n"),
                itemCount = totalCount,
            )
        }
    }

    private fun BackendScheduleEvent.toBriefingEvent(
        now: ZonedDateTime,
        today: LocalDate,
    ): BriefingEvent? {
        val startAt = parseDateTime(start) ?: return null
        val endAt = parseDateTime(end) ?: startAt
        if (startAt.toLocalDate() != today) return null
        if (endAt.isBefore(now)) return null
        val timeLabel = startAt.toLocalTime().format(TimeFormatter)
        return BriefingEvent(start = startAt, label = "$timeLabel $title")
    }

    private fun BackendTodoItem.toBriefingTodo(today: LocalDate): BriefingTodo? {
        val dueDate = parseDate(due) ?: return null
        if (dueDate != today) return null
        return BriefingTodo(due = dueDate, label = title)
    }

    private fun parseDateTime(value: String): ZonedDateTime? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        return runCatching {
            OffsetDateTime.parse(trimmed).atZoneSameInstant(zoneId)
        }.getOrElse {
            runCatching {
                LocalDateTime.parse(trimmed).atZone(zoneId)
            }.getOrElse {
                parseDate(trimmed)?.atTime(LocalTime.MIN)?.atZone(zoneId)
            }
        }
    }

    private fun parseDate(value: String): LocalDate? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        return runCatching {
            LocalDate.parse(trimmed)
        }.getOrElse {
            runCatching {
                OffsetDateTime.parse(trimmed).atZoneSameInstant(zoneId).toLocalDate()
            }.getOrElse {
                runCatching {
                    LocalDateTime.parse(trimmed).toLocalDate()
                }.getOrNull()
            }
        }
    }

    private data class BriefingEvent(
        val start: ZonedDateTime,
        val label: String,
    )

    private data class BriefingTodo(
        val due: LocalDate,
        val label: String,
    )

    private companion object {
        const val MaxItemsPerSection = 4
        val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
    }
}
