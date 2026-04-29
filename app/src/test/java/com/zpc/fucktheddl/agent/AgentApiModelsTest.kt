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
}

