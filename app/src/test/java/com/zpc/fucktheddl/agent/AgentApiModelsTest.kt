package com.zpc.fucktheddl.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentApiModelsTest {
    @Test
    fun debugBaseUrlAlwaysHasTrailingSlash() {
        val config = AgentApiConfig(baseUrl = "http://192.168.1.10:8000")

        assertEquals("http://192.168.1.10:8000/", config.normalizedBaseUrl)
    }

    @Test
    fun scheduleProposalPayloadKeepsConfirmationBoundary() {
        val proposal = AgentProposal(
            id = "proposal-1",
            commitmentType = CommitmentType.Schedule,
            title = "项目复盘会",
            summary = "Create a calendar event.",
            impact = "Occupies a time block.",
            requiresConfirmation = true,
        )

        assertTrue(proposal.requiresConfirmation)
        assertEquals(CommitmentType.Schedule, proposal.commitmentType)
    }

    @Test
    fun unchangedScheduleProposalConfirmsWithoutEditRequest() {
        val proposal = AgentProposal(
            id = "proposal-1",
            commitmentType = CommitmentType.Schedule,
            title = "金山云面试",
            summary = "准备创建日程",
            impact = "确认后写入",
            requiresConfirmation = true,
            schedulePatch = AgentSchedulePatch(
                title = "金山云面试",
                start = "2026-05-06T10:30:00+08:00",
                end = "2026-05-06T11:30:00+08:00",
                timezone = "Asia/Shanghai",
                location = "",
                notes = "带简历",
                tags = listOf("面试"),
            ),
        )

        assertEquals(false, shouldEditProposalBeforeConfirm(proposal, edited = false))
        assertEquals(true, shouldEditProposalBeforeConfirm(proposal, edited = true))
    }
}
