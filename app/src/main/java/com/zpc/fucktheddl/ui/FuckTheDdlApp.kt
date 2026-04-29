package com.zpc.fucktheddl.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zpc.fucktheddl.agent.AgentApiClient
import com.zpc.fucktheddl.agent.AgentProposal
import com.zpc.fucktheddl.agent.mapCommitmentsToScheduleState
import com.zpc.fucktheddl.schedule.AgentState
import com.zpc.fucktheddl.schedule.AgentStepState
import com.zpc.fucktheddl.schedule.OpenSlot
import com.zpc.fucktheddl.schedule.ScheduleEvent
import com.zpc.fucktheddl.schedule.AgentProposal as ScheduleAgentProposal
import com.zpc.fucktheddl.schedule.ScheduleRisk
import com.zpc.fucktheddl.schedule.ScheduleShellState
import com.zpc.fucktheddl.schedule.ScheduleTab
import com.zpc.fucktheddl.schedule.TabDestination
import com.zpc.fucktheddl.schedule.TodoItem
import com.zpc.fucktheddl.schedule.TodoPriority
import com.zpc.fucktheddl.voice.RealtimeAsrCallback
import com.zpc.fucktheddl.voice.RealtimeAsrClient

private val Ink = Color(0xFF141414)
private val Muted = Color(0xFF6B6B6B)
private val Accent = Color(0xFF2563EB)
private val Risk = Color(0xFFD97706)
private val Danger = Color(0xFFDC2626)
private val Success = Color(0xFF059669)
private val SurfaceSoft = Color(0xFFF2F2EF)

@Composable
fun FuckTheDdlApp(
    initialState: ScheduleShellState,
    agentApiClient: AgentApiClient? = null,
    asrClient: RealtimeAsrClient? = null,
) {
    var selectedTab by remember { mutableStateOf(initialState.selectedTab) }
    var shellState by remember { mutableStateOf(initialState) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun refreshCommitments() {
        val client = agentApiClient ?: return
        Thread {
            runCatching {
                mapCommitmentsToScheduleState(client.commitments())
            }.onSuccess { commitments ->
                mainHandler.post {
                    shellState = shellState.copy(
                        events = commitments.events.ifEmpty { initialState.events },
                        todos = commitments.todos.ifEmpty { initialState.todos },
                    )
                }
            }
        }.start()
    }

    LaunchedEffect(agentApiClient) {
        refreshCommitments()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AgentComposer(
                agentApiClient = agentApiClient,
                asrClient = asrClient,
                onCommitmentsChanged = ::refreshCommitments,
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

            when (selectedTab.destination) {
                TabDestination.Today -> TodaySurface(
                    events = shellState.events,
                    todos = shellState.todos,
                    openSlots = shellState.openSlots,
                    agentState = shellState.agentState,
                )

                TabDestination.Calendar -> CalendarSurface(events = shellState.events)
                TabDestination.Todo -> TodoSurface(todos = shellState.todos)
                TabDestination.Agent -> AgentSurface(agentState = shellState.agentState)
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
                    text = "日程是必须在特定时间参与的事；待办是在期限前完成的事。智能体负责把它们分清楚。",
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
        SectionTitle("接下来要处理")
        events.take(2).forEach { event -> EventCard(event = event) }
        SectionTitle("截止事项")
        todos.filter { !it.done }.take(2).forEach { todo -> TodoCard(todo = todo) }
        SectionTitle("空档")
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
        MetricPill(label = "日程", value = events.size.toString(), color = Accent)
        MetricPill(label = "待办", value = todos.count { !it.done }.toString(), color = Risk)
        MetricPill(label = "完成", value = todos.count { it.done }.toString(), color = Success)
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
        SectionTitle("日历")
        Text(
            text = "只有必须在特定时间参与的事情才放在这里。只要能在截止前任意时间完成，就应该放进待办。",
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
        SectionTitle("待办")
        Text(
            text = "待办用于追踪有截止期限的任务。智能体可以为它安排专注时段，但待办本身不是日历事件。",
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
        SectionTitle("智能体链路")
        Text(
            text = "所有写入都需要确认。智能体可以分类、校验并提出方案，但持久化 JSON 修改必须等你明确批准。",
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
private fun ProposalCard(proposal: ScheduleAgentProposal) {
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
                    Text("确认")
                }
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F3F3F)),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text("编辑")
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
        Text(text = state.localizedLabel(), color = color, fontSize = 12.sp)
    }
}

private fun AgentStepState.localizedLabel(): String {
    return when (this) {
        AgentStepState.Done -> "已完成"
        AgentStepState.Active -> "进行中"
        AgentStepState.Waiting -> "等待"
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AgentComposer(
    agentApiClient: AgentApiClient?,
    asrClient: RealtimeAsrClient?,
    onCommitmentsChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var inputText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("按住说话") }
    var proposal by remember { mutableStateOf<AgentProposal?>(null) }
    var lastCommitmentId by remember { mutableStateOf<String?>(null) }
    var isListening by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    fun submit(value: String) {
        if (value.isBlank() || agentApiClient == null) return
        status = "正在提交"
        Thread {
            val result = agentApiClient.propose(value)
            mainHandler.post {
                if (result.proposal != null) {
                    proposal = result.proposal
                    status = "方案已生成"
                } else {
                    status = result.error ?: "智能体暂不可用"
                }
            }
        }.start()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        proposal?.let { current ->
            Surface(color = Color(0xFFFFF8EA), shape = RoundedCornerShape(6.dp)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(current.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text(current.summary, color = Muted, fontSize = 12.sp, lineHeight = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                Thread {
                                    val result = agentApiClient?.confirm(current.id)
                                    mainHandler.post {
                                        proposal = null
                                        lastCommitmentId = result?.commitmentId?.takeIf { it.isNotBlank() }
                                        status = result?.error ?: "已确认"
                                        if (result?.error == null) {
                                            onCommitmentsChanged()
                                        }
                                    }
                                }.start()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                            shape = RoundedCornerShape(6.dp),
                        ) { Text("确认") }
                        Button(
                            onClick = {
                                inputText = current.title
                                proposal = null
                                status = "正在编辑"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F3F3F)),
                            shape = RoundedCornerShape(6.dp),
                        ) { Text("编辑") }
                        Button(
                            onClick = {
                                proposal = null
                                status = "已取消"
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Danger),
                            shape = RoundedCornerShape(6.dp),
                        ) { Text("取消") }
                    }
                }
            }
        }
        lastCommitmentId?.let { commitmentId ->
            Surface(color = Color(0xFFEAF3EF), shape = RoundedCornerShape(6.dp)) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("上次修改已提交", color = Success, fontSize = 12.sp)
                    Button(
                        onClick = {
                            Thread {
                                val result = agentApiClient?.undo(commitmentId)
                                mainHandler.post {
                                    status = result?.error ?: "已撤销"
                                    lastCommitmentId = null
                                    if (result?.error == null) {
                                        onCommitmentsChanged()
                                    }
                                }
                            }.start()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Risk),
                        shape = RoundedCornerShape(6.dp),
                    ) { Text("撤销") }
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                placeholder = { Text("添加日程或待办") },
                singleLine = true,
                shape = RoundedCornerShape(6.dp),
            )
            Button(
                onClick = { submit(inputText) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(6.dp),
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
                                    status = "已识别"
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
        Text(status, color = Muted, fontSize = 12.sp)
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
        color = if (listening) Danger else Risk,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.pointerInput(enabled) {
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
        Text(
            text = if (listening) "松开" else "按住",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 13.dp),
        )
    }
}
