package com.zpc.fucktheddl.ui

import com.zpc.fucktheddl.schedule.ScheduleEvent
import com.zpc.fucktheddl.schedule.ScheduleRisk
import com.zpc.fucktheddl.schedule.TodoItem
import com.zpc.fucktheddl.schedule.TodoPriority
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatisticsDisplayModelTest {
    @Test
    fun countsOnlyCompletedSchedulesAndTodos() {
        val today = LocalDate.parse("2026-05-02")
        val model = buildStatisticsDisplayModel(
            events = listOf(
                schedule(date = "2026-05-01"),
                schedule(date = "2026-05-02"),
                schedule(date = "2026-05-03"),
            ),
            todos = listOf(
                todo(dueDate = "2026-05-01", done = true),
                todo(dueDate = "2026-05-02", done = true),
                todo(dueDate = "2026-05-01", done = false),
            ),
            today = today,
        )

        assertEquals(1, model.completedSchedules)
        assertEquals(2, model.completedTodos)
    }

    @Test
    fun startsFromEarliestCompletedDate() {
        val model = buildStatisticsDisplayModel(
            events = listOf(schedule(date = "2026-04-28")),
            todos = listOf(todo(dueDate = "2026-04-20", done = true)),
            today = LocalDate.parse("2026-05-02"),
        )

        assertEquals("2026.04.20", model.startedAtLabel)
    }

    @Test
    fun counterFontSizeKeepsFourDigitCountsReadable() {
        assertEquals(36, statisticsCounterFontSize(999))
        assertEquals(32, statisticsCounterFontSize(1000))
        assertEquals(28, statisticsCounterFontSize(10000))
    }

    private fun schedule(date: String): ScheduleEvent {
        return ScheduleEvent(
            id = date,
            date = date,
            timeRange = "10:00-11:00",
            title = "日程",
            detail = "",
            tag = "日程",
            risk = ScheduleRisk.Normal,
        )
    }

    private fun todo(dueDate: String, done: Boolean): TodoItem {
        return TodoItem(
            id = dueDate,
            dueDate = dueDate,
            title = "待办",
            dueLabel = dueDate,
            detail = "",
            tag = "待办",
            priority = TodoPriority.ImportantNotUrgent,
            done = done,
        )
    }
}
