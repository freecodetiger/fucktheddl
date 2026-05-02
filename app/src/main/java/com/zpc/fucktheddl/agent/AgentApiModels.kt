package com.zpc.fucktheddl.agent

data class AgentConnectionSettings(
    val deepseekApiKey: String = "",
    val deepseekBaseUrl: String = "https://api.deepseek.com/v1",
    val deepseekModel: String = "deepseek-v4-flash",
    val aliyunApiKey: String = "",
    val aliyunAsrUrl: String = DEFAULT_ALIYUN_ASR_URL,
)

const val DEFAULT_ALIYUN_ASR_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/inference"

interface AgentClient {
    fun testService(settings: AgentConnectionSettings): AgentConnectionTestResult

    fun propose(
        text: String,
        sessionId: String = "android-${java.util.UUID.randomUUID()}",
        commitments: AgentCommitmentsPayload? = null,
        settings: AgentConnectionSettings? = null,
    ): AgentSubmitResult
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
    val done: Boolean? = null,
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

internal fun shouldEditProposalBeforeConfirm(
    proposal: AgentProposal,
    edited: Boolean,
): Boolean {
    return edited && (proposal.schedulePatch != null || proposal.todoPatch != null)
}
