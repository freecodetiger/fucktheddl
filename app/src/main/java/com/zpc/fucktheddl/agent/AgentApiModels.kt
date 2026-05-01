package com.zpc.fucktheddl.agent

data class AgentApiConfig(
    val baseUrl: String,
    val accessToken: String = "",
) {
    val normalizedBaseUrl: String =
        if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
}

data class AgentConnectionSettings(
    val baseUrl: String,
    val accessToken: String = "",
    val userEmail: String = "",
    val deepseekApiKey: String = "",
    val deepseekBaseUrl: String = "https://api.deepseek.com/v1",
    val deepseekModel: String = "deepseek-v4-flash",
) {
    fun toConfig(): AgentApiConfig = AgentApiConfig(
        baseUrl = baseUrl.trim(),
        accessToken = accessToken.trim(),
    )
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
    val deletePatch: AgentDeletePatch? = null,
    val updatePatch: AgentUpdatePatch? = null,
    val candidates: List<AgentProposalCandidate> = emptyList(),
)

enum class ProposalPresentation {
    CandidateChoice,
    Confirmable,
    ResultOnly,
}

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

data class AgentDeletePatch(
    val targetId: String,
    val targetType: String,
    val targetTitle: String,
)

data class AgentUpdatePatch(
    val targetId: String,
    val targetType: String,
    val targetTitle: String,
    val schedulePatch: AgentSchedulePatch? = null,
    val todoPatch: AgentTodoPatch? = null,
)

data class AgentProposalCandidate(
    val id: String,
    val targetType: String,
    val title: String,
    val whenLabel: String,
    val detail: String,
    val resolutionText: String,
    val actionLabel: String = "选择",
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

data class AgentConnectionTestResult(
    val healthy: Boolean,
    val label: String,
    val detail: String = "",
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

fun AgentProposal.presentation(): ProposalPresentation {
    return when {
        candidates.isNotEmpty() -> ProposalPresentation.CandidateChoice
        requiresConfirmation -> ProposalPresentation.Confirmable
        else -> ProposalPresentation.ResultOnly
    }
}
