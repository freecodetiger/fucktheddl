package com.zpc.fucktheddl.notifications

import com.zpc.fucktheddl.agent.AgentCommitmentsPayload
import com.zpc.fucktheddl.agent.BackendScheduleEvent
import com.zpc.fucktheddl.agent.BackendTodoItem
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyBriefingBuilderTest {
    private val builder = DailyBriefingBuilder(ZoneId.of("Asia/Shanghai"))
    private val now = ZonedDateTime.parse("2026-05-02T08:30:00+08:00[Asia/Shanghai]")

    @Test
    fun buildIncludesOnlyTodayActiveAndFutureItems() {
        val briefing = builder.build(
            commitments = AgentCommitmentsPayload(
                events = listOf(
                    event("past-event", "早会", "2026-05-02T07:00:00+08:00", "2026-05-02T08:00:00+08:00"),
                    event("today-event", "项目评审", "2026-05-02T10:00:00+08:00", "2026-05-02T11:00:00+08:00"),
                    event("tomorrow-event", "明天会议", "2026-05-03T10:00:00+08:00", "2026-05-03T11:00:00+08:00"),
                ),
                todos = listOf(
                    todo("overdue-todo", "昨天截止", "2026-05-01"),
                    todo("today-todo", "提交作业", "2026-05-02"),
                    todo("done-todo", "已完成事项", "2026-05-02", status = "done"),
                ),
            ),
            now = now,
        )

        assertEquals("今天还有 2 项需要处理", briefing.title)
        assertTrue(briefing.body.contains("10:00 项目评审"))
        assertTrue(briefing.body.contains("待办 提交作业"))
        assertFalse(briefing.body.contains("早会"))
        assertFalse(briefing.body.contains("明天会议"))
        assertFalse(briefing.body.contains("昨天截止"))
        assertFalse(briefing.body.contains("已完成事项"))
    }

    @Test
    fun buildOmitsEmptySectionLabels() {
        val briefing = builder.build(
            commitments = AgentCommitmentsPayload(
                events = emptyList(),
                todos = listOf(todo("today-todo", "提交作业", "2026-05-02")),
            ),
            now = now,
        )

        assertEquals("今天还有 1 项需要处理", briefing.title)
        assertFalse(briefing.body.contains("日程"))
        assertTrue(briefing.body.startsWith("待办 "))
    }

    @Test
    fun buildReturnsEmptyBriefingWhenOnlyOverdueItemsExist() {
        val briefing = builder.build(
            commitments = AgentCommitmentsPayload(
                events = listOf(event("past-event", "早会", "2026-05-02T07:00:00+08:00", "2026-05-02T08:00:00+08:00")),
                todos = listOf(todo("overdue-todo", "昨天截止", "2026-05-01")),
            ),
            now = now,
        )

        assertEquals("今天没有即将截止的事项", briefing.title)
        assertEquals(0, briefing.itemCount)
    }

    private fun event(
        id: String,
        title: String,
        start: String,
        end: String,
        status: String = "confirmed",
    ) = BackendScheduleEvent(
        id = id,
        title = title,
        start = start,
        end = end,
        status = status,
        location = "",
        notes = "",
        tags = emptyList(),
    )

    private fun todo(
        id: String,
        title: String,
        due: String,
        status: String = "active",
    ) = BackendTodoItem(
        id = id,
        title = title,
        due = due,
        status = status,
        priority = "medium",
        notes = "",
        tags = emptyList(),
    )
}
