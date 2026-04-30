package com.zpc.fucktheddl.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterScheduleRepositoryTest {
    private val repository = StarterScheduleRepository()

    @Test
    fun initialStateHasUsefulTodaySurfaceData() {
        val state = repository.loadInitialState()

        assertEquals("今天", state.selectedTab.label)
        assertTrue(state.events.isNotEmpty())
        assertTrue(state.todos.isNotEmpty())
        assertTrue(state.openSlots.isNotEmpty())
        assertEquals("clean", state.syncState.kind)
        assertEquals("proposal_required", state.agentState.writePolicy)
    }

    @Test
    fun navigationExposesVoiceFirstCoreDestinations() {
        val labels = repository.loadInitialState().tabs.map { it.label }

        assertEquals(listOf("今天", "待办"), labels)
    }

    @Test
    fun todosRepresentDeadlineBoundWorkNotTimedAttendance() {
        val state = repository.loadInitialState()

        assertTrue(state.todos.all { it.dueLabel.isNotBlank() })
        assertTrue(state.todos.none { it.dueLabel.contains(":") })
        assertTrue(state.events.all { it.timeRange.contains(":") })
    }

    @Test
    fun starterCopyIsLocalizedForChineseUi() {
        val state = repository.loadInitialState()

        assertEquals("Git 已同步", state.syncState.label)
        assertEquals("正在生成待确认方案", state.agentState.status)
        assertEquals("复盘 DDL 队列", state.events.first().title)
        assertEquals("今天截止", state.todos.first().dueLabel)
    }
}
