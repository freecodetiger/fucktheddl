package com.zpc.fucktheddl.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.zpc.fucktheddl.agent.AgentApiClient
import com.zpc.fucktheddl.agent.AgentApplyResult
import com.zpc.fucktheddl.agent.AgentProposal
import com.zpc.fucktheddl.agent.AgentProposalCandidate
import com.zpc.fucktheddl.agent.AgentSchedulePatch
import com.zpc.fucktheddl.agent.AgentSubmitResult
import com.zpc.fucktheddl.agent.AgentTodoPatch
import com.zpc.fucktheddl.agent.mapCommitmentsToScheduleState
import com.zpc.fucktheddl.agent.shouldEditProposalBeforeConfirm
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val Ink = Color(0xFF14282E)
private val InkSoft = Color(0xFF355158)
private val Muted = Color(0xFF75858A)
private val Divider = Color(0xFFE3E8EA)
private val Canvas = Color(0xFFFFFFFF)
private val Panel = Color(0xFFFFFFFF)
private val Accent = Color(0xFF2F7D8C)
private val AccentSoft = Color(0xFFEAF3F5)
private val Risk = Color(0xFFD88A3D)
private val RiskSoft = Color(0xFFFFF5E9)
private val Danger = Color(0xFFC94F4F)
private val DangerSoft = Color(0xFFFFECEB)
private val Success = Color(0xFF6FA58B)
private val SuccessSoft = Color(0xFFEFF6F2)
private val BottomInk = Color(0xFF111111)
private val BottomMuted = Color(0xFF8A8A8E)
private val VoiceIdle = Color(0xFF111111)
private val VoiceDisabled = Color(0xFFE8E8EA)

private val TodayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)

private enum class ComposerPhase {
    Idle,
    Working,
    Confirming,
    Error,
    ProposalReady,
    Confirmed,
}

private enum class BottomNavIcon {
    Today,
    Todo,
}

private data class EditableProposalDraft(
    val title: String,
    val date: String,
    val startTime: String,
    val endTime: String,
    val due: String,
    val notes: String,
    val priority: String,
) {
    fun toProposal(proposal: AgentProposal): AgentProposal {
        proposal.schedulePatch?.let { patch ->
            val start = "${date.ifBlank { patch.start.take(10) }}T${startTime.normalizedClock(patch.start.safeTime())}:00${patch.start.safeOffset()}"
            val end = "${date.ifBlank { patch.end.take(10) }}T${endTime.normalizedClock(patch.end.safeTime())}:00${patch.end.safeOffset()}"
            val schedulePatch = patch.copy(
                title = title.ifBlank { patch.title },
                start = start,
                end = end,
                notes = notes,
            )
            return proposal.copy(
                title = schedulePatch.title,
                summary = "${schedulePatch.title} ${schedulePatch.start.take(10)} ${schedulePatch.start.safeTime()}-${schedulePatch.end.safeTime()}",
                schedulePatch = schedulePatch,
            )
        }
        proposal.todoPatch?.let { patch ->
            val todoPatch = patch.copy(
                title = title.ifBlank { patch.title },
                due = due.ifBlank { patch.due },
                notes = notes,
                priority = priority.ifBlank { patch.priority },
            )
            return proposal.copy(
                title = todoPatch.title,
                summary = "${todoPatch.title} ${todoPatch.due}",
                todoPatch = todoPatch,
            )
        }
        return proposal
    }
}

@Composable
fun FuckTheDdlApp(
    initialState: ScheduleShellState,
    agentApiClient: AgentApiClient? = null,
    asrClient: RealtimeAsrClient? = null,
) {
    var shellState by remember { mutableStateOf(initialState) }
    val todayTab = remember(shellState.tabs) {
        shellState.tabs.firstOrNull { it.destination == TabDestination.Today }
            ?: ScheduleTab(label = "今天", destination = TabDestination.Today)
    }
    val todoTab = remember(shellState.tabs) {
        shellState.tabs.firstOrNull { it.destination == TabDestination.Todo }
            ?: ScheduleTab(label = "待办", destination = TabDestination.Todo)
    }
    var selectedTab by remember { mutableStateOf(if (initialState.selectedTab.destination == TabDestination.Todo) todoTab else todayTab) }
    var showingCalendar by remember { mutableStateOf(false) }
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

    fun deleteCommitment(commitmentId: String) {
        val client = agentApiClient
        if (commitmentId.isBlank() || client == null) {
            connectionLabel = "无法删除"
            connectionHealthy = false
            return
        }
        Thread {
            val result = client.undo(commitmentId)
            mainHandler.post {
                if (result.error == null) {
                    connectionLabel = "已删除"
                    connectionHealthy = true
                    refreshCommitments()
                } else {
                    connectionLabel = result.error
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
                selectedTab = selectedTab,
                onTodaySelected = {
                    selectedTab = todayTab
                    showingCalendar = false
                },
                onTodoSelected = {
                    selectedTab = todoTab
                    showingCalendar = false
                },
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
                connectionLabel = connectionLabel,
                connectionHealthy = connectionHealthy,
                onDateClick = {
                    selectedTab = todayTab
                    showingCalendar = true
                },
            )
            when {
                showingCalendar -> CalendarSurface(
                    events = shellState.events,
                    todos = shellState.todos,
                    onDeleteCommitment = ::deleteCommitment,
                )

                selectedTab.destination == TabDestination.Today -> TodayTimeline(
                    events = shellState.events,
                    todos = shellState.todos,
                    openSlots = shellState.openSlots,
                    onDeleteCommitment = ::deleteCommitment,
                )

                selectedTab.destination == TabDestination.Todo -> TodoSurface(
                    todos = shellState.todos,
                    onDeleteCommitment = ::deleteCommitment,
                )

                else -> TodayTimeline(
                    events = shellState.events,
                    todos = shellState.todos,
                    openSlots = shellState.openSlots,
                    onDeleteCommitment = ::deleteCommitment,
                )
            }
        }
    }
}

@Composable
private fun CompactHeader(
    connectionLabel: String,
    connectionHealthy: Boolean,
    onDateClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dateTitle = LocalDate.now().format(TodayFormatter)
        Text(
            text = dateTitle,
            color = Ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.sp,
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = { onDateClick() })
            },
        )
        StatusPill(
            label = connectionLabel,
            healthy = connectionHealthy,
        )
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
    onDeleteCommitment: (String) -> Unit,
) {
    val today = LocalDate.now()
    val todayEvents = events.filterForDate(today)
    val upcomingEvents = events
        .filterAfter(today)
        .sortedWith(compareBy<ScheduleEvent> { it.date.ifBlank { today.toString() } }.thenBy { it.timeRange })
        .take(4)
    val pendingTodos = todos.filterNot { it.done }
    val todayTodos = pendingTodos.filterForDueDate(today)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (todayEvents.isNotEmpty() || openSlots.isNotEmpty()) {
            Surface(
                color = Panel,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Divider, RoundedCornerShape(18.dp)),
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    todayEvents.forEachIndexed { index, event ->
                        TimelineEventRow(
                            event = event,
                            showConnector = index != todayEvents.lastIndex || openSlots.isNotEmpty(),
                            onDelete = { onDeleteCommitment(event.id) },
                        )
                    }
                    openSlots.take(2).forEach { slot ->
                        CompactOpenSlotRow(slot = slot)
                    }
                }
            }
        }
        if (todayTodos.isNotEmpty()) {
            DeadlinePressureBlock(
                todos = todayTodos.take(3),
                onDeleteCommitment = onDeleteCommitment,
            )
        }
        if (upcomingEvents.isNotEmpty()) {
            UpcomingBlock(
                events = upcomingEvents,
                onDeleteCommitment = onDeleteCommitment,
            )
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
        if (caption.isNotBlank()) {
            Text(text = caption, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TimelineEventRow(
    event: ScheduleEvent,
    showConnector: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(14.dp)) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(12.dp)
                    .background(Divider, RoundedCornerShape(999.dp)),
            )
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(event.risk.color(), RoundedCornerShape(999.dp)),
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(if (showConnector) 18.dp else 12.dp)
                    .background(Divider, RoundedCornerShape(999.dp)),
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = event.timeRange,
                color = Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(88.dp),
            )
            Text(
                text = event.title,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (event.id.isNotBlank()) {
                MiniDeleteButton(onClick = onDelete)
            }
        }
    }
}

@Composable
private fun CompactOpenSlotRow(slot: OpenSlot) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(14.dp)) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(26.dp)
                    .background(Divider, RoundedCornerShape(999.dp)),
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .border(2.dp, Divider, RoundedCornerShape(999.dp)),
            )
        }
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = slot.timeRange, color = Muted, fontSize = 12.sp, modifier = Modifier.width(88.dp))
            Text(text = "空档", color = Muted, fontSize = 14.sp, maxLines = 1)
        }
    }
}

@Composable
private fun DeadlinePressureBlock(
    todos: List<TodoItem>,
    onDeleteCommitment: (String) -> Unit,
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            todos.forEach { todo ->
                DeadlineRow(
                    todo = todo,
                    onDelete = { onDeleteCommitment(todo.id) },
                )
            }
        }
    }
}

@Composable
private fun DeadlineRow(
    todo: TodoItem,
    onDelete: () -> Unit,
) {
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
            if (todo.id.isNotBlank()) {
                MiniDeleteButton(onClick = onDelete)
            }
        }
    }
}

@Composable
private fun UpcomingBlock(
    events: List<ScheduleEvent>,
    onDeleteCommitment: (String) -> Unit,
) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            events.forEach { event ->
                UpcomingEventRow(
                    event = event,
                    onDelete = { onDeleteCommitment(event.id) },
                )
            }
        }
    }
}

@Composable
private fun UpcomingEventRow(
    event: ScheduleEvent,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = event.relativeDateLabel(),
            color = Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(48.dp),
        )
        Text(
            text = event.timeRange.substringBefore(" - "),
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.width(44.dp),
        )
        Text(
            text = event.title,
            color = Ink,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (event.id.isNotBlank()) {
            MiniDeleteButton(onClick = onDelete)
        }
    }
}

@Composable
private fun CalendarSurface(
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
    onDeleteCommitment: (String) -> Unit,
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val month = remember(events) {
            events.firstNotNullOfOrNull { event ->
                runCatching { YearMonth.from(LocalDate.parse(event.date)) }.getOrNull()
            } ?: YearMonth.now()
        }
        SectionHeader(title = "${month.year}年${month.monthValue}月", caption = "")
        CalendarMonthGrid(
            month = month,
            events = events,
            todos = todos,
            selectedDate = selectedDate,
            onDateSelected = { selectedDate = it },
        )
        val selectedEvents = events.filter { it.date == selectedDate.toString() }
        val selectedTodos = todos.filter { it.dueDate == selectedDate.toString() && !it.done }
        SectionHeader(
            title = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日",
            caption = "",
        )
        if (selectedEvents.isEmpty() && selectedTodos.isEmpty()) {
            EmptyState(title = "—", detail = "")
        } else {
            selectedEvents.forEach { event ->
                EventCard(
                    event = event,
                    onDelete = { onDeleteCommitment(event.id) },
                )
            }
            selectedTodos.forEach { todo ->
                TodoCard(
                    todo = todo,
                    onDelete = { onDeleteCommitment(todo.id) },
                )
            }
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    month: YearMonth,
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
) {
    val eventsByDate = events.groupBy { it.date }
    val todosByDate = todos.filterNot { it.done }.groupBy { it.dueDate }
    val firstDay = month.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val cells = List(leadingBlanks) { null } + List(month.lengthOfMonth()) { day -> month.atDay(day + 1) }
    val paddedCells = cells + List((7 - cells.size % 7) % 7) { null }
    Surface(
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(18.dp)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                    Text(
                        text = label,
                        color = Muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            paddedCells.chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    week.forEach { date ->
                        CalendarDayCell(
                            date = date,
                            events = date?.let { eventsByDate[it.toString()].orEmpty() }.orEmpty(),
                            todos = date?.let { todosByDate[it.toString()].orEmpty() }.orEmpty(),
                            selected = date == selectedDate,
                            onClick = { if (date != null) onDateSelected(date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCell(
    date: LocalDate?,
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val eventCount = events.size
    val hasDdl = todos.isNotEmpty()
    val isToday = date == LocalDate.now()
    val loadColor = when {
        hasDdl -> Danger
        eventCount >= 4 -> Risk
        eventCount >= 2 -> Accent
        eventCount == 1 -> Success
        else -> Muted
    }
    val background = when {
        selected -> AccentSoft
        hasDdl -> DangerSoft
        eventCount >= 4 -> RiskSoft
        eventCount > 0 -> SuccessSoft
        else -> Canvas
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .height(58.dp)
            .border(
                width = if (isToday || selected) 2.dp else 0.dp,
                color = if (hasDdl) Danger else if (isToday || selected) Accent else Color.Transparent,
                shape = RoundedCornerShape(14.dp),
            )
            .pointerInput(date) {
                detectTapGestures(onTap = { if (date != null) onClick() })
            },
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = date?.dayOfMonth?.toString().orEmpty(),
                    color = if (date == null) Divider else loadColor,
                    fontSize = 13.sp,
                    fontWeight = if (eventCount > 0 || hasDdl || isToday) FontWeight.SemiBold else FontWeight.Normal,
                )
                if (eventCount > 0 || hasDdl) {
                    Surface(color = loadColor, shape = RoundedCornerShape(999.dp)) {
                        Text(
                            text = if (hasDdl) "!" else eventCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(eventCount.coerceAtMost(4)) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(loadColor, RoundedCornerShape(999.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun TodoSurface(
    todos: List<TodoItem>,
    onDeleteCommitment: (String) -> Unit,
) {
    val active = todos.filterNot { it.done }
    val done = todos.filter { it.done }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (active.isEmpty() && done.isEmpty()) {
            EmptyState(title = "—", detail = "")
        } else {
            active.forEach { todo ->
                TodoCard(
                    todo = todo,
                    onDelete = { onDeleteCommitment(todo.id) },
                )
            }
            if (done.isNotEmpty()) {
                done.forEach { todo ->
                    TodoCard(
                        todo = todo,
                        onDelete = { onDeleteCommitment(todo.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EventCard(
    event: ScheduleEvent,
    onDelete: () -> Unit,
) {
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MetadataPill(text = event.tag, color = Accent, background = AccentSoft)
                    if (event.id.isNotBlank()) {
                        MiniDeleteButton(onClick = onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoCard(
    todo: TodoItem,
    onDelete: () -> Unit,
) {
    val color = if (todo.done) Success else todo.priority.color()
    val density = LocalDensity.current
    val revealWidthPx = with(density) { 88.dp.toPx() }
    var targetOffsetX by remember { mutableStateOf(0f) }
    val animatedOffsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "todo-swipe-offset",
    )
    val revealDelete = animatedOffsetX < -1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp),
    ) {
        if (revealDelete) {
            Surface(
                color = Danger,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(68.dp)
                    .width(88.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onDelete() })
                    },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(text = "删除", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Surface(
            color = Panel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .offset { androidx.compose.ui.unit.IntOffset(animatedOffsetX.roundToInt(), 0) }
                .border(1.dp, Divider, RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            targetOffsetX = if (targetOffsetX < -revealWidthPx * 0.42f) {
                                -revealWidthPx
                            } else {
                                0f
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            targetOffsetX = (targetOffsetX + dragAmount).coerceIn(-revealWidthPx, 0f)
                        },
                    )
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 4.dp, height = 42.dp)
                        .background(color, RoundedCornerShape(999.dp)),
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
                        Text(
                            text = todo.title,
                            color = Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = todo.dueLabel,
                            color = color,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Text(
                        text = todo.detail,
                        color = InkSoft,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
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
private fun MiniDeleteButton(onClick: () -> Unit) {
    Surface(
        color = DangerSoft,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onTap = { onClick() })
        },
    ) {
        Text(
            text = "删除",
            color = Danger,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(text = title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (detail.isNotBlank()) {
                Text(text = detail, color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
    }
}

@Composable
private fun BottomWorkspace(
    selectedTab: ScheduleTab,
    onTodaySelected: () -> Unit,
    onTodoSelected: () -> Unit,
    agentApiClient: AgentApiClient?,
    asrClient: RealtimeAsrClient?,
    onCommitmentsChanged: () -> Unit,
) {
    Surface(
        color = Panel,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Divider),
            )
            VoiceAgentDock(
                selectedTab = selectedTab,
                onTodaySelected = onTodaySelected,
                onTodoSelected = onTodoSelected,
                agentApiClient = agentApiClient,
                asrClient = asrClient,
                onCommitmentsChanged = onCommitmentsChanged,
                modifier = Modifier
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun VoiceAgentDock(
    selectedTab: ScheduleTab,
    onTodaySelected: () -> Unit,
    onTodoSelected: () -> Unit,
    agentApiClient: AgentApiClient?,
    asrClient: RealtimeAsrClient?,
    onCommitmentsChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var status by remember { mutableStateOf(if (agentApiClient == null) "后端未连接" else "准备说话") }
    var submittedText by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf(ComposerPhase.Idle) }
    var proposal by remember { mutableStateOf<AgentProposal?>(null) }
    var activeRequestId by remember { mutableStateOf(0) }
    var isListening by remember { mutableStateOf(false) }
    var voiceText by remember { mutableStateOf("") }
    var voiceCancelArmed by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun submit(value: String) {
        val prompt = value.trim()
        if (prompt.isBlank()) {
            status = "没有识别到内容"
            return
        }
        submittedText = prompt
        proposal = null
        val client = agentApiClient
        if (client == null) {
            phase = ComposerPhase.Error
            status = "后端未连接"
            return
        }
        phase = ComposerPhase.Working
        status = "正在整理"
        activeRequestId += 1
        val requestId = activeRequestId
        Thread {
            val result = client.propose(prompt)
            mainHandler.post {
                if (requestId != activeRequestId) {
                    return@post
                }
                if (result.proposal != null) {
                    proposal = result.proposal
                    phase = ComposerPhase.ProposalReady
                    status = "方案待确认"
                } else {
                    phase = ComposerPhase.Error
                    status = result.error ?: "连接失败"
                }
            }
            }.start()
    }

    val overlayVisible = isListening ||
        phase == ComposerPhase.Working ||
        phase == ComposerPhase.Confirming ||
        phase == ComposerPhase.ProposalReady ||
        phase == ComposerPhase.Error

    Box(modifier = modifier) {
        if (overlayVisible) {
            VoiceInteractionOverlay(
                phase = phase,
                status = status,
                transcript = when {
                    isListening -> voiceText
                    submittedText.isNullOrBlank() -> voiceText
                    else -> submittedText.orEmpty()
                },
                cancelArmed = voiceCancelArmed,
                canCancelVoice = isListening,
                proposal = proposal,
                onConfirm = { editedProposal, edited ->
                    val current = editedProposal
                    val client = agentApiClient
                    if (client == null) {
                        phase = ComposerPhase.Error
                        status = "后端未连接"
                        return@VoiceInteractionOverlay
                    }
                    phase = ComposerPhase.Confirming
                    status = "确认中"
                    Thread {
                        val editResult = if (shouldEditProposalBeforeConfirm(current, edited)) {
                            client.editProposal(current)
                        } else {
                            AgentSubmitResult(proposal = current, error = null)
                        }
                        val result = if (editResult.error == null) {
                            client.confirm(editResult.proposal?.id ?: current.id)
                        } else {
                            AgentApplyResult(status = "failed", commitmentId = "", error = editResult.error)
                        }
                        mainHandler.post {
                            proposal = null
                            status = result.error ?: "已确认并刷新"
                            if (result.error == null) {
                                submittedText = null
                                phase = ComposerPhase.Confirmed
                                onCommitmentsChanged()
                            } else {
                                phase = ComposerPhase.Error
                            }
                        }
                    }.start()
                },
                onCancel = {
                    activeRequestId += 1
                    submittedText = null
                    proposal = null
                    phase = ComposerPhase.Idle
                    status = "准备说话"
                    if (isListening) {
                        isListening = false
                        voiceCancelArmed = false
                        voiceText = ""
                        asrClient?.cancel()
                    }
                },
                onCandidateSelected = { candidate ->
                    submit(candidate.resolutionText)
                },
            )
        }
        BottomVoiceNav(
            selectedTab = selectedTab,
            onTodaySelected = onTodaySelected,
            onTodoSelected = onTodoSelected,
            voiceEnabled = asrClient != null && phase != ComposerPhase.Working && phase != ComposerPhase.Confirming,
            listening = isListening,
            cancelArmed = voiceCancelArmed,
            onPressStart = {
                val client = asrClient
                if (client == null) {
                    status = "语音暂不可用"
                    phase = ComposerPhase.Error
                    return@BottomVoiceNav
                }
                activeRequestId += 1
                proposal = null
                submittedText = null
                voiceText = ""
                voiceCancelArmed = false
                isListening = true
                phase = ComposerPhase.Idle
                status = "正在听"
                client.start(object : RealtimeAsrCallback {
                    override fun onPartial(text: String) {
                        mainHandler.post {
                            voiceText = text
                            status = "正在听"
                        }
                    }

                    override fun onFinal(text: String) {
                        mainHandler.post {
                            voiceText = text
                            status = "已识别，松手发送"
                        }
                    }

                    override fun onError(message: String) {
                        mainHandler.post {
                            isListening = false
                            voiceCancelArmed = false
                            phase = ComposerPhase.Error
                            status = message
                        }
                    }
                })
            },
            onCancelMove = { armed ->
                voiceCancelArmed = armed
                status = if (armed) "松手取消" else "正在听"
            },
            onPressEnd = {
                val client = asrClient
                val releaseRequestId = activeRequestId + 1
                activeRequestId = releaseRequestId
                isListening = false
                voiceCancelArmed = false
                phase = ComposerPhase.Working
                status = "正在收尾"
                client?.stop()
                mainHandler.postDelayed(
                    {
                        if (releaseRequestId != activeRequestId) {
                            return@postDelayed
                        }
                        val text = voiceText.trim()
                        if (text.isNotBlank()) {
                            status = "正在整理"
                            submit(text)
                        } else {
                            status = "没有识别到内容"
                            phase = ComposerPhase.Error
                        }
                    },
                    1200L,
                )
            },
            onCancel = {
                activeRequestId += 1
                isListening = false
                voiceCancelArmed = false
                voiceText = ""
                asrClient?.cancel()
                status = "已取消语音"
            },
        )
    }
}

@Composable
private fun VoiceInteractionOverlay(
    phase: ComposerPhase,
    status: String,
    transcript: String,
    cancelArmed: Boolean,
    canCancelVoice: Boolean,
    proposal: AgentProposal?,
    onConfirm: (AgentProposal, Boolean) -> Unit,
    onCancel: () -> Unit,
    onCandidateSelected: (AgentProposalCandidate) -> Unit,
) {
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White.copy(alpha = 0.92f))
                .padding(horizontal = 24.dp, vertical = 64.dp),
        ) {
            if (canCancelVoice) {
                VoiceCancelZone(
                    armed = cancelArmed,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(x = 68.dp, y = (-54).dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                val proposalReviewVisible = phase == ComposerPhase.ProposalReady &&
                    proposal != null &&
                    proposal.candidates.isEmpty()
                if (!proposalReviewVisible) {
                    LargeVoiceWave(
                        active = phase == ComposerPhase.Working ||
                            phase == ComposerPhase.Confirming ||
                            status.contains("听"),
                        danger = cancelArmed || phase == ComposerPhase.Error,
                    )
                    VoiceOverlayCopy(
                        phase = phase,
                        status = status,
                        transcript = transcript,
                        proposal = proposal,
                    )
                }
                when {
                    phase == ComposerPhase.ProposalReady && proposal != null && proposal.candidates.isNotEmpty() -> {
                        CandidateChoiceList(
                            candidates = proposal.candidates,
                            onCandidateSelected = onCandidateSelected,
                        )
                    }

                    phase == ComposerPhase.ProposalReady && proposal != null -> {
                        ProposalReviewPanel(
                            proposal = proposal,
                            onConfirm = onConfirm,
                            onClose = onCancel,
                        )
                    }

                    phase == ComposerPhase.Error -> {
                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(containerColor = InkSoft),
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text("关闭")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoiceOverlayCopy(
    phase: ComposerPhase,
    status: String,
    transcript: String,
    proposal: AgentProposal?,
) {
    val title = when {
        phase == ComposerPhase.ProposalReady && proposal != null && proposal.candidates.isNotEmpty() -> proposal.title
        phase == ComposerPhase.ProposalReady && proposal != null -> proposal.title
        transcript.isNotBlank() -> transcript
        phase == ComposerPhase.Working -> "处理中"
        phase == ComposerPhase.Confirming -> "确认中"
        phase == ComposerPhase.Error -> status
        else -> "正在听"
    }
    val subtitle = when {
        phase == ComposerPhase.ProposalReady && proposal != null && proposal.candidates.isNotEmpty() -> proposal.summary
        phase == ComposerPhase.ProposalReady && proposal != null -> ""
        phase == ComposerPhase.Working -> ""
        phase == ComposerPhase.Confirming -> ""
        phase == ComposerPhase.Error -> ""
        status.contains("取消") -> "松手取消"
        else -> "松手发送"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            color = if (phase == ComposerPhase.Error) Danger else Ink,
            fontSize = if (title.length <= 8) 32.sp else 24.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 36.sp,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = if (phase == ComposerPhase.Error) Danger else Muted,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CandidateChoiceList(
    candidates: List<AgentProposalCandidate>,
    onCandidateSelected: (AgentProposalCandidate) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        candidates.take(5).forEach { candidate ->
            Surface(
                color = Color.White.copy(alpha = 0.72f),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Divider, RoundedCornerShape(18.dp))
                    .pointerInput(candidate.id) {
                        detectTapGestures(onTap = { onCandidateSelected(candidate) })
                    },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(Accent, RoundedCornerShape(999.dp)),
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = candidate.title,
                            color = Ink,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = candidate.whenLabel,
                            color = Muted,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(text = "选择", color = Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ProposalReviewPanel(
    proposal: AgentProposal,
    onConfirm: (AgentProposal, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    var editing by remember(proposal.id) { mutableStateOf(false) }
    val originalDraft = remember(proposal.id) { proposal.toEditableDraft() }
    var draft by remember(proposal.id) { mutableStateOf(originalDraft) }
    val editedProposal = draft.toProposal(proposal)
    val edited = draft != originalDraft
    Surface(
        color = Color.White.copy(alpha = 0.92f),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 18.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(28.dp)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (editing && proposal.schedulePatch != null) {
                EditableScheduleFields(
                    draft = draft,
                    onDraftChanged = { draft = it },
                )
            } else if (editing && proposal.todoPatch != null) {
                EditableTodoFields(
                    draft = draft,
                    onDraftChanged = { draft = it },
                )
            } else {
                ProposalReadOnlySummary(proposal = editedProposal)
            }
            Button(
                onClick = { onConfirm(if (edited) editedProposal else proposal, edited) },
                colors = ButtonDefaults.buttonColors(containerColor = Accent),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text("确认", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction(
                    text = if (editing) "完成编辑" else "编辑",
                    onClick = { editing = !editing },
                )
                Text(text = "  ·  ", color = Divider, fontSize = 14.sp)
                TextAction(text = "关闭", onClick = onClose)
            }
        }
    }
}

@Composable
private fun ProposalReadOnlySummary(proposal: AgentProposal) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = proposal.title,
            color = Ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 32.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        proposal.schedulePatch?.let { patch ->
            Text(
                text = "${patch.start.take(10)}  ${patch.start.safeTime()}-${patch.end.safeTime()}",
                color = InkSoft,
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
            )
            if (patch.notes.isNotBlank()) {
                Text(text = patch.notes, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        proposal.todoPatch?.let { patch ->
            Text(text = patch.due, color = InkSoft, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (patch.notes.isNotBlank()) {
                Text(text = patch.notes, color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EditableScheduleFields(
    draft: EditableProposalDraft,
    onDraftChanged: (EditableProposalDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CompactEditField(value = draft.title, onValueChange = { onDraftChanged(draft.copy(title = it)) }, label = "标题")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactEditField(
                value = draft.date,
                onValueChange = { onDraftChanged(draft.copy(date = it)) },
                label = "日期",
                modifier = Modifier.weight(1.25f),
            )
            CompactEditField(
                value = draft.startTime,
                onValueChange = { onDraftChanged(draft.copy(startTime = it)) },
                label = "开始",
                modifier = Modifier.weight(1f),
            )
            CompactEditField(
                value = draft.endTime,
                onValueChange = { onDraftChanged(draft.copy(endTime = it)) },
                label = "结束",
                modifier = Modifier.weight(1f),
            )
        }
        CompactEditField(value = draft.notes, onValueChange = { onDraftChanged(draft.copy(notes = it)) }, label = "备注")
    }
}

@Composable
private fun EditableTodoFields(
    draft: EditableProposalDraft,
    onDraftChanged: (EditableProposalDraft) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CompactEditField(value = draft.title, onValueChange = { onDraftChanged(draft.copy(title = it)) }, label = "标题")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompactEditField(
                value = draft.due,
                onValueChange = { onDraftChanged(draft.copy(due = it)) },
                label = "截止",
                modifier = Modifier.weight(1.4f),
            )
            CompactEditField(
                value = draft.priority,
                onValueChange = { onDraftChanged(draft.copy(priority = it)) },
                label = "优先级",
                modifier = Modifier.weight(1f),
            )
        }
        CompactEditField(value = draft.notes, onValueChange = { onDraftChanged(draft.copy(notes = it)) }, label = "备注")
    }
}

@Composable
private fun CompactEditField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun TextAction(
    text: String,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = Accent,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .pointerInput(onClick) {
                detectTapGestures(onTap = { onClick() })
            },
    )
}

@Composable
private fun CallActionButton(
    symbol: String,
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            color = color,
            shape = RoundedCornerShape(999.dp),
            shadowElevation = 10.dp,
            modifier = Modifier
                .size(72.dp)
                .pointerInput(onClick) {
                    detectTapGestures(onTap = { onClick() })
                },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(text = symbol, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Text(text = label, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LargeVoiceWave(
    active: Boolean,
    danger: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "voice-wave")
    val pulse by transition.animateFloat(
        initialValue = if (active) 0.55f else 0.28f,
        targetValue = if (active) 1f else 0.42f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (active) 620 else 1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "voice-wave-pulse",
    )
    val color = if (danger) Danger else Accent
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(128.dp)) {
        Box(
            modifier = Modifier
                .size((96 + pulse * 24).dp)
                .background(color.copy(alpha = 0.10f * pulse), RoundedCornerShape(999.dp)),
        )
        Box(
            modifier = Modifier
                .size((66 + pulse * 14).dp)
                .background(color.copy(alpha = 0.18f * pulse), RoundedCornerShape(999.dp)),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(5) { index ->
                val height = 18 + ((index % 3) + 1) * 8 * pulse
                Box(
                    modifier = Modifier
                        .width(5.dp)
                        .height(height.dp)
                        .background(color, RoundedCornerShape(999.dp)),
                )
            }
        }
    }
}

@Composable
private fun MiniWaveform(color: Color) {
    val transition = rememberInfiniteTransition(label = "mini-wave")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "mini-wave-pulse",
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height((10 + index * 5 + pulse * 8).dp)
                    .background(color, RoundedCornerShape(999.dp)),
            )
        }
    }
}

@Composable
private fun MicGlyph(color: Color) {
    Canvas(modifier = Modifier.size(width = 34.dp, height = 30.dp)) {
        val strokeWidth = 2.2.dp.toPx()
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(13.dp.toPx(), 4.dp.toPx()),
            size = Size(8.dp.toPx(), 16.dp.toPx()),
            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(8.dp.toPx(), 14.dp.toPx()),
            end = Offset(8.dp.toPx(), 16.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(8.dp.toPx(), 16.dp.toPx()),
            end = Offset(11.dp.toPx(), 22.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(11.dp.toPx(), 22.dp.toPx()),
            end = Offset(17.dp.toPx(), 24.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(17.dp.toPx(), 24.dp.toPx()),
            end = Offset(23.dp.toPx(), 22.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(23.dp.toPx(), 22.dp.toPx()),
            end = Offset(26.dp.toPx(), 16.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(26.dp.toPx(), 16.dp.toPx()),
            end = Offset(26.dp.toPx(), 14.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(17.dp.toPx(), 24.dp.toPx()),
            end = Offset(17.dp.toPx(), 27.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(13.dp.toPx(), 27.dp.toPx()),
            end = Offset(21.dp.toPx(), 27.dp.toPx()),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun BottomVoiceNav(
    selectedTab: ScheduleTab,
    onTodaySelected: () -> Unit,
    onTodoSelected: () -> Unit,
    voiceEnabled: Boolean,
    listening: Boolean,
    cancelArmed: Boolean,
    onPressStart: () -> Unit,
    onCancelMove: (Boolean) -> Unit,
    onPressEnd: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(98.dp)
            .background(Color.White)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTabButton(
            icon = BottomNavIcon.Today,
            label = "今天",
            selected = selectedTab.destination == TabDestination.Today,
            onClick = onTodaySelected,
            modifier = Modifier.weight(1f),
        )
        VoicePrimaryButton(
            enabled = voiceEnabled,
            listening = listening,
            cancelArmed = cancelArmed,
            onPressStart = onPressStart,
            onCancelMove = onCancelMove,
            onPressEnd = onPressEnd,
            onCancel = onCancel,
            modifier = Modifier.weight(1f),
        )
        BottomTabButton(
            icon = BottomNavIcon.Todo,
            label = "待办",
            selected = selectedTab.destination == TabDestination.Todo,
            onClick = onTodoSelected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomTabButton(
    icon: BottomNavIcon,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) BottomInk else BottomMuted
    Column(
        modifier = modifier
            .height(72.dp)
            .pointerInput(onClick) {
                detectTapGestures(onTap = { onClick() })
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        AppleTabIcon(icon = icon, color = color, selected = selected)
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            letterSpacing = 0.2.sp,
        )
    }
}

@Composable
private fun AppleTabIcon(
    icon: BottomNavIcon,
    color: Color,
    selected: Boolean,
) {
    Canvas(modifier = Modifier.size(width = 34.dp, height = 30.dp)) {
        val strokeWidth = if (selected) 2.4.dp.toPx() else 2.dp.toPx()
        val stroke = Stroke(
            width = strokeWidth,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        when (icon) {
            BottomNavIcon.Today -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(6.dp.toPx(), 7.dp.toPx()),
                    size = Size(22.dp.toPx(), 19.dp.toPx()),
                    cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = stroke,
                )
                drawLine(
                    color = color,
                    start = Offset(6.dp.toPx(), 13.dp.toPx()),
                    end = Offset(28.dp.toPx(), 13.dp.toPx()),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(12.dp.toPx(), 5.dp.toPx()),
                    end = Offset(12.dp.toPx(), 9.dp.toPx()),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(22.dp.toPx(), 5.dp.toPx()),
                    end = Offset(22.dp.toPx(), 9.dp.toPx()),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawCircle(color = color, radius = 1.7.dp.toPx(), center = Offset(14.dp.toPx(), 19.dp.toPx()))
                drawCircle(color = color, radius = 1.7.dp.toPx(), center = Offset(20.dp.toPx(), 19.dp.toPx()))
            }

            BottomNavIcon.Todo -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(5.dp.toPx(), 6.dp.toPx()),
                    size = Size(24.dp.toPx(), 21.dp.toPx()),
                    cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                    style = stroke,
                )
                drawLine(
                    color = color,
                    start = Offset(11.dp.toPx(), 14.dp.toPx()),
                    end = Offset(14.dp.toPx(), 17.dp.toPx()),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color,
                    start = Offset(14.dp.toPx(), 17.dp.toPx()),
                    end = Offset(19.dp.toPx(), 11.dp.toPx()),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = color.copy(alpha = 0.82f),
                    start = Offset(12.dp.toPx(), 22.dp.toPx()),
                    end = Offset(23.dp.toPx(), 22.dp.toPx()),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun VoiceCancelZone(
    armed: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (armed) DangerSoft else Color(0xFFF7F7F8),
        shape = RoundedCornerShape(999.dp),
        modifier = modifier.border(
            width = 1.dp,
            color = if (armed) Danger else Divider,
            shape = RoundedCornerShape(999.dp),
        ),
    ) {
        Text(
            text = if (armed) "松手取消" else "↗ 取消",
            color = if (armed) Danger else Muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}

@Composable
private fun VoicePrimaryButton(
    enabled: Boolean,
    listening: Boolean,
    cancelArmed: Boolean,
    onPressStart: () -> Unit,
    onCancelMove: (Boolean) -> Unit,
    onPressEnd: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val diagonalCancelX = with(LocalDensity.current) { 34.dp.toPx() }
    val diagonalCancelY = with(LocalDensity.current) { 22.dp.toPx() }
    val forgivingCancelY = with(LocalDensity.current) { 58.dp.toPx() }
    val upwardFallbackY = with(LocalDensity.current) { 96.dp.toPx() }
    Column(
        modifier = Modifier
            .then(modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Surface(
            color = when {
                !enabled -> VoiceDisabled
                cancelArmed -> Danger
                listening -> VoiceIdle
                else -> VoiceIdle
            },
            shape = RoundedCornerShape(999.dp),
            shadowElevation = if (listening) 8.dp else 0.dp,
            modifier = Modifier
                .size(58.dp)
                .pointerInput(enabled, diagonalCancelX, diagonalCancelY, forgivingCancelY, upwardFallbackY) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!enabled) {
                            return@awaitEachGesture
                        }
                        onPressStart()
                        val startX = down.position.x
                        val startY = down.position.y
                        var armed = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.first()
                            val dx = change.position.x - startX
                            val dy = change.position.y - startY
                            val rightUpTarget = dx > diagonalCancelX && dy < -diagonalCancelY
                            val nearRightUpTarget = dx > diagonalCancelX / 2 && dy < -forgivingCancelY
                            val upwardFallback = dy < -upwardFallbackY
                            val nextArmed = rightUpTarget || nearRightUpTarget || upwardFallback
                            if (nextArmed != armed) {
                                armed = nextArmed
                                onCancelMove(armed)
                            }
                            if (!change.pressed) {
                                if (armed) {
                                    onCancel()
                                } else {
                                    onPressEnd()
                                }
                                break
                            }
                        }
                    }
                },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                if (listening) {
                    MiniWaveform(color = Color.White)
                } else {
                    MicGlyph(color = if (enabled) Color.White else BottomMuted)
                }
            }
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

private fun AgentProposal.toEditableDraft(): EditableProposalDraft {
    schedulePatch?.let { patch ->
        return EditableProposalDraft(
            title = patch.title,
            date = patch.start.take(10),
            startTime = patch.start.safeTime(),
            endTime = patch.end.safeTime(),
            due = "",
            notes = patch.notes,
            priority = "",
        )
    }
    todoPatch?.let { patch ->
        return EditableProposalDraft(
            title = patch.title,
            date = "",
            startTime = "",
            endTime = "",
            due = patch.due,
            notes = patch.notes,
            priority = patch.priority,
        )
    }
    return EditableProposalDraft(
        title = title,
        date = "",
        startTime = "",
        endTime = "",
        due = "",
        notes = "",
        priority = "medium",
    )
}

private fun String.safeTime(): String {
    return if (length >= 16 && this[10] == 'T') substring(11, 16) else "09:00"
}

private fun String.safeOffset(): String {
    return when {
        length >= 25 && (this[19] == '+' || this[19] == '-') -> substring(19)
        endsWith("Z") -> "+00:00"
        else -> "+08:00"
    }
}

private fun String.normalizedClock(fallback: String): String {
    val trimmed = trim()
    val match = Regex("""^(\d{1,2})(?::(\d{1,2}))?(?::\d{1,2})?$""").matchEntire(trimmed)
        ?: return fallback
    val hour = match.groupValues[1].toIntOrNull()?.coerceIn(0, 23) ?: return fallback
    val minute = match.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() }?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return "%02d:%02d".format(hour, minute)
}

private fun List<ScheduleEvent>.filterForDate(date: LocalDate): List<ScheduleEvent> {
    val key = date.toString()
    return filter { event -> event.date.isBlank() || event.date == key }
}

private fun List<TodoItem>.filterForDueDate(date: LocalDate): List<TodoItem> {
    val key = date.toString()
    return filter { todo -> todo.dueDate.isBlank() || todo.dueDate == key }
}

private fun List<ScheduleEvent>.filterAfter(date: LocalDate): List<ScheduleEvent> {
    return filter { event ->
        runCatching { LocalDate.parse(event.date).isAfter(date) }.getOrDefault(false)
    }
}

private fun ScheduleEvent.relativeDateLabel(): String {
    val eventDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return ""
    val today = LocalDate.now()
    return when (eventDate) {
        today.plusDays(1) -> "明天"
        today.plusDays(2) -> "后天"
        else -> "${eventDate.monthValue}/${eventDate.dayOfMonth}"
    }
}
