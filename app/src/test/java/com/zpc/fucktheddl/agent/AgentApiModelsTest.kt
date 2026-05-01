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
    fun connectionSettingsBuildRuntimeApiConfig() {
        val settings = AgentConnectionSettings(
            baseUrl = " http://server.example:8001 ",
            accessToken = " token-1 ",
        )

        val config = settings.toConfig()

        assertEquals("http://server.example:8001/", config.normalizedBaseUrl)
        assertEquals("token-1", config.accessToken)
    }

    @Test
    fun deepseekSettingsDoNotChangeBackendConnectionConfig() {
        val base = AgentConnectionSettings(
            baseUrl = "http://server.example:8001",
            accessToken = "token-1",
            deepseekApiKey = "",
            deepseekBaseUrl = "https://api.deepseek.com/v1",
            deepseekModel = "deepseek-v4-flash",
        )
        val withUserModel = base.copy(
            deepseekApiKey = "user-model-key",
            deepseekBaseUrl = "https://custom-model.example/v1",
            deepseekModel = "custom-model",
        )

        assertEquals(base.toConfig(), withUserModel.toConfig())
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

    @Test
    fun queryProposalIsResultOnlyAndNeverConfirmable() {
        val proposal = AgentProposal(
            id = "proposal-query",
            commitmentType = CommitmentType.Query,
            title = "明天安排",
            summary = "15:00 去电玩城",
            impact = "这只是查询结果，不会写入任何内容。",
            requiresConfirmation = false,
        )

        assertEquals(ProposalPresentation.ResultOnly, proposal.presentation())
    }

    @Test
    fun fuzzyDeleteProposalShowsCandidateChoice() {
        val proposal = AgentProposal(
            id = "proposal-delete",
            commitmentType = CommitmentType.Delete,
            title = "选择要取消的项目",
            summary = "我找到几个可能要取消的项目，请选一个继续。",
            impact = "点选候选后会生成删除确认，不会直接删除。",
            requiresConfirmation = false,
            candidates = listOf(
                AgentProposalCandidate(
                    id = "evt-1",
                    targetType = "schedule",
                    title = "去电玩城",
                    whenLabel = "今天 15:00",
                    detail = "日程",
                    resolutionText = "今天取消某项活动 #evt-1",
                ),
            ),
        )

        assertEquals(ProposalPresentation.CandidateChoice, proposal.presentation())
    }

    @Test
    fun updateProposalCanBeConfirmedWithoutEdit() {
        val proposal = AgentProposal(
            id = "proposal-update",
            commitmentType = CommitmentType.Update,
            title = "午睡",
            summary = "准备修改日程：午睡，调整为明天 14:00-15:00。",
            impact = "确认后会更新原日程。",
            requiresConfirmation = true,
            schedulePatch = AgentSchedulePatch(
                title = "午睡",
                start = "2026-05-01T14:00:00+08:00",
                end = "2026-05-01T15:00:00+08:00",
                timezone = "Asia/Shanghai",
                location = "",
                notes = "",
                tags = emptyList(),
            ),
        )

        assertEquals(ProposalPresentation.Confirmable, proposal.presentation())
        assertEquals(false, shouldEditProposalBeforeConfirm(proposal, edited = false))
        assertEquals(true, shouldEditProposalBeforeConfirm(proposal, edited = true))
    }
}
