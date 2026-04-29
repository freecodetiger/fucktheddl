package com.zpc.fucktheddl.schedule

class StarterScheduleRepository {
    fun loadInitialState(): ScheduleShellState {
        val tabs = listOf(
            ScheduleTab(label = "Today"),
            ScheduleTab(label = "Calendar"),
            ScheduleTab(label = "Todo"),
            ScheduleTab(label = "Agent"),
        )

        return ScheduleShellState(
            tabs = tabs,
            selectedTab = tabs.first(),
            events = listOf(
                ScheduleEvent(
                    timeRange = "09:30 - 10:15",
                    title = "Review DDL queue",
                    detail = "Sort urgent deadlines before agent planning.",
                    tag = "planning",
                    risk = ScheduleRisk.Deadline,
                ),
                ScheduleEvent(
                    timeRange = "14:00 - 15:30",
                    title = "Deep work block",
                    detail = "Protected focus time for the current project.",
                    tag = "focus",
                    risk = ScheduleRisk.Focus,
                ),
                ScheduleEvent(
                    timeRange = "20:00 - 20:20",
                    title = "Daily schedule closeout",
                    detail = "Confirm tomorrow's first task and reminders.",
                    tag = "routine",
                    risk = ScheduleRisk.Normal,
                ),
            ),
            todos = listOf(
                TodoItem(
                    title = "Finish Android agent shell",
                    dueLabel = "Due today",
                    detail = "Keep the first native loop buildable and installable.",
                    tag = "android",
                    priority = TodoPriority.High,
                    done = false,
                ),
                TodoItem(
                    title = "Define JSON patch contract",
                    dueLabel = "Due tomorrow",
                    detail = "Separate event patches from todo patches before backend writes.",
                    tag = "backend",
                    priority = TodoPriority.Medium,
                    done = false,
                ),
                TodoItem(
                    title = "Archive browser prototype notes",
                    dueLabel = "Due this week",
                    detail = "Move validated prototype decisions into native UI tasks.",
                    tag = "design",
                    priority = TodoPriority.Low,
                    done = true,
                ),
            ),
            openSlots = listOf(
                OpenSlot(
                    timeRange = "10:30 - 11:30",
                    suggestion = "Open slot for short admin tasks.",
                ),
                OpenSlot(
                    timeRange = "16:00 - 17:00",
                    suggestion = "Good window for an Agent-suggested reschedule.",
                ),
            ),
            agentState = AgentState(
                status = "Drafting a confirmation card",
                activeChain = listOf(
                    AgentStep(label = "Classify intent", state = AgentStepState.Done),
                    AgentStep(label = "Read schedule and todo facts", state = AgentStepState.Done),
                    AgentStep(label = "Validate conflicts", state = AgentStepState.Active),
                    AgentStep(label = "Wait for confirmation", state = AgentStepState.Waiting),
                    AgentStep(label = "Write JSON and Git commit", state = AgentStepState.Waiting),
                ),
                pendingProposal = AgentProposal(
                    title = "Split commitments by type",
                    summary = "Timed attendance stays on the calendar. Deadline work moves to Todo.",
                    impact = "Prevents a deadline from pretending to be a meeting.",
                ),
                writePolicy = "proposal_required",
            ),
            syncState = SyncState(
                kind = "clean",
                label = "Git clean",
            ),
        )
    }
}
