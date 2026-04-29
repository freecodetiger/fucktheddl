package com.zpc.fucktheddl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zpc.fucktheddl.schedule.AgentProposal
import com.zpc.fucktheddl.schedule.AgentState
import com.zpc.fucktheddl.schedule.AgentStepState
import com.zpc.fucktheddl.schedule.OpenSlot
import com.zpc.fucktheddl.schedule.ScheduleEvent
import com.zpc.fucktheddl.schedule.ScheduleRisk
import com.zpc.fucktheddl.schedule.ScheduleShellState
import com.zpc.fucktheddl.schedule.ScheduleTab
import com.zpc.fucktheddl.schedule.TodoItem
import com.zpc.fucktheddl.schedule.TodoPriority

private val Ink = Color(0xFF141414)
private val Muted = Color(0xFF6B6B6B)
private val Accent = Color(0xFF2563EB)
private val Risk = Color(0xFFD97706)
private val Danger = Color(0xFFDC2626)
private val Success = Color(0xFF059669)
private val SurfaceSoft = Color(0xFFF2F2EF)

@Composable
fun FuckTheDdlApp(initialState: ScheduleShellState) {
    var selectedTab by remember { mutableStateOf(initialState.selectedTab) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AgentComposer(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Header(
                syncLabel = initialState.syncState.label,
                agentStatus = initialState.agentState.status,
            )
            TabSwitcher(
                tabs = initialState.tabs,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
            )

            when (selectedTab.label) {
                "Today" -> TodaySurface(
                    events = initialState.events,
                    todos = initialState.todos,
                    openSlots = initialState.openSlots,
                    agentState = initialState.agentState,
                )

                "Calendar" -> CalendarSurface(events = initialState.events)
                "Todo" -> TodoSurface(todos = initialState.todos)
                "Agent" -> AgentSurface(agentState = initialState.agentState)
            }
        }
    }
}

@Composable
private fun Header(
    syncLabel: String,
    agentStatus: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "fucktheddl",
                    color = Ink,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = "Schedules are attendance. Todos are obligations. The Agent keeps them honest.",
                    color = Muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
            StatusPill(label = syncLabel)
        }
        Surface(
            color = Color(0xFFEAF1FF),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text(
                text = agentStatus,
                color = Accent,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun StatusPill(label: String) {
    Surface(
        color = Color(0xFFEAF3EF),
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            color = Success,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TabSwitcher(
    tabs: List<ScheduleTab>,
    selectedTab: ScheduleTab,
    onTabSelected: (ScheduleTab) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        tabs.forEach { tab ->
            FilterChip(
                selected = tab == selectedTab,
                onClick = { onTabSelected(tab) },
                label = { Text(tab.label) },
                shape = RoundedCornerShape(6.dp),
            )
        }
    }
}

@Composable
private fun TodaySurface(
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
    openSlots: List<OpenSlot>,
    agentState: AgentState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SummaryStrip(events = events, todos = todos)
        SectionTitle("Next commitments")
        events.take(2).forEach { event -> EventCard(event = event) }
        SectionTitle("Deadline work")
        todos.filter { !it.done }.take(2).forEach { todo -> TodoCard(todo = todo) }
        SectionTitle("Open slots")
        openSlots.forEach { slot -> OpenSlotCard(slot = slot) }
        agentState.pendingProposal?.let { ProposalCard(proposal = it) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryStrip(
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MetricPill(label = "Timed", value = events.size.toString(), color = Accent)
        MetricPill(label = "Todo", value = todos.count { !it.done }.toString(), color = Risk)
        MetricPill(label = "Done", value = todos.count { it.done }.toString(), color = Success)
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    color: Color,
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.border(1.dp, Color(0xFFE6E6E2), RoundedCornerShape(6.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = value, color = color, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(text = label, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CalendarSurface(events: List<ScheduleEvent>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Calendar")
        Text(
            text = "Only time-bound attendance belongs here. If it can be done any time before a deadline, it belongs in Todo.",
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        events.forEach { event -> EventCard(event = event) }
    }
}

@Composable
private fun TodoSurface(todos: List<TodoItem>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Todo")
        Text(
            text = "Todo tracks deadline-bound work. The Agent may schedule focus blocks for it, but the Todo itself is not a calendar event.",
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        todos.forEach { todo -> TodoCard(todo = todo) }
    }
}

@Composable
private fun AgentSurface(agentState: AgentState) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Agent chain")
        Text(
            text = "Writes are confirmation-gated. The Agent can classify, validate, and propose, but durable JSON changes wait for explicit approval.",
            color = Muted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        )
        agentState.activeChain.forEach { step ->
            AgentStepRow(label = step.label, state = step.state)
        }
        agentState.pendingProposal?.let { ProposalCard(proposal = it) }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Ink,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.sp,
    )
}

@Composable
private fun EventCard(event: ScheduleEvent) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.border(1.dp, Color(0xFFE6E6E2), RoundedCornerShape(4.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RiskMarker(risk = event.risk)
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(text = event.timeRange, color = Muted, fontSize = 12.sp)
                Text(
                    text = event.title,
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = event.detail,
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                )
                Text(text = "#${event.tag}", color = Accent, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TodoCard(todo: TodoItem) {
    val color = when (todo.priority) {
        TodoPriority.High -> Danger
        TodoPriority.Medium -> Risk
        TodoPriority.Low -> Success
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.border(1.dp, Color(0xFFE6E6E2), RoundedCornerShape(4.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 3.dp)
                    .size(18.dp)
                    .border(2.dp, if (todo.done) Success else color, RoundedCornerShape(4.dp))
                    .background(if (todo.done) Color(0xFFEAF3EF) else Color.Transparent),
            )
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(text = todo.dueLabel, color = color, fontSize = 12.sp)
                Text(
                    text = todo.title,
                    color = Ink,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(text = todo.detail, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                Text(text = "#${todo.tag}", color = Accent, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ProposalCard(proposal: AgentProposal) {
    Surface(
        color = Color(0xFFFFF8EA),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.border(1.dp, Color(0xFFF3D7A3), RoundedCornerShape(6.dp)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = proposal.title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = proposal.summary, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            Text(text = proposal.impact, color = Risk, fontSize = 13.sp, lineHeight = 18.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text("Confirm")
                }
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F3F3F)),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text("Edit")
                }
            }
        }
    }
}

@Composable
private fun AgentStepRow(
    label: String,
    state: AgentStepState,
) {
    val color = when (state) {
        AgentStepState.Done -> Success
        AgentStepState.Active -> Accent
        AgentStepState.Waiting -> Muted
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFFE6E6E2), RoundedCornerShape(4.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(999.dp)),
        )
        Text(text = label, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(text = state.name.lowercase(), color = color, fontSize = 12.sp)
    }
}

@Composable
private fun RiskMarker(risk: ScheduleRisk) {
    val color = when (risk) {
        ScheduleRisk.Normal -> Success
        ScheduleRisk.Deadline -> Risk
        ScheduleRisk.Focus -> Accent
    }
    Box(
        modifier = Modifier
            .padding(top = 3.dp)
            .size(width = 4.dp, height = 58.dp)
            .background(color, RoundedCornerShape(4.dp)),
    )
}

@Composable
private fun OpenSlotCard(slot: OpenSlot) {
    Surface(
        color = SurfaceSoft,
        shape = RoundedCornerShape(4.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = slot.timeRange, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = slot.suggestion, color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun AgentComposer(modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Add schedule or todo") },
            singleLine = true,
            shape = RoundedCornerShape(6.dp),
        )
        Button(
            onClick = { text = "" },
            colors = ButtonDefaults.buttonColors(containerColor = Accent),
            shape = RoundedCornerShape(6.dp),
        ) {
            Text("Send")
        }
    }
}
