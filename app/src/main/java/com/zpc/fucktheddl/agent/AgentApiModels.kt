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
    Delete,
    Update,
    Query,
    Suggestion,
    Clarify,
}

data class AgentProposal(
    val id: String,
    val commitmentType: CommitmentType,
    val title: String,
    val summary: String,
    val impact: String,
    val requiresConfirmation: Boolean,
    val schedulePatch: AgentSchedulePatch? = null,
    val todoPatch: AgentTodoPatch? = null,
    val candidates: List<AgentProposalCandidate> = emptyList(),
)

data class AgentSchedulePatch(
    val title: String,
    val start: String,
    val end: String,
    val timezone: String,
    val location: String,
    val notes: String,
    val tags: List<String>,
)

data class AgentTodoPatch(
    val title: String,
    val due: String,
    val timezone: String,
    val priority: String,
    val notes: String,
    val tags: List<String>,
)

data class AgentProposalCandidate(
    val id: String,
    val targetType: String,
    val title: String,
    val whenLabel: String,
    val detail: String,
    val resolutionText: String,
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

data class AgentCommitmentsPayload(
    val events: List<BackendScheduleEvent>,
    val todos: List<BackendTodoItem>,
)

data class BackendScheduleEvent(
    val id: String,
    val title: String,
    val start: String,
    val end: String,
    val status: String,
    val location: String,
    val notes: String,
    val tags: List<String>,
)

data class BackendTodoItem(
    val id: String,
    val title: String,
    val due: String,
    val status: String,
    val priority: String,
    val notes: String,
    val tags: List<String>,
)
