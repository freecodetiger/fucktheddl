package com.zpc.fucktheddl.agent

data class AgentApiConfig(
    val baseUrl: String,
) {
    val normalizedBaseUrl: String =
        if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
}

enum class CommitmentType {
    Schedule,
    Todo,
    Clarify,
}

data class AgentProposal(
    val id: String,
    val commitmentType: CommitmentType,
    val title: String,
    val summary: String,
    val impact: String,
    val requiresConfirmation: Boolean,
)

data class AgentSubmitResult(
    val proposal: AgentProposal?,
    val error: String?,
)

data class AgentApplyResult(
    val status: String,
    val commitmentId: String,
    val error: String?,
)
