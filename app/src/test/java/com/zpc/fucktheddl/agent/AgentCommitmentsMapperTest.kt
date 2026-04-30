package com.zpc.fucktheddl.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentCommitmentsMapperTest {
    @Test
    fun mapsConfirmedBackendScheduleIntoUiEvent() {
        val response = AgentCommitmentsPayload(
            events = listOf(
                BackendScheduleEvent(
                    id = "evt_20260430_150000_arcade",
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
        assertEquals("evt_20260430_150000_arcade", state.events.first().id)
        assertEquals("2026-04-30", state.events.first().date)
        assertEquals("15:00 - 16:00", state.events.first().timeRange)
        assertEquals("已创建日程", state.events.first().detail)
        assertEquals("日程", state.events.first().tag)
        assertEquals(0, state.todos.size)
    }
}
