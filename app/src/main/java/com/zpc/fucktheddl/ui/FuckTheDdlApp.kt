package com.zpc.fucktheddl.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zpc.fucktheddl.agent.AgentApiClient
import com.zpc.fucktheddl.agent.AgentProposal
import com.zpc.fucktheddl.agent.CommitmentType
import com.zpc.fucktheddl.agent.mapCommitmentsToScheduleState
import com.zpc.fucktheddl.schedule.AgentState
import com.zpc.fucktheddl.schedule.AgentStepState
import com.zpc.fucktheddl.schedule.OpenSlot
import com.zpc.fucktheddl.schedule.ScheduleEvent
import com.zpc.fucktheddl.schedule.ScheduleRisk
import com.zpc.fucktheddl.schedule.ScheduleShellState
import com.zpc.fucktheddl.schedule.ScheduleTab
import com.zpc.fucktheddl.schedule.TabDestination
import com.zpc.fucktheddl.schedule.TodoItem
import com.zpc.fucktheddl.schedule.TodoPriority
import com.zpc.fucktheddl.voice.RealtimeAsrCallback
import com.zpc.fucktheddl.voice.RealtimeAsrClient

private val Ink = Color(0xFF171717)
private val InkSoft = Color(0xFF3F3F46)
private val Muted = Color(0xFF71717A)
private val Divider = Color(0xFFE4E4E7)
private val Canvas = Color(0xFFF7F7F5)
private val Panel = Color(0xFFFFFFFF)
private val Accent = Color(0xFF2563EB)
private val AccentSoft = Color(0xFFEAF1FF)
private val Risk = Color(0xFFD97706)
private val RiskSoft = Color(0xFFFFF4DE)
private val Danger = Color(0xFFDC2626)
private val DangerSoft = Color(0xFFFFE8E8)
private val Success = Color(0xFF059669)
private val SuccessSoft = Color(0xFFE8F6EF)

@Composable
fun FuckTheDdlApp(
    initialState: ScheduleShellState,
    agentApiClient: AgentApiClient? = null,
    asrClient: RealtimeAsrClient? = null,
) {
    var selectedTab by remember { mutableStateOf(initialState.selectedTab) }
    var shellState by remember { mutableStateOf(initialState) }
    var connectionLabel by remember { mutableStateOf(initialState.syncState.label) }
    var connectionHealthy by remember { mutableStateOf(true) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun refreshCommitments() {
        val client = agentApiClient
        if (client == null) {
            connectionLabel = "未连接后端"
            connectionHealthy = false
            return
        }
        Thread {
            runCatching {
                mapCommitmentsToScheduleState(client.commitments())
            }.onSuccess { commitments ->
                mainHandler.post {
                    shellState = shellState.copy(
                        events = commitments.events.ifEmpty { initialState.events },
                        todos = commitments.todos.ifEmpty { initialState.todos },
                    )
                    connectionLabel = "已同步"
                    connectionHealthy = true
                }
            }.onFailure { error ->
                mainHandler.post {
                    connectionLabel = error.message?.takeIf { it.isNotBlank() } ?: "连接失败"
                    connectionHealthy = false
                }
            }
        }.start()
    }

    LaunchedEffect(agentApiClient) {
        refreshCommitments()
    }

    Scaffold(
        containerColor = Canvas,
        bottomBar = {
            BottomWorkspace(
                tabs = initialState.tabs,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                agentApiClient = agentApiClient,
                asrClient = asrClient,
                onCommitmentsChanged = ::refreshCommitments,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            CompactHeader(
                selectedTab = selectedTab,
                connectionLabel = connectionLabel,
                connectionHealthy = connectionHealthy,
                events = shellState.events,
                todos = shellState.todos,
            )
            when (selectedTab.destination) {
                TabDestination.Today -> TodayTimeline(
                    events = shellState.events,
                    todos = shellState.todos,
                    openSlots = shellState.openSlots,
                )

                TabDestination.Calendar -> CalendarSurface(events = shellState.events)
                TabDestination.Todo -> TodoSurface(todos = shellState.todos)
                TabDestination.Agent -> AgentSurface(agentState = shellState.agentState)
            }
        }
    }
}

@Composable
private fun CompactHeader(
    selectedTab: ScheduleTab,
    connectionLabel: String,
    connectionHealthy: Boolean,
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                    text = selectedTab.label,
                    color = Ink,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.sp,
                )
                Text(
                    text = "fucktheddl",
                    color = Muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            StatusPill(
                label = connectionLabel,
                healthy = connectionHealthy,
            )
        }
        TodayBrief(events = events, todos = todos)
    }
}

@Composable
private fun TodayBrief(
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
) {
    val activeTodos = todos.count { !it.done }
    val urgentTodos = todos.count { !it.done && it.priority == TodoPriority.High }
    Surface(
        color = Panel,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryMetric(value = events.size.toString(), label = "日程", color = Accent)
            SummaryMetric(value = activeTodos.toString(), label = "待办", color = Risk)
            SummaryMetric(value = urgentTodos.toString(), label = "高压", color = Danger)
        }
    }
}

@Composable
private fun SummaryMetric(
    value: String,
    label: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = color,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text = label, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun StatusPill(
    label: String,
    healthy: Boolean,
) {
    val color = if (healthy) Success else Danger
    val background = if (healthy) SuccessSoft else DangerSoft
    Surface(
        color = background,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}

@Composable
private fun TodayTimeline(
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
    openSlots: List<OpenSlot>,
) {
    val pendingTodos = todos.filterNot { it.done }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(
            title = "今天时间线",
            caption = events.firstOrNull()?.timeRange ?: "暂无固定日程",
        )
        Surface(
            color = Panel,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Divider, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                if (events.isEmpty()) {
                    EmptyState(title = "今天没有固定日程", detail = "底部智能条可以直接添加安排。")
                } else {
                    events.forEachIndexed { index, event ->
                        TimelineEventRow(
                            event = event,
                            showConnector = index != events.lastIndex || openSlots.isNotEmpty(),
                        )
                    }
                }
                openSlots.take(2).forEach { slot ->
                    OpenSlotRow(slot = slot)
                }
            }
        }
        if (pendingTodos.isNotEmpty()) {
            DeadlinePressureBlock(todos = pendingTodos.take(3))
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    caption: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title,
            color = Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(text = caption, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun TimelineEventRow(
    event: ScheduleEvent,
    showConnector: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(event.risk.color(), RoundedCornerShape(999.dp)),
            )
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .width(2.dp)
                        .height(58.dp)
                        .background(Divider, RoundedCornerShape(999.dp)),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = event.timeRange,
                    color = Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(text = event.risk.label(), color = event.risk.color(), fontSize = 12.sp)
            }
            Text(
                text = event.title,
                color = Ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = event.detail,
                color = InkSoft,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            MetadataPill(text = event.tag, color = Accent, background = AccentSoft)
        }
    }
}

@Composable
private fun OpenSlotRow(slot: OpenSlot) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .border(2.dp, Divider, RoundedCornerShape(999.dp)),
            )
        }
        Surface(
            color = Canvas,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.weight(1f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = slot.timeRange, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(text = slot.suggestion, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun DeadlinePressureBlock(todos: List<TodoItem>) {
    Surface(
        color = RiskSoft,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF3D7A3), RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(title = "截止压力", caption = "${todos.size} 项待处理")
            todos.forEach { todo -> DeadlineRow(todo = todo) }
        }
    }
}

@Composable
private fun DeadlineRow(todo: TodoItem) {
    val color = todo.priority.color()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 3.dp)
                .size(18.dp)
                .border(2.dp, color, RoundedCornerShape(6.dp))
                .background(if (todo.done) SuccessSoft else Color.Transparent, RoundedCornerShape(6.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = todo.title, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(text = todo.dueLabel, color = color, fontSize = 12.sp)
            }
            Text(
                text = todo.detail,
                color = InkSoft,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CalendarSurface(events: List<ScheduleEvent>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(title = "日历", caption = "${events.size} 个固定安排")
        if (events.isEmpty()) {
            EmptyState(title = "暂无固定日程", detail = "有明确开始和结束时间的安排会出现在这里。")
        } else {
            events.forEach { event -> EventCard(event = event) }
        }
    }
}

@Composable
private fun TodoSurface(todos: List<TodoItem>) {
    val active = todos.filterNot { it.done }
    val done = todos.filter { it.done }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(title = "待办", caption = "${active.size} 项未完成")
        if (active.isEmpty() && done.isEmpty()) {
            EmptyState(title = "没有待办", detail = "把有截止期限的任务交给底部智能条。")
        } else {
            active.forEach { todo -> TodoCard(todo = todo) }
            if (done.isNotEmpty()) {
                SectionHeader(title = "已完成", caption = "${done.size} 项")
                done.forEach { todo -> TodoCard(todo = todo) }
            }
        }
    }
}

@Composable
private fun AgentSurface(agentState: AgentState) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionHeader(title = "智能体", caption = agentState.writePolicy)
        AgentStatusPanel(status = agentState.status)
        Surface(
            color = Panel,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Divider, RoundedCornerShape(18.dp)),
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                agentState.activeChain.forEachIndexed { index, step ->
                    AgentStepRow(
                        label = step.label,
                        state = step.state,
                        showConnector = index != agentState.activeChain.lastIndex,
                    )
                }
            }
        }
        agentState.pendingProposal?.let { proposal ->
            StaticProposalPanel(
                title = proposal.title,
                summary = proposal.summary,
                impact = proposal.impact,
            )
        }
    }
}

@Composable
private fun AgentStatusPanel(status: String) {
    Surface(
        color = AccentSoft,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(Accent, RoundedCornerShape(999.dp)),
            )
            Text(text = status, color = Accent, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EventCard(event: ScheduleEvent) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(width = 4.dp, height = 54.dp)
                    .background(event.risk.color(), RoundedCornerShape(999.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = event.timeRange, color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(text = event.title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(text = event.detail, color = InkSoft, fontSize = 13.sp, lineHeight = 18.sp)
                MetadataPill(text = event.tag, color = Accent, background = AccentSoft)
            }
        }
    }
}

@Composable
private fun TodoCard(todo: TodoItem) {
    val color = if (todo.done) Success else todo.priority.color()
    Surface(
        color = Panel,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .size(20.dp)
                    .border(2.dp, color, RoundedCornerShape(7.dp))
                    .background(if (todo.done) SuccessSoft else Color.Transparent, RoundedCornerShape(7.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = todo.dueLabel, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    MetadataPill(text = todo.priority.label(), color = color, background = color.copy(alpha = 0.12f))
                }
                Text(text = todo.title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(text = todo.detail, color = InkSoft, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
    }
}

@Composable
private fun StaticProposalPanel(
    title: String,
    summary: String,
    impact: String,
) {
    Surface(
        color = RiskSoft,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFF3D7A3), RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(text = summary, color = InkSoft, fontSize = 13.sp, lineHeight = 19.sp)
            Text(text = impact, color = Risk, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun AgentStepRow(
    label: String,
    state: AgentStepState,
    showConnector: Boolean,
) {
    val color = state.color()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(color, RoundedCornerShape(999.dp)),
            )
            if (showConnector) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .width(2.dp)
                        .height(34.dp)
                        .background(Divider, RoundedCornerShape(999.dp)),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = label, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(text = state.localizedLabel(), color = color, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MetadataPill(
    text: String,
    color: Color,
    background: Color,
) {
    Surface(
        color = background,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun EmptyState(
    title: String,
    detail: String,
) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(16.dp)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(text = detail, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
        }
    }
}

@Composable
private fun BottomWorkspace(
    tabs: List<ScheduleTab>,
    selectedTab: ScheduleTab,
    onTabSelected: (ScheduleTab) -> Unit,
    agentApiClient: AgentApiClient?,
    asrClient: RealtimeAsrClient?,
    onCommitmentsChanged: () -> Unit,
) {
    Surface(
        color = Panel,
        shadowElevation = 10.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            SmartComposer(
                agentApiClient = agentApiClient,
                asrClient = asrClient,
                onCommitmentsChanged = onCommitmentsChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Divider),
            )
            NavigationBar(
                containerColor = Panel,
                tonalElevation = 0.dp,
            ) {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        icon = {
                            Text(
                                text = tab.destination.symbol(),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        },
                        label = { Text(text = tab.label, fontSize = 12.sp) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SmartComposer(
    agentApiClient: AgentApiClient?,
    asrClient: RealtimeAsrClient?,
    onCommitmentsChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var inputText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(if (agentApiClient == null) "后端未连接" else "准备记录") }
    var proposal by remember { mutableStateOf<AgentProposal?>(null) }
    var lastCommitmentId by remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submit(value: String) {
        if (value.isBlank()) {
            status = "请输入日程或待办"
            return
        }
        val client = agentApiClient
        if (client == null) {
            status = "后端未连接，仍可编辑输入"
            return
        }
        status = "正在生成方案"
        Thread {
            val result = client.propose(value)
            mainHandler.post {
                if (result.proposal != null) {
                    proposal = result.proposal
                    status = "方案待确认"
                } else {
                    status = result.error ?: "连接失败"
                }
            }
        }.start()
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        proposal?.let { current ->
            AgentProposalSheet(
                proposal = current,
                onConfirm = {
                    val client = agentApiClient
                    if (client == null) {
                        status = "后端未连接"
                        return@AgentProposalSheet
                    }
                    status = "正在确认"
                    Thread {
                        val result = client.confirm(current.id)
                        mainHandler.post {
                            proposal = null
                            lastCommitmentId = result.commitmentId.takeIf { it.isNotBlank() }
                            status = result.error ?: "已确认并刷新"
                            if (result.error == null) {
                                inputText = ""
                                onCommitmentsChanged()
                            }
                        }
                    }.start()
                },
                onEdit = {
                    inputText = current.title
                    proposal = null
                    status = "编辑后重新发送"
                },
                onCancel = {
                    proposal = null
                    status = "已取消方案"
                },
            )
        }
        lastCommitmentId?.let { commitmentId ->
            UndoStrip(
                onUndo = {
                    val client = agentApiClient
                    if (client == null) {
                        status = "后端未连接"
                        return@UndoStrip
                    }
                    status = "正在撤销"
                    Thread {
                        val result = client.undo(commitmentId)
                        mainHandler.post {
                            status = result.error ?: "已撤销"
                            lastCommitmentId = null
                            if (result.error == null) {
                                onCommitmentsChanged()
                            }
                        }
                    }.start()
                },
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            keyboardController?.show()
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                focusRequester.requestFocus()
                                keyboardController?.show()
                            },
                        )
                    },
                placeholder = { Text("告诉智能体要安排什么") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Button(
                onClick = { submit(inputText) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(56.dp),
            ) {
                Text("发送")
            }
            HoldToSpeakButton(
                enabled = asrClient != null,
                listening = isListening,
                onPressStart = {
                    val client = asrClient
                    if (client == null) {
                        status = "语音暂不可用"
                    } else {
                        keyboardController?.hide()
                        isListening = true
                        status = "正在听"
                        client.start(object : RealtimeAsrCallback {
                            override fun onPartial(text: String) {
                                mainHandler.post {
                                    inputText = text
                                    status = "正在听"
                                }
                            }

                            override fun onFinal(text: String) {
                                mainHandler.post {
                                    inputText = text
                                    status = "已识别，正在生成方案"
                                    submit(text)
                                }
                            }

                            override fun onError(message: String) {
                                mainHandler.post {
                                    isListening = false
                                    status = message
                                }
                            }
                        })
                    }
                },
                onPressEnd = {
                    val client = asrClient
                    if (client != null && isListening) {
                        isListening = false
                        status = "正在结束"
                        client.stop()
                    }
                },
            )
        }
        Text(
            text = status,
            color = if (status.contains("失败") || status.contains("未连接")) Danger else Muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AgentProposalSheet(
    proposal: AgentProposal,
    onConfirm: () -> Unit,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(22.dp)),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetadataPill(
                    text = proposal.commitmentType.label(),
                    color = Accent,
                    background = AccentSoft,
                )
                Text(
                    text = if (proposal.requiresConfirmation) "待确认" else "可直接执行",
                    color = Risk,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(text = proposal.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(text = proposal.summary, color = InkSoft, fontSize = 13.sp, lineHeight = 19.sp)
            Text(text = proposal.impact, color = Risk, fontSize = 13.sp, lineHeight = 19.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("确认")
                }
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = InkSoft),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("编辑")
                }
                Button(
                    onClick = onCancel,
                    colors = ButtonDefaults.buttonColors(containerColor = Danger),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消")
                }
            }
        }
    }
}

@Composable
private fun UndoStrip(onUndo: () -> Unit) {
    Surface(
        color = SuccessSoft,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("上次修改已提交", color = Success, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Button(
                onClick = onUndo,
                colors = ButtonDefaults.buttonColors(containerColor = Risk),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("撤销")
            }
        }
    }
}

@Composable
private fun HoldToSpeakButton(
    enabled: Boolean,
    listening: Boolean,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit,
) {
    Surface(
        color = when {
            !enabled -> Divider
            listening -> Danger
            else -> Risk
        },
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .height(56.dp)
            .pointerInput(enabled) {
                detectTapGestures(
                    onPress = {
                        if (!enabled) return@detectTapGestures
                        onPressStart()
                        try {
                            tryAwaitRelease()
                        } finally {
                            onPressEnd()
                        }
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (listening) "松开" else "按住",
                color = if (enabled) Color.White else Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

private fun ScheduleRisk.color(): Color {
    return when (this) {
        ScheduleRisk.Normal -> Success
        ScheduleRisk.Deadline -> Risk
        ScheduleRisk.Focus -> Accent
    }
}

private fun ScheduleRisk.label(): String {
    return when (this) {
        ScheduleRisk.Normal -> "正常"
        ScheduleRisk.Deadline -> "临近"
        ScheduleRisk.Focus -> "专注"
    }
}

private fun TodoPriority.color(): Color {
    return when (this) {
        TodoPriority.High -> Danger
        TodoPriority.Medium -> Risk
        TodoPriority.Low -> Success
    }
}

private fun TodoPriority.label(): String {
    return when (this) {
        TodoPriority.High -> "高"
        TodoPriority.Medium -> "中"
        TodoPriority.Low -> "低"
    }
}

private fun AgentStepState.color(): Color {
    return when (this) {
        AgentStepState.Done -> Success
        AgentStepState.Active -> Accent
        AgentStepState.Waiting -> Muted
    }
}

private fun AgentStepState.localizedLabel(): String {
    return when (this) {
        AgentStepState.Done -> "已完成"
        AgentStepState.Active -> "进行中"
        AgentStepState.Waiting -> "等待"
    }
}

private fun CommitmentType.label(): String {
    return when (this) {
        CommitmentType.Schedule -> "日程"
        CommitmentType.Todo -> "待办"
        CommitmentType.Clarify -> "澄清"
    }
}

private fun TabDestination.symbol(): String {
    return when (this) {
        TabDestination.Today -> "今"
        TabDestination.Calendar -> "历"
        TabDestination.Todo -> "办"
        TabDestination.Agent -> "智"
    }
}
