package com.zpc.fucktheddl.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentCommitmentsMapperTest {
    @Test
    fun mapsConfirmedBackendScheduleIntoUiEvent() {
        val response = AgentCommitmentsPayload(
            events = listOf(
                BackendScheduleEvent(
                    title = "去电玩城",
                    start = "2026-04-30T15:00:00+08:00",
                    end = "2026-04-30T16:00:00+08:00",
                    status = "confirmed",
                    location = "",
                    notes = "",
                    tags = emptyList(),
                ),
            ),
            todos = emptyList(),
        )

        val state = mapCommitmentsToScheduleState(response)

        assertEquals("去电玩城", state.events.first().title)
        assertEquals("15:00 - 16:00", state.events.first().timeRange)
        assertEquals("智能体创建的日程", state.events.first().detail)
        assertEquals("智能体", state.events.first().tag)
        assertEquals(0, state.todos.size)
    }
}
