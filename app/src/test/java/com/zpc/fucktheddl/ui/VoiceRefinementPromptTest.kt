package com.zpc.fucktheddl.ui

import com.zpc.fucktheddl.agent.AgentProposal
import com.zpc.fucktheddl.agent.AgentSchedulePatch
import com.zpc.fucktheddl.agent.CommitmentType
import com.zpc.fucktheddl.agent.AgentCommitmentsPayload
import com.zpc.fucktheddl.agent.LocalAgentEngine
import java.time.LocalDate
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class VoiceRefinementPromptTest {
    @Test
    fun refinementPromptCarriesOriginalUnderstandingAndCorrection() {
        val proposal = AgentProposal(
            id = "proposal-1",
            commitmentType = CommitmentType.Schedule,
            title = "项目会",
            summary = "准备创建日程：项目会，明天 15:00-16:00。",
            impact = "确认后会作为日程加入日历。",
            requiresConfirmation = true,
            schedulePatch = AgentSchedulePatch(
                title = "项目会",
                start = "2026-05-03T15:00:00+08:00",
                end = "2026-05-03T16:00:00+08:00",
                timezone = "Asia/Shanghai",
                location = "",
                notes = "带电脑",
                tags = emptyList(),
            ),
        )

        val prompt = buildVoiceRefinementPrompt(
            originalText = "明天下午三点项目会，带电脑",
            proposal = proposal,
            correctionText = "不是明天，是后天",
        )

        assertTrue(prompt.contains("明天下午三点项目会，带电脑"))
        assertTrue(prompt.contains("当前理解"))
        assertTrue(prompt.contains("项目会"))
        assertTrue(prompt.contains("2026-05-03T15:00:00+08:00"))
        assertTrue(prompt.contains("用户修正"))
        assertTrue(prompt.contains("不是明天，是后天"))
    }

    @Test
    fun refinementDraftPrefersModelDateOverOldContextDates() {
        val proposal = AgentProposal(
            id = "proposal-1",
            commitmentType = CommitmentType.Schedule,
            title = "项目会",
            summary = "准备创建日程：项目会，明天 15:00-16:00。",
            impact = "确认后会作为日程加入日历。",
            requiresConfirmation = true,
            schedulePatch = AgentSchedulePatch(
                title = "项目会",
                start = "2026-05-03T15:00:00+08:00",
                end = "2026-05-03T16:00:00+08:00",
                timezone = "Asia/Shanghai",
                location = "",
                notes = "带电脑",
                tags = emptyList(),
            ),
        )
        val prompt = buildVoiceRefinementPrompt(
            originalText = "明天下午三点项目会，带电脑",
            proposal = proposal,
            correctionText = "不是明天，是后天",
        )

        val refined = LocalAgentEngine(
            todayProvider = { LocalDate.of(2026, 5, 2) },
        ).draftProposal(
            text = prompt,
            sessionId = "refine-date-test",
            modelExtraction = JSONObject(
                """
                    {
                      "commitment_type": "schedule",
                      "title": "项目会",
                      "date": "后天",
                      "time": "下午三点",
                      "notes": "带电脑"
                    }
                """.trimIndent(),
            ),
            commitments = AgentCommitmentsPayload(emptyList(), emptyList()),
        )

        assertEquals("2026-05-04T15:00:00+08:00", refined.schedulePatch?.start)
    }

    @Test
    fun newVoiceConversationSessionIdsCreateTurnBoundaries() {
        val first = newVoiceConversationSessionId()
        val second = newVoiceConversationSessionId()

        assertTrue(first.startsWith("voice-"))
        assertTrue(second.startsWith("voice-"))
        assertNotEquals(first, second)
    }
}
