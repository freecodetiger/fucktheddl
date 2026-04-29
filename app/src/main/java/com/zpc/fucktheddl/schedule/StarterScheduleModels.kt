package com.zpc.fucktheddl.schedule

data class ScheduleShellState(
    val tabs: List<ScheduleTab>,
    val selectedTab: ScheduleTab,
    val events: List<ScheduleEvent>,
    val todos: List<TodoItem>,
    val openSlots: List<OpenSlot>,
    val agentState: AgentState,
    val syncState: SyncState,
)

data class ScheduleTab(
    val label: String,
)

data class ScheduleEvent(
    val timeRange: String,
    val title: String,
    val detail: String,
    val tag: String,
    val risk: ScheduleRisk,
)

data class TodoItem(
    val title: String,
    val dueLabel: String,
    val detail: String,
    val tag: String,
    val priority: TodoPriority,
    val done: Boolean,
)

data class OpenSlot(
    val timeRange: String,
    val suggestion: String,
)

data class AgentState(
    val status: String,
    val activeChain: List<AgentStep>,
    val pendingProposal: AgentProposal?,
    val writePolicy: String,
)

data class AgentStep(
    val label: String,
    val state: AgentStepState,
)

data class AgentProposal(
    val title: String,
    val summary: String,
    val impact: String,
)

data class SyncState(
    val kind: String,
    val label: String,
)

enum class AgentStepState {
    Done,
    Active,
    Waiting,
}

enum class ScheduleRisk {
    Normal,
    Deadline,
    Focus,
}

enum class TodoPriority {
    Low,
    Medium,
    High,
}
