package com.zpc.fucktheddl.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarterScheduleRepositoryTest {
    private val repository = StarterScheduleRepository()

    @Test
    fun initialStateHasUsefulTodaySurfaceData() {
        val state = repository.loadInitialState()

        assertEquals("Today", state.selectedTab.label)
        assertTrue(state.events.isNotEmpty())
        assertTrue(state.todos.isNotEmpty())
        assertTrue(state.openSlots.isNotEmpty())
        assertEquals("clean", state.syncState.kind)
        assertEquals("proposal_required", state.agentState.writePolicy)
    }

    @Test
    fun navigationExposesScheduleTodoAndAgentDestinations() {
        val labels = repository.loadInitialState().tabs.map { it.label }

        assertEquals(listOf("Today", "Calendar", "Todo", "Agent"), labels)
    }

    @Test
    fun todosRepresentDeadlineBoundWorkNotTimedAttendance() {
        val state = repository.loadInitialState()

        assertTrue(state.todos.all { it.dueLabel.isNotBlank() })
        assertTrue(state.todos.none { it.dueLabel.contains(":") })
        assertTrue(state.events.all { it.timeRange.contains(":") })
    }
}
