package com.zpc.fucktheddl.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.zpc.fucktheddl.BuildConfig
import com.zpc.fucktheddl.agent.AgentApiClient
import com.zpc.fucktheddl.agent.AgentApplyResult
import com.zpc.fucktheddl.agent.AgentCommitmentsPayload
import com.zpc.fucktheddl.agent.AgentConnectionSettings
import com.zpc.fucktheddl.agent.AgentProposal
import com.zpc.fucktheddl.agent.AgentProposalCandidate
import com.zpc.fucktheddl.agent.AgentSchedulePatch
import com.zpc.fucktheddl.agent.AgentTodoPatch
import com.zpc.fucktheddl.agent.ProposalPresentation
import com.zpc.fucktheddl.agent.createScheduleProposal
import com.zpc.fucktheddl.agent.createTodoProposal
import com.zpc.fucktheddl.agent.mapCommitmentsToScheduleState
import com.zpc.fucktheddl.agent.presentation
import com.zpc.fucktheddl.agent.toScheduleUpdateProposal
import com.zpc.fucktheddl.agent.toTodoUpdateProposal
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

enum class AppThemeMode(
    val storageKey: String,
    val label: String,
    val description: String,
) {
    ClassicLight("classic_light", "经典浅色", "当前主题"),
    Dark("dark", "深色", "纯黑深色"),
    FogBlue("fog_blue", "雾蓝", "更冷静的浅色");

    companion object {
        fun fromStorage(storageKey: String?): AppThemeMode {
            return values().firstOrNull { it.storageKey == storageKey } ?: ClassicLight
        }
    }
}

private data class AppColors(
    val ink: Color,
    val inkSoft: Color,
    val muted: Color,
    val divider: Color,
    val canvas: Color,
    val panel: Color,
    val accent: Color,
    val accentSoft: Color,
    val risk: Color,
    val riskSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val success: Color,
    val successSoft: Color,
    val bottomInk: Color,
    val bottomMuted: Color,
    val bottomBar: Color,
    val voiceIdle: Color,
    val voiceDisabled: Color,
    val voiceGlyph: Color,
    val voiceDisabledGlyph: Color,
)

private val ClassicLightColors = AppColors(
    ink = Color(0xFF14282E),
    inkSoft = Color(0xFF355158),
    muted = Color(0xFF75858A),
    divider = Color(0xFFE3E8EA),
    canvas = Color(0xFFFFFFFF),
    panel = Color(0xFFFFFFFF),
    accent = Color(0xFF2F7D8C),
    accentSoft = Color(0xFFEAF3F5),
    risk = Color(0xFFD88A3D),
    riskSoft = Color(0xFFFFF5E9),
    danger = Color(0xFFC94F4F),
    dangerSoft = Color(0xFFFFECEB),
    success = Color(0xFF6FA58B),
    successSoft = Color(0xFFEFF6F2),
    bottomInk = Color(0xFF111111),
    bottomMuted = Color(0xFF8A8A8E),
    bottomBar = Color(0xFFFFFFFF),
    voiceIdle = Color(0xFF111111),
    voiceDisabled = Color(0xFFE8E8EA),
    voiceGlyph = Color(0xFFFFFFFF),
    voiceDisabledGlyph = Color(0xFF8A8A8E),
)

private val DarkColors = AppColors(
    ink = Color(0xFFF5F5F7),
    inkSoft = Color(0xFFD1D1D6),
    muted = Color(0xFF8E8E93),
    divider = Color(0xFF2C2C2E),
    canvas = Color(0xFF000000),
    panel = Color(0xFF1C1C1E),
    accent = Color(0xFFF5F5F7),
    accentSoft = Color(0xFF2C2C2E),
    risk = Color(0xFF8E8E93),
    riskSoft = Color(0xFF1C1C1E),
    danger = Color(0xFF8E8E93),
    dangerSoft = Color(0xFF2C2C2E),
    success = Color(0xFFC7C7CC),
    successSoft = Color(0xFF2C2C2E),
    bottomInk = Color(0xFFF5F5F7),
    bottomMuted = Color(0xFF8E8E93),
    bottomBar = Color(0xFF000000),
    voiceIdle = Color(0xFFF5F5F7),
    voiceDisabled = Color(0xFF2C2C2E),
    voiceGlyph = Color(0xFF000000),
    voiceDisabledGlyph = Color(0xFF8E8E93),
)

private val FogBlueColors = AppColors(
    ink = Color(0xFF172332),
    inkSoft = Color(0xFF41556A),
    muted = Color(0xFF718197),
    divider = Color(0xFFDFE7EE),
    canvas = Color(0xFFF7F9FB),
    panel = Color(0xFFFFFFFF),
    accent = Color(0xFF5F7E9B),
    accentSoft = Color(0xFFEDF3F8),
    risk = Color(0xFFC58B47),
    riskSoft = Color(0xFFFFF4E6),
    danger = Color(0xFFC85D65),
    dangerSoft = Color(0xFFFFEDEF),
    success = Color(0xFF6C9F91),
    successSoft = Color(0xFFEEF7F4),
    bottomInk = Color(0xFF152233),
    bottomMuted = Color(0xFF718197),
    bottomBar = Color(0xFFFAFCFE),
    voiceIdle = Color(0xFF152233),
    voiceDisabled = Color(0xFFE7EEF4),
    voiceGlyph = Color(0xFFFFFFFF),
    voiceDisabledGlyph = Color(0xFF718197),
)

private val LocalAppColors = staticCompositionLocalOf { ClassicLightColors }

private fun AppThemeMode.colors(): AppColors = when (this) {
    AppThemeMode.ClassicLight -> ClassicLightColors
    AppThemeMode.Dark -> DarkColors
    AppThemeMode.FogBlue -> FogBlueColors
}

private val Ink: Color
    @Composable get() = LocalAppColors.current.ink
private val InkSoft: Color
    @Composable get() = LocalAppColors.current.inkSoft
private val Muted: Color
    @Composable get() = LocalAppColors.current.muted
private val Divider: Color
    @Composable get() = LocalAppColors.current.divider
private val Canvas: Color
    @Composable get() = LocalAppColors.current.canvas
private val Panel: Color
    @Composable get() = LocalAppColors.current.panel
private val Accent: Color
    @Composable get() = LocalAppColors.current.accent
private val AccentSoft: Color
    @Composable get() = LocalAppColors.current.accentSoft
private val Risk: Color
    @Composable get() = LocalAppColors.current.risk
private val RiskSoft: Color
    @Composable get() = LocalAppColors.current.riskSoft
private val Danger: Color
    @Composable get() = LocalAppColors.current.danger
private val DangerSoft: Color
    @Composable get() = LocalAppColors.current.dangerSoft
private val Success: Color
    @Composable get() = LocalAppColors.current.success
private val SuccessSoft: Color
    @Composable get() = LocalAppColors.current.successSoft
private val BottomInk: Color
    @Composable get() = LocalAppColors.current.bottomInk
private val BottomMuted: Color
    @Composable get() = LocalAppColors.current.bottomMuted
private val BottomBar: Color
    @Composable get() = LocalAppColors.current.bottomBar
private val VoiceIdle: Color
    @Composable get() = LocalAppColors.current.voiceIdle
private val VoiceDisabled: Color
    @Composable get() = LocalAppColors.current.voiceDisabled
private val VoiceGlyph: Color
    @Composable get() = LocalAppColors.current.voiceGlyph
private val VoiceDisabledGlyph: Color
    @Composable get() = LocalAppColors.current.voiceDisabledGlyph

private fun readableOn(color: Color): Color {
    return if (color.luminance() > 0.55f) Color.Black else Color.White
}

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

private enum class SettingsPanel {
    Root,
    User,
    Connection,
    Theme,
}

private enum class CreateCommitmentKind {
    Schedule,
    Todo,
}

private enum class ConnectionIndicator {
    Checking,
    Connected,
    NotConnected,
    Failed,
}

private data class BackendConnectionState(
    val indicator: ConnectionIndicator = ConnectionIndicator.NotConnected,
    val label: String = "未连接",
    val detail: String = "",
)

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

private sealed interface CommitmentEditTarget {
    data class Schedule(val event: ScheduleEvent) : CommitmentEditTarget
    data class Todo(val todo: TodoItem) : CommitmentEditTarget
}

private data class CommitmentEditMenuRequest(
    val target: CommitmentEditTarget,
    val anchor: Offset,
)

private typealias CommitmentEditRequester = (CommitmentEditTarget, Offset) -> Unit

private fun Modifier.commitmentLongPressMenu(
    target: CommitmentEditTarget?,
    onEdit: CommitmentEditRequester,
    onTap: () -> Unit = {},
    onBeforeEdit: () -> Unit = {},
): Modifier = composed {
    var origin by remember(target) { mutableStateOf(Offset.Zero) }
    onGloballyPositioned { origin = it.positionInRoot() }
        .pointerInput(target) {
            detectTapGestures(
                onLongPress = { pressOffset ->
                    target?.let {
                        onBeforeEdit()
                        onEdit(it, origin + pressOffset)
                    }
                },
                onTap = { onTap() },
            )
        }
}

@Composable
fun FuckTheDdlApp(
    initialState: ScheduleShellState,
    connectionSettings: AgentConnectionSettings,
    themeMode: AppThemeMode = AppThemeMode.ClassicLight,
    agentApiClient: AgentApiClient? = null,
    asrClient: RealtimeAsrClient? = null,
    commitmentsProvider: () -> AgentCommitmentsPayload = { AgentCommitmentsPayload(emptyList(), emptyList()) },
    proposalApplier: (AgentProposal) -> AgentApplyResult = { AgentApplyResult("failed", "", "本地存储不可用") },
    commitmentDeleter: (String) -> AgentApplyResult = { AgentApplyResult("failed", "", "本地存储不可用") },
    userEmail: String = "",
    onConnectionSettingsSaved: (AgentConnectionSettings) -> Unit = {},
    onThemeModeChanged: (AppThemeMode) -> Unit = {},
) {
    var shellState by remember { mutableStateOf(initialState) }
    val todayTab = remember(shellState.tabs) {
        shellState.tabs.firstOrNull { it.destination == TabDestination.Today }
            ?: ScheduleTab(label = "日程", destination = TabDestination.Today)
    }
    val todoTab = remember(shellState.tabs) {
        shellState.tabs.firstOrNull { it.destination == TabDestination.Todo }
            ?: ScheduleTab(label = "待办", destination = TabDestination.Todo)
    }
    var selectedTab by remember { mutableStateOf(if (initialState.selectedTab.destination == TabDestination.Todo) todoTab else todayTab) }
    var showingCalendar by remember { mutableStateOf(false) }
    var showingSettings by remember { mutableStateOf(false) }
    var activeEditMenu by remember { mutableStateOf<CommitmentEditMenuRequest?>(null) }
    var activeEditTarget by remember { mutableStateOf<CommitmentEditTarget?>(null) }
    var createMenuVisible by remember { mutableStateOf(false) }
    var activeCreateKind by remember { mutableStateOf<CreateCommitmentKind?>(null) }
    var voiceRecording by remember { mutableStateOf(false) }
    var backendConnectionState by remember { mutableStateOf(BackendConnectionState()) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun testBackendConnection(settings: AgentConnectionSettings) {
        backendConnectionState = BackendConnectionState(
            indicator = ConnectionIndicator.Checking,
            label = "检测中",
        )
        Thread {
            val result = AgentApiClient(settings.toConfig()).testService(settings)
            mainHandler.post {
                backendConnectionState = BackendConnectionState(
                    indicator = if (result.healthy) ConnectionIndicator.Connected else ConnectionIndicator.Failed,
                    label = result.label,
                    detail = result.detail,
                )
            }
        }.start()
    }

    fun refreshCommitments() {
        Thread {
            runCatching {
                mapCommitmentsToScheduleState(commitmentsProvider())
            }.onSuccess { commitments ->
                mainHandler.post {
                    shellState = shellState.copy(
                        events = commitments.events,
                        todos = commitments.todos,
                    )
                }
            }.onFailure { error ->
                mainHandler.post {
                    backendConnectionState = BackendConnectionState(
                        indicator = ConnectionIndicator.Failed,
                        label = error.message?.takeIf { it.isNotBlank() } ?: "本地读取失败",
                    )
                }
            }
        }.start()
    }

    fun deleteCommitment(commitmentId: String) {
        if (commitmentId.isBlank()) {
            backendConnectionState = BackendConnectionState(
                indicator = ConnectionIndicator.Failed,
                label = "无法删除",
            )
            return
        }
        Thread {
            val result = commitmentDeleter(commitmentId)
            mainHandler.post {
                if (result.error == null) {
                    refreshCommitments()
                } else {
                    backendConnectionState = BackendConnectionState(
                        indicator = ConnectionIndicator.Failed,
                        label = result.error,
                    )
                }
            }
        }.start()
    }

    fun updateCommitment(proposal: AgentProposal) {
        Thread {
            val result = proposalApplier(proposal)
            mainHandler.post {
                if (result.error == null) {
                    refreshCommitments()
                } else {
                    backendConnectionState = BackendConnectionState(
                        indicator = ConnectionIndicator.Failed,
                        label = result.error,
                    )
                }
            }
        }.start()
    }

    LaunchedEffect(Unit) {
        refreshCommitments()
        testBackendConnection(connectionSettings)
    }

    CompositionLocalProvider(LocalAppColors provides themeMode.colors()) {
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
                    connectionSettings = connectionSettings,
                    commitmentsProvider = commitmentsProvider,
                    proposalApplier = proposalApplier,
                    onCommitmentsChanged = ::refreshCommitments,
                    onVoiceRecordingChanged = { voiceRecording = it },
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(if (activeEditTarget != null) 18.dp else 0.dp)
                        .padding(innerPadding)
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    CompactHeader(
                        connectionState = backendConnectionState,
                        onDateClick = {
                            selectedTab = todayTab
                            showingCalendar = true
                        },
                        onSettingsClick = { showingSettings = true },
                    )
                    when {
                        showingCalendar -> CalendarSurface(
                            events = shellState.events,
                            todos = shellState.todos,
                            onDeleteCommitment = ::deleteCommitment,
                            onEditCommitment = { target, anchor ->
                                activeEditMenu = CommitmentEditMenuRequest(target, anchor)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        )

                        selectedTab.destination == TabDestination.Today -> TodayTimeline(
                            events = shellState.events,
                            todos = shellState.todos,
                            onDeleteCommitment = ::deleteCommitment,
                            onEditCommitment = { target, anchor ->
                                activeEditMenu = CommitmentEditMenuRequest(target, anchor)
                            },
                            modifier = Modifier.weight(1f),
                        )

                        selectedTab.destination == TabDestination.Todo -> TodoSurface(
                            todos = shellState.todos,
                            onDeleteCommitment = ::deleteCommitment,
                            onEditCommitment = { target, anchor ->
                                activeEditMenu = CommitmentEditMenuRequest(target, anchor)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                        )

                        else -> TodayTimeline(
                            events = shellState.events,
                            todos = shellState.todos,
                            onDeleteCommitment = ::deleteCommitment,
                            onEditCommitment = { target, anchor ->
                                activeEditMenu = CommitmentEditMenuRequest(target, anchor)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                activeEditMenu?.let { request ->
                    RootEditBubbleOverlay(
                        request = request,
                        onDismiss = { activeEditMenu = null },
                        onEdit = {
                            activeEditMenu = null
                            activeEditTarget = request.target
                        },
                    )
                }
                activeEditTarget?.let { target ->
                    CommitmentEditOverlay(
                        target = target,
                        onDismiss = { activeEditTarget = null },
                        onSave = { proposal ->
                            activeEditTarget = null
                            updateCommitment(proposal)
                        },
                    )
                }
                if (!voiceRecording && !showingSettings && activeEditMenu == null && activeEditTarget == null && activeCreateKind == null) {
                    GlobalCreateFab(
                        menuVisible = createMenuVisible,
                        onToggle = { createMenuVisible = !createMenuVisible },
                        onDismissMenu = { createMenuVisible = false },
                        onCreateSchedule = {
                            createMenuVisible = false
                            activeCreateKind = CreateCommitmentKind.Schedule
                        },
                        onCreateTodo = {
                            createMenuVisible = false
                            activeCreateKind = CreateCommitmentKind.Todo
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 18.dp, bottom = 132.dp),
                    )
                }
                activeCreateKind?.let { kind ->
                    CreateCommitmentOverlay(
                        kind = kind,
                        onDismiss = { activeCreateKind = null },
                        onSave = { proposal ->
                            activeCreateKind = null
                            updateCommitment(proposal)
                        },
                    )
                }
            }
            if (showingSettings) {
                ConnectionSettingsOverlay(
                    settings = connectionSettings,
                    connectionState = backendConnectionState,
                    themeMode = themeMode,
                    userEmail = userEmail.ifBlank { connectionSettings.userEmail },
                    onThemeModeChanged = onThemeModeChanged,
                    onTestConnection = ::testBackendConnection,
                    onSave = { settings ->
                        val shouldRetest = settings.serviceTestKey() != connectionSettings.serviceTestKey()
                        onConnectionSettingsSaved(settings)
                        if (shouldRetest) {
                            testBackendConnection(settings)
                        }
                        showingSettings = false
                    },
                    onClose = { showingSettings = false },
                )
            }
        }
    }
}

@Composable
private fun CompactHeader(
    connectionState: BackendConnectionState,
    onDateClick: () -> Unit,
    onSettingsClick: () -> Unit,
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
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusPill(
                state = connectionState,
            )
            SettingsButton(onClick = onSettingsClick)
        }
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit) {
    Surface(
        color = AccentSoft,
        shape = RoundedCornerShape(999.dp),
        modifier = Modifier
            .size(38.dp)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "⚙",
                color = Accent,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun StatusPill(
    state: BackendConnectionState,
) {
    val color = state.indicator.color()
    val background = when (state.indicator) {
        ConnectionIndicator.Connected -> SuccessSoft
        ConnectionIndicator.Failed -> DangerSoft
        ConnectionIndicator.Checking,
        ConnectionIndicator.NotConnected -> AccentSoft
    }
    Surface(
        color = background,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = state.label,
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
private fun ConnectionSettingsOverlay(
    settings: AgentConnectionSettings,
    connectionState: BackendConnectionState,
    themeMode: AppThemeMode,
    userEmail: String,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onTestConnection: (AgentConnectionSettings) -> Unit,
    onSave: (AgentConnectionSettings) -> Unit,
    onClose: () -> Unit,
) {
    var panel by remember { mutableStateOf(SettingsPanel.Root) }
    var baseUrl by remember(settings) { mutableStateOf(settings.baseUrl) }
    var deepseekApiKey by remember(settings) { mutableStateOf(settings.deepseekApiKey) }
    var deepseekBaseUrl by remember(settings) { mutableStateOf(settings.deepseekBaseUrl) }
    var deepseekModel by remember(settings) { mutableStateOf(settings.deepseekModel) }
    val normalizedBaseUrl = baseUrl.trim()
    val urlValid = normalizedBaseUrl.startsWith("http://") || normalizedBaseUrl.startsWith("https://")
    val settingsToTest = AgentConnectionSettings(
        baseUrl = normalizedBaseUrl,
        accessToken = settings.accessToken.trim(),
        userEmail = userEmail.trim(),
        deepseekApiKey = deepseekApiKey.trim(),
        deepseekBaseUrl = deepseekBaseUrl.trim().ifBlank { "https://api.deepseek.com/v1" },
        deepseekModel = deepseekModel.trim().ifBlank { "deepseek-v4-flash" },
    )
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(
            focusable = true,
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
        ),
        onDismissRequest = onClose,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas.copy(alpha = 0.94f))
                .pointerInput(panel) {
                    detectTapGestures(onTap = {
                        if (panel == SettingsPanel.Root) onClose()
                        else panel = SettingsPanel.Root
                    })
                }
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = Panel,
                shape = RoundedCornerShape(28.dp),
                shadowElevation = 20.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Divider, RoundedCornerShape(28.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { /* consume */ })
                    },
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (panel) {
                        SettingsPanel.Root -> SettingsRootMenu(
                            connectionState = connectionState,
                            themeMode = themeMode,
                            userEmail = userEmail,
                            userBound = userEmail.isNotBlank(),
                            onUserClick = { panel = SettingsPanel.User },
                            onConnectionClick = { panel = SettingsPanel.Connection },
                            onThemeClick = { panel = SettingsPanel.Theme },
                            onClose = onClose,
                        )

                        SettingsPanel.User -> UserSettingsMenu(
                            email = userEmail,
                            onBack = { panel = SettingsPanel.Root },
                                )

                        SettingsPanel.Connection -> ConnectionSettingsMenu(
                            baseUrl = baseUrl,
                            onBaseUrlChange = { baseUrl = it },
                            deepseekApiKey = deepseekApiKey,
                            onDeepseekApiKeyChange = { deepseekApiKey = it },
                            deepseekBaseUrl = deepseekBaseUrl,
                            onDeepseekBaseUrlChange = { deepseekBaseUrl = it },
                            deepseekModel = deepseekModel,
                            onDeepseekModelChange = { deepseekModel = it },
                            urlValid = urlValid,
                            connectionState = connectionState,
                            onBack = { panel = SettingsPanel.Root },
                            onTest = { onTestConnection(settingsToTest) },
                            onSave = {
                                onSave(settingsToTest)
                            },
                        )

                        SettingsPanel.Theme -> ThemeSettingsMenu(
                            themeMode = themeMode,
                            onThemeModeChanged = onThemeModeChanged,
                            onBack = { panel = SettingsPanel.Root },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsRootMenu(
    connectionState: BackendConnectionState,
    themeMode: AppThemeMode,
    userEmail: String,
    userBound: Boolean,
    onUserClick: () -> Unit,
    onConnectionClick: () -> Unit,
    onThemeClick: () -> Unit,
    onClose: () -> Unit,
) {
    SettingsHeader(title = "设置")
    SettingsMenuRow(
        title = "用户",
        detail = userEmail.ifBlank { "未绑定" },
        indicator = if (userBound) ConnectionIndicator.Connected else ConnectionIndicator.NotConnected,
        onClick = onUserClick,
    )
    SettingsMenuRow(
        title = "连接",
        detail = connectionState.label,
        indicator = connectionState.indicator,
        onClick = onConnectionClick,
    )
    SettingsMenuRow(
        title = "主题色",
        detail = themeMode.label,
        swatch = themeMode.swatchColor(),
        onClick = onThemeClick,
    )
    SettingsGroupTitle("关于")
    SettingsInfoRow(
        label = "版本",
        value = BuildConfig.VERSION_NAME,
        leadingColor = Ink,
    )
    Button(
        onClick = onClose,
        colors = ButtonDefaults.buttonColors(containerColor = AccentSoft),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text("关闭", color = Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConnectionSettingsMenu(
    baseUrl: String,
    onBaseUrlChange: (String) -> Unit,
    deepseekApiKey: String,
    onDeepseekApiKeyChange: (String) -> Unit,
    deepseekBaseUrl: String,
    onDeepseekBaseUrlChange: (String) -> Unit,
    deepseekModel: String,
    onDeepseekModelChange: (String) -> Unit,
    urlValid: Boolean,
    connectionState: BackendConnectionState,
    onBack: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    SettingsHeader(title = "连接", onBack = onBack)
    OutlinedTextField(
        value = baseUrl,
        onValueChange = onBaseUrlChange,
        label = { Text("后端 URL", fontSize = 12.sp) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = appTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = deepseekApiKey,
        onValueChange = onDeepseekApiKeyChange,
        label = { Text("DeepSeek API Key", fontSize = 12.sp) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = appTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = deepseekBaseUrl,
            onValueChange = onDeepseekBaseUrlChange,
            label = { Text("DeepSeek URL", fontSize = 12.sp) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = appTextFieldColors(),
            modifier = Modifier.weight(1.35f),
        )
        OutlinedTextField(
            value = deepseekModel,
            onValueChange = onDeepseekModelChange,
            label = { Text("模型", fontSize = 12.sp) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = appTextFieldColors(),
            modifier = Modifier.weight(1f),
        )
    }
    if (!urlValid) {
        Text(
            text = "URL 需要以 http:// 或 https:// 开头",
            color = Danger,
            fontSize = 12.sp,
        )
    }
    Button(
        onClick = onTest,
        enabled = urlValid && connectionState.indicator != ConnectionIndicator.Checking,
        colors = ButtonDefaults.buttonColors(
            containerColor = AccentSoft,
            disabledContainerColor = AccentSoft.copy(alpha = 0.52f),
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
    ) {
        Text(
            text = if (connectionState.indicator == ConnectionIndicator.Checking) "测试中..." else "测试服务",
            color = Accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    ConnectionTestResult(connectionState = connectionState)
    Button(
        onClick = onSave,
        enabled = urlValid,
        colors = ButtonDefaults.buttonColors(containerColor = Ink),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
    ) {
        Text("保存", color = readableOn(Ink), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun UserSettingsMenu(
    email: String,
    onBack: () -> Unit,
) {
    SettingsHeader(title = "用户", onBack = onBack)
    Text(
        text = "当前邮箱",
        color = Muted,
        fontSize = 13.sp,
        lineHeight = 19.sp,
    )
    SettingsInfoRow(
        label = "邮箱",
        value = email.ifBlank { "未登录" },
        leadingColor = Success,
    )
}

@Composable
private fun ThemeSettingsMenu(
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onBack: () -> Unit,
) {
    SettingsHeader(title = "主题色", onBack = onBack)
    ThemeOptionRow(
        name = AppThemeMode.ClassicLight.label,
        description = AppThemeMode.ClassicLight.description,
        color = AppThemeMode.ClassicLight.swatchColor(),
        selected = themeMode == AppThemeMode.ClassicLight,
        onClick = { onThemeModeChanged(AppThemeMode.ClassicLight) },
    )
    ThemeOptionRow(
        name = AppThemeMode.Dark.label,
        description = AppThemeMode.Dark.description,
        color = AppThemeMode.Dark.swatchColor(),
        selected = themeMode == AppThemeMode.Dark,
        onClick = { onThemeModeChanged(AppThemeMode.Dark) },
    )
    ThemeOptionRow(
        name = AppThemeMode.FogBlue.label,
        description = AppThemeMode.FogBlue.description,
        color = AppThemeMode.FogBlue.swatchColor(),
        selected = themeMode == AppThemeMode.FogBlue,
        onClick = { onThemeModeChanged(AppThemeMode.FogBlue) },
    )
}

@Composable
private fun SettingsHeader(
    title: String,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Surface(
                color = AccentSoft,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .size(36.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onBack() })
                    },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(text = "‹", color = Accent, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(
            text = title,
            color = Ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsMenuRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    indicator: ConnectionIndicator? = null,
    swatch: Color? = null,
) {
    Surface(
        color = AccentSoft.copy(alpha = 0.42f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(18.dp))
            .pointerInput(title) {
                detectTapGestures(onTap = { onClick() })
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                indicator != null -> ConnectionStatusDot(indicator)
                swatch != null -> Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(swatch, RoundedCornerShape(999.dp)),
                )
            }
            Text(
                text = title,
                color = Ink,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = detail,
                color = Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = "›", color = Muted, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ConnectionStatusDot(indicator: ConnectionIndicator) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .background(indicator.color(), RoundedCornerShape(999.dp)),
    )
}

@Composable
private fun ConnectionTestResult(
    connectionState: BackendConnectionState,
) {
    val statusColor = connectionState.indicator.color()
    val statusBackground = when (connectionState.indicator) {
        ConnectionIndicator.Connected -> SuccessSoft
        ConnectionIndicator.Failed -> DangerSoft
        ConnectionIndicator.Checking,
        ConnectionIndicator.NotConnected -> AccentSoft
    }
    Surface(
        color = statusBackground,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = connectionState.label,
                color = statusColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (connectionState.detail.isNotBlank()) {
                Text(
                    text = connectionState.detail,
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ConnectionIndicator.color(): Color = when (this) {
    ConnectionIndicator.Checking -> Accent
    ConnectionIndicator.Connected -> Success
    ConnectionIndicator.NotConnected -> BottomMuted
    ConnectionIndicator.Failed -> Danger
}

private fun ConnectionIndicator.label(): String = when (this) {
    ConnectionIndicator.Checking -> "检测中"
    ConnectionIndicator.Connected -> "已连接"
    ConnectionIndicator.NotConnected -> "未连接"
    ConnectionIndicator.Failed -> "连接失败"
}

private fun AgentConnectionSettings.serviceTestKey(): String {
    return listOf(
        baseUrl.trim(),
        accessToken.trim(),
        deepseekApiKey.trim(),
        deepseekBaseUrl.trim(),
        deepseekModel.trim(),
    ).joinToString(separator = "\u0000")
}

private fun AppThemeMode.swatchColor(): Color = when (this) {
    AppThemeMode.ClassicLight -> Color(0xFF2F7D8C)
    AppThemeMode.Dark -> Color(0xFF000000)
    AppThemeMode.FogBlue -> Color(0xFF5F7E9B)
}

@Composable
private fun SettingsGroupTitle(text: String) {
    Text(
        text = text,
        color = Ink,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ThemeOptionRow(
    name: String,
    description: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (selected) AccentSoft else Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (selected) Accent else Divider,
                shape = RoundedCornerShape(16.dp),
            )
            .pointerInput(name) {
                detectTapGestures(onTap = { onClick() })
            },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(color, RoundedCornerShape(999.dp)),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(text = name, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(text = description, color = Muted, fontSize = 12.sp)
            }
            Text(
                text = if (selected) "已选" else "",
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun SettingsInfoRow(
    label: String,
    value: String,
    leadingColor: Color,
) {
    Surface(
        color = AccentSoft.copy(alpha = 0.42f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(leadingColor, RoundedCornerShape(999.dp)),
            )
            Text(
                text = label,
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value,
                color = Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TodayTimeline(
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
    onDeleteCommitment: (String) -> Unit,
    onEditCommitment: CommitmentEditRequester,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val todayEvents = events
        .filterForDate(today)
        .sortedBy { event -> event.timeRange }
    val upcomingEvents = events
        .filterAfter(today)
        .sortedWith(compareBy<ScheduleEvent> { it.date.ifBlank { today.toString() } }.thenBy { it.timeRange })
    val pendingTodos = todos.filterNot { it.done }
    val homeTodos = pendingTodos
        .sortedByDueDate()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        HomeScheduleSection(
            todayEvents = todayEvents,
            upcomingEvents = upcomingEvents,
            onDeleteCommitment = onDeleteCommitment,
            onEditCommitment = onEditCommitment,
            modifier = Modifier.weight(1f),
        )
        HomeTodoTimelineSection(
            todos = homeTodos,
            onDeleteCommitment = onDeleteCommitment,
            onEditCommitment = onEditCommitment,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeScheduleSection(
    todayEvents: List<ScheduleEvent>,
    upcomingEvents: List<ScheduleEvent>,
    onDeleteCommitment: (String) -> Unit,
    onEditCommitment: CommitmentEditRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeSectionLabel(
            title = "日程",
            caption = if (todayEvents.isEmpty()) "今天" else "今天 ${todayEvents.size}",
        )
        Surface(
            color = Panel,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Divider, RoundedCornerShape(18.dp)),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                if (todayEvents.isEmpty()) {
                    QuietTimelineEmpty(text = "今天没有日程")
                } else {
                    todayEvents.forEachIndexed { index, event ->
                        TimelineEventRow(
                            event = event,
                            showConnector = index != todayEvents.lastIndex,
                            onDelete = { onDeleteCommitment(event.id) },
                            onEdit = onEditCommitment,
                        )
                    }
                }
                if (upcomingEvents.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .height(1.dp)
                            .background(Divider),
                    )
                    upcomingEvents.forEach { event ->
                        UpcomingEventRow(
                            event = event,
                            onDelete = { onDeleteCommitment(event.id) },
                            onEdit = onEditCommitment,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeTodoTimelineSection(
    todos: List<TodoItem>,
    onDeleteCommitment: (String) -> Unit,
    onEditCommitment: CommitmentEditRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeSectionLabel(
            title = "待办",
            caption = if (todos.isEmpty()) "" else "${todos.size}",
        )
        Surface(
            color = AccentSoft.copy(alpha = 0.42f),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Divider, RoundedCornerShape(18.dp)),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
            ) {
                if (todos.isEmpty()) {
                    QuietTimelineEmpty(text = "没有待办")
                } else {
                    todos.forEachIndexed { index, todo ->
                        HomeTodoTimelineRow(
                            todo = todo,
                            showConnector = index != todos.lastIndex,
                            onDelete = { onDeleteCommitment(todo.id) },
                            onEdit = onEditCommitment,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeSectionLabel(
    title: String,
    caption: String,
) {
    Row(
        modifier = Modifier.padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = Ink,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        if (caption.isNotBlank()) {
            Text(text = caption, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun QuietTimelineEmpty(text: String) {
    Text(
        text = text,
        color = Muted,
        fontSize = 14.sp,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
    )
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
    onEdit: CommitmentEditRequester,
) {
    var confirmingDelete by remember(event.id) { mutableStateOf(false) }
    var expanded by remember(event.id, event.detail) { mutableStateOf(false) }
    val hasNote = event.detail.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 240))
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
        Column(
            modifier = Modifier
                .weight(1f)
                .commitmentLongPressMenu(
                    target = event.editTargetOrNull(),
                    onEdit = onEdit,
                    onTap = { if (hasNote) expanded = !expanded },
                ),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
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
                if (hasNote) {
                    Text(text = if (expanded) "收起" else "备注", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            ExpandableNoteText(note = event.detail, expanded = expanded)
        }
        if (event.id.isNotBlank()) {
            ConfirmableMiniDeleteButton(
                confirming = confirmingDelete,
                onRequestConfirm = { confirmingDelete = true },
                onConfirm = {
                    confirmingDelete = false
                    onDelete()
                },
                onCancel = { confirmingDelete = false },
            )
        }
    }
}

@Composable
private fun HomeTodoTimelineRow(
    todo: TodoItem,
    showConnector: Boolean,
    onDelete: () -> Unit,
    onEdit: CommitmentEditRequester,
) {
    val color = todo.priority.color()
    var expanded by remember(todo.stableUiKey(), todo.detail) { mutableStateOf(false) }
    val hasNote = todo.detail.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 240))
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(14.dp)) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(9.dp)
                    .background(Color(0xFFCADADD), RoundedCornerShape(999.dp)),
            )
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .border(2.dp, color, RoundedCornerShape(4.dp)),
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(if (showConnector) 22.dp else 9.dp)
                    .background(Color(0xFFCADADD), RoundedCornerShape(999.dp)),
            )
        }
        Text(
            text = todo.relativeDueLabel(),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(52.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .commitmentLongPressMenu(
                    target = todo.editTargetOrNull(),
                    onEdit = onEdit,
                    onTap = { if (hasNote) expanded = !expanded },
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = todo.title,
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (hasNote) {
                    Text(text = if (expanded) "收起" else "备注", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            ExpandableNoteText(
                note = todo.detail,
                expanded = expanded,
                collapsedMaxLines = 1,
                fontSize = 12,
                lineHeight = 15,
            )
        }
        if (todo.id.isNotBlank()) {
            MiniDeleteButton(onClick = onDelete)
        }
    }
}

@Composable
private fun InlineScheduleEditor(
    title: String,
    date: String,
    startTime: String,
    endTime: String,
    notes: String,
    onTitleChange: (String) -> Unit,
    onDateChange: (String) -> Unit,
    onStartTimeChange: (String) -> Unit,
    onEndTimeChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.animateContentSize(animationSpec = tween(durationMillis = 220)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactEditField(value = title, onValueChange = onTitleChange, label = "标题")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DatePickerField(value = date, onDateChange = onDateChange, label = "日期", modifier = Modifier.weight(1.25f))
            TimePickerField(value = startTime, onTimeChange = onStartTimeChange, label = "开始", modifier = Modifier.weight(1f))
            TimePickerField(value = endTime, onTimeChange = onEndTimeChange, label = "结束", modifier = Modifier.weight(1f))
        }
        CompactEditField(value = notes, onValueChange = onNotesChange, label = "备注", singleLine = false)
        InlineEditActions(onSave = onSave, onCancel = onCancel)
    }
}

@Composable
private fun InlineTodoEditor(
    title: String,
    due: String,
    notes: String,
    priority: String,
    hasDeadline: Boolean,
    onTitleChange: (String) -> Unit,
    onDueChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onPriorityChange: (String) -> Unit,
    onHasDeadlineChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.animateContentSize(animationSpec = tween(durationMillis = 220)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CompactEditField(value = title, onValueChange = onTitleChange, label = "标题")
        DeadlinePicker(
            hasDeadline = hasDeadline,
            onHasDeadlineChange = onHasDeadlineChange,
            date = due,
            onDateChange = onDueChange,
        )
        PriorityPicker(priority = priority, onPriorityChange = onPriorityChange)
        CompactEditField(value = notes, onValueChange = onNotesChange, label = "备注", singleLine = false)
        InlineEditActions(onSave = onSave, onCancel = onCancel)
    }
}

@Composable
private fun PriorityPicker(
    priority: String,
    onPriorityChange: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("high" to "高", "medium" to "中", "low" to "低").forEach { (value, label) ->
            val selected = priority == value
            val color = value.priorityColor()
            Surface(
                color = if (selected) color else Panel,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .border(1.dp, if (selected) color else Divider, RoundedCornerShape(999.dp))
                    .pointerInput(value) {
                        detectTapGestures(onTap = { onPriorityChange(value) })
                    },
            ) {
                Text(
                    text = label,
                    color = if (selected) readableOn(color) else Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun InlineEditActions(
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = Panel,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .border(1.dp, Divider, RoundedCornerShape(999.dp))
                .pointerInput(Unit) { detectTapGestures(onTap = { onCancel() }) },
        ) {
            Text(
                text = "取消",
                color = Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        Surface(
            color = Ink,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { onSave() }) },
        ) {
            Text(
                text = "完成",
                color = readableOn(Ink),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(
    value: String,
    onDateChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    val displayText = remember(value) {
        if (value.isBlank()) null
        else try {
            val date = LocalDate.parse(value)
            val pattern = DateTimeFormatter.ofPattern("M月d日 (E)", Locale.CHINESE)
            date.format(pattern)
        } catch (_: Exception) { value }
    }
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = displayText ?: "",
            onValueChange = {},
            label = { Text(label, fontSize = 12.sp) },
            placeholder = { Text("选择日期", fontSize = 14.sp, color = Muted) },
            readOnly = true,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = appTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Text("▾", fontSize = 12.sp, color = Muted) },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { showDialog = true }) },
        )
    }

    if (showDialog) {
        val initialDate = remember(value) {
            try { LocalDate.parse(value) } catch (_: Exception) { LocalDate.now() }
        }
        val initialMillis = initialDate.atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        val pickerColors = DatePickerDefaults.colors(
            containerColor = Panel,
            titleContentColor = Ink,
            headlineContentColor = Ink,
            weekdayContentColor = Muted,
            subheadContentColor = Ink,
            navigationContentColor = Ink,
            dayContentColor = Ink,
            selectedDayContentColor = readableOn(Accent),
            selectedDayContainerColor = Accent,
            todayContentColor = Accent,
            todayDateBorderColor = Accent,
            dividerColor = Divider,
        )

        val isDark = Canvas.luminance() < 0.5f
        val dialogColorScheme = if (isDark) {
            darkColorScheme(
                surface = Panel,
                surfaceVariant = Panel,
                background = Canvas,
                onSurface = Ink,
                onSurfaceVariant = Muted,
                primary = Accent,
                onPrimary = readableOn(Accent),
                outline = Divider,
            )
        } else {
            lightColorScheme(
                surface = Panel,
                surfaceVariant = Panel,
                background = Canvas,
                onSurface = Ink,
                onSurfaceVariant = Muted,
                primary = Accent,
                onPrimary = readableOn(Accent),
                outline = Divider,
            )
        }

        MaterialTheme(colorScheme = dialogColorScheme) {
            DatePickerDialog(
                onDismissRequest = { showDialog = false },
                colors = pickerColors,
                confirmButton = {
                    TextAction(text = "确定", onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.of("Asia/Shanghai"))
                                .toLocalDate()
                            onDateChange(selectedDate.toString())
                        }
                        showDialog = false
                    })
                },
                dismissButton = { TextAction(text = "取消", onClick = { showDialog = false }) },
            ) {
                DatePicker(state = datePickerState, colors = pickerColors)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerField(
    value: String,
    onTimeChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    val (hour, minute) = remember(value) {
        val parts = value.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        h to m
    }

    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            label = { Text(label, fontSize = 12.sp) },
            readOnly = true,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = appTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Text("▾", fontSize = 12.sp, color = Muted) },
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) { detectTapGestures(onTap = { showDialog = true }) },
        )
    }

    if (showDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true,
        )

        val pickerColors = TimePickerDefaults.colors(
            containerColor = Panel,
            clockDialColor = Divider,
            clockDialSelectedContentColor = Ink,
            clockDialUnselectedContentColor = Muted,
            selectorColor = Accent,
            timeSelectorSelectedContainerColor = Accent,
            timeSelectorSelectedContentColor = readableOn(Accent),
            timeSelectorUnselectedContainerColor = Color.Transparent,
            timeSelectorUnselectedContentColor = Muted,
        )

        val isDark = Canvas.luminance() < 0.5f
        val dialogColorScheme = if (isDark) {
            darkColorScheme(
                surface = Panel,
                surfaceVariant = Panel,
                background = Canvas,
                onSurface = Ink,
                onSurfaceVariant = Muted,
                primary = Accent,
                onPrimary = readableOn(Accent),
                outline = Divider,
            )
        } else {
            lightColorScheme(
                surface = Panel,
                surfaceVariant = Panel,
                background = Canvas,
                onSurface = Ink,
                onSurfaceVariant = Muted,
                primary = Accent,
                onPrimary = readableOn(Accent),
                outline = Divider,
            )
        }

        MaterialTheme(colorScheme = dialogColorScheme) {
            TimePickerDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("选择时间", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink) },
                confirmButton = {
                    TextAction(text = "确定", onClick = {
                        onTimeChange("%02d:%02d".format(timePickerState.hour, timePickerState.minute))
                        showDialog = false
                    })
                },
                dismissButton = { TextAction(text = "取消", onClick = { showDialog = false }) },
            ) {
                TimePicker(state = timePickerState, colors = pickerColors)
            }
        }
    }
}

@Composable
private fun DeadlinePicker(
    hasDeadline: Boolean,
    onHasDeadlineChange: (Boolean) -> Unit,
    date: String,
    onDateChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "截止日期",
                color = if (hasDeadline) Ink else Muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
            Switch(
                checked = hasDeadline,
                onCheckedChange = onHasDeadlineChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Divider,
                ),
            )
        }
        if (hasDeadline) {
            DatePickerField(value = date, onDateChange = onDateChange, label = "")
        }
    }
}

@Composable
private fun EditDecisionBubble(
    onEdit: () -> Unit,
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(999.dp),
        shadowElevation = 0.dp,
        modifier = Modifier
            .border(1.dp, Color(0xFFE5E5EA), RoundedCornerShape(999.dp)),
    ) {
        Text(
            text = "编辑",
            color = Color.Black,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onEdit() })
                }
                .padding(horizontal = 18.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun RootEditBubbleOverlay(
    request: CommitmentEditMenuRequest,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
) {
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(
            x = (request.anchor.x.roundToInt() - 20).coerceAtLeast(8),
            y = (request.anchor.y.roundToInt() - 54).coerceAtLeast(8),
        ),
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = true,
            dismissOnClickOutside = true,
        ),
    ) {
        EditDecisionBubble(onEdit = onEdit)
    }
}

@Composable
private fun CommitmentEditOverlay(
    target: CommitmentEditTarget,
    onDismiss: () -> Unit,
    onSave: (AgentProposal) -> Unit,
) {
    var entered by remember(target) { mutableStateOf(false) }
    LaunchedEffect(target) { entered = true }
    val cardScale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.96f,
        animationSpec = spring(dampingRatio = 0.84f, stiffness = 520f),
        label = "edit-overlay-scale",
    )
    val scrimAlpha by animateFloatAsState(
        targetValue = if (entered) 0.34f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "edit-overlay-scrim",
    )
    val cardAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "edit-overlay-alpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .pointerInput(target) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Panel.copy(alpha = 0.96f),
            shape = RoundedCornerShape(30.dp),
            shadowElevation = 28.dp,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = cardAlpha
                    scaleX = cardScale
                    scaleY = cardScale
                }
                .border(1.dp, Divider.copy(alpha = 0.78f), RoundedCornerShape(30.dp))
                .pointerInput(target) {
                    detectTapGestures(onTap = { })
                },
        ) {
            when (target) {
                is CommitmentEditTarget.Schedule -> ScheduleEditCard(
                    event = target.event,
                    onDismiss = onDismiss,
                    onSave = onSave,
                )
                is CommitmentEditTarget.Todo -> TodoEditCard(
                    todo = target.todo,
                    onDismiss = onDismiss,
                    onSave = onSave,
                )
            }
        }
    }
}

@Composable
private fun GlobalCreateFab(
    menuVisible: Boolean,
    onToggle: () -> Unit,
    onDismissMenu: () -> Unit,
    onCreateSchedule: () -> Unit,
    onCreateTodo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomEnd) {
        if (menuVisible) {
            Popup(
                alignment = Alignment.BottomEnd,
                offset = IntOffset(x = -18, y = -204),
                onDismissRequest = onDismissMenu,
                properties = PopupProperties(
                    focusable = true,
                    dismissOnClickOutside = true,
                ),
            ) {
                CreateQuickMenu(
                    onCreateSchedule = onCreateSchedule,
                    onCreateTodo = onCreateTodo,
                )
            }
        }
        Surface(
            color = Ink,
            shape = RoundedCornerShape(999.dp),
            shadowElevation = 12.dp,
            modifier = Modifier
                .size(54.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onToggle() })
                },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = "+",
                    color = readableOn(Ink),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 28.sp,
                )
            }
        }
    }
}

@Composable
private fun CreateQuickMenu(
    onCreateSchedule: () -> Unit,
    onCreateTodo: () -> Unit,
) {
    Surface(
        color = Panel.copy(alpha = 0.96f),
        shape = RoundedCornerShape(22.dp),
        shadowElevation = 6.dp,
        modifier = Modifier
            .width(156.dp)
            .border(1.dp, Divider.copy(alpha = 0.8f), RoundedCornerShape(22.dp)),
    ) {
        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CreateMenuRow(text = "日程", onClick = onCreateSchedule)
            CreateMenuRow(text = "待办", onClick = onCreateTodo)
        }
    }
}

@Composable
private fun CreateMenuRow(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(text) {
                detectTapGestures(onTap = { onClick() })
            },
    ) {
        Text(
            text = text,
            color = Ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun CreateCommitmentOverlay(
    kind: CreateCommitmentKind,
    onDismiss: () -> Unit,
    onSave: (AgentProposal) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.34f))
            .pointerInput(kind) {
                detectTapGestures(onTap = { onDismiss() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            color = Panel.copy(alpha = 0.96f),
            shape = RoundedCornerShape(30.dp),
            shadowElevation = 28.dp,
            modifier = Modifier
                .padding(horizontal = 18.dp)
                .fillMaxWidth()
                .border(1.dp, Divider.copy(alpha = 0.78f), RoundedCornerShape(30.dp))
                .pointerInput(kind) {
                    detectTapGestures(onTap = { })
                },
        ) {
            when (kind) {
                CreateCommitmentKind.Schedule -> ScheduleCreateCard(
                    onDismiss = onDismiss,
                    onSave = onSave,
                )
                CreateCommitmentKind.Todo -> TodoCreateCard(
                    onDismiss = onDismiss,
                    onSave = onSave,
                )
            }
        }
    }
}

@Composable
private fun ScheduleCreateCard(
    onDismiss: () -> Unit,
    onSave: (AgentProposal) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("10:00") }
    var notes by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EditCardHeader(title = "新建日程", subtitle = "时间会进入日程线")
        InlineScheduleEditor(
            title = title,
            date = date,
            startTime = startTime,
            endTime = endTime,
            notes = notes,
            onTitleChange = { title = it },
            onDateChange = { date = it },
            onStartTimeChange = { startTime = it },
            onEndTimeChange = { endTime = it },
            onNotesChange = { notes = it },
            onSave = { onSave(createScheduleProposal(title, date, startTime, endTime, notes)) },
            onCancel = onDismiss,
        )
    }
}

@Composable
private fun TodoCreateCard(
    onDismiss: () -> Unit,
    onSave: (AgentProposal) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf("") }
    var hasDeadline by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("medium") }
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EditCardHeader(title = "新建待办", subtitle = "截止可留空")
        InlineTodoEditor(
            title = title,
            due = due,
            notes = notes,
            priority = priority,
            hasDeadline = hasDeadline,
            onTitleChange = { title = it },
            onDueChange = { due = it },
            onNotesChange = { notes = it },
            onPriorityChange = { priority = it },
            onHasDeadlineChange = { enabled ->
                hasDeadline = enabled
                if (enabled && due.isBlank()) {
                    due = LocalDate.now().toString()
                } else if (!enabled) {
                    due = ""
                }
            },
            onSave = { onSave(createTodoProposal(title, due, notes, priority)) },
            onCancel = onDismiss,
        )
    }
}

@Composable
private fun ScheduleEditCard(
    event: ScheduleEvent,
    onDismiss: () -> Unit,
    onSave: (AgentProposal) -> Unit,
) {
    val originalDate = event.date.ifBlank { LocalDate.now().toString() }
    val originalStart = event.timeRange.substringBefore(" - ").ifBlank { "09:00" }
    val originalEnd = event.timeRange.substringAfter(" - ", "").ifBlank { "10:00" }
    var title by remember(event.id, event.title) { mutableStateOf(event.title) }
    var date by remember(event.id, event.date) { mutableStateOf(originalDate) }
    var startTime by remember(event.id, event.timeRange) { mutableStateOf(originalStart) }
    var endTime by remember(event.id, event.timeRange) { mutableStateOf(originalEnd) }
    var notes by remember(event.id, event.detail) { mutableStateOf(event.detail) }
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EditCardHeader(title = "编辑日程", subtitle = event.title)
        InlineScheduleEditor(
            title = title,
            date = date,
            startTime = startTime,
            endTime = endTime,
            notes = notes,
            onTitleChange = { title = it },
            onDateChange = { date = it },
            onStartTimeChange = { startTime = it },
            onEndTimeChange = { endTime = it },
            onNotesChange = { notes = it },
            onSave = { onSave(event.toScheduleUpdateProposal(title, date, startTime, endTime, notes)) },
            onCancel = onDismiss,
        )
    }
}

@Composable
private fun TodoEditCard(
    todo: TodoItem,
    onDismiss: () -> Unit,
    onSave: (AgentProposal) -> Unit,
) {
    var title by remember(todo.stableUiKey(), todo.title) { mutableStateOf(todo.title) }
    var due by remember(todo.stableUiKey(), todo.dueDate) { mutableStateOf(todo.dueDate) }
    var hasDeadline by remember(todo.stableUiKey(), todo.dueDate) { mutableStateOf(todo.dueDate.isNotBlank()) }
    var notes by remember(todo.stableUiKey(), todo.detail) { mutableStateOf(todo.detail) }
    var priority by remember(todo.stableUiKey(), todo.priority) { mutableStateOf(todo.priority.backendValue()) }
    Column(
        modifier = Modifier.padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EditCardHeader(title = "编辑待办", subtitle = todo.title)
        InlineTodoEditor(
            title = title,
            due = due,
            notes = notes,
            priority = priority,
            hasDeadline = hasDeadline,
            onTitleChange = { title = it },
            onDueChange = { due = it },
            onNotesChange = { notes = it },
            onPriorityChange = { priority = it },
            onHasDeadlineChange = { enabled ->
                hasDeadline = enabled
                if (enabled && due.isBlank()) {
                    due = LocalDate.now().toString()
                } else if (!enabled) {
                    due = ""
                }
            },
            onSave = { onSave(todo.toTodoUpdateProposal(title, due, notes, priority)) },
            onCancel = onDismiss,
        )
    }
}

@Composable
private fun EditCardHeader(
    title: String,
    subtitle: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                color = Ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 28.sp,
            )
            Text(
                text = subtitle,
                color = Muted,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ExpandableNoteText(
    note: String,
    expanded: Boolean,
    collapsedMaxLines: Int = 0,
    fontSize: Int = 13,
    lineHeight: Int = 19,
) {
    if (note.isBlank()) {
        return
    }
    if (!expanded && collapsedMaxLines == 0) {
        return
    }
    Text(
        text = note,
        color = Muted,
        fontSize = fontSize.sp,
        lineHeight = lineHeight.sp,
        maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

@Composable
private fun UpcomingEventRow(
    event: ScheduleEvent,
    onDelete: () -> Unit,
    onEdit: CommitmentEditRequester,
) {
    var confirmingDelete by remember(event.id) { mutableStateOf(false) }
    var expanded by remember(event.id, event.detail) { mutableStateOf(false) }
    val hasNote = event.detail.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 240))
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
        Column(
            modifier = Modifier
                .weight(1f)
                .commitmentLongPressMenu(
                    target = event.editTargetOrNull(),
                    onEdit = onEdit,
                    onTap = { if (hasNote) expanded = !expanded },
                ),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = event.title,
                    color = Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (hasNote) {
                    Text(text = if (expanded) "收起" else "备注", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            ExpandableNoteText(note = event.detail, expanded = expanded)
        }
        if (event.id.isNotBlank()) {
            ConfirmableMiniDeleteButton(
                confirming = confirmingDelete,
                onRequestConfirm = { confirmingDelete = true },
                onConfirm = {
                    confirmingDelete = false
                    onDelete()
                },
                onCancel = { confirmingDelete = false },
            )
        }
    }
}
@Composable
private fun CalendarSurface(
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
    onDeleteCommitment: (String) -> Unit,
    onEditCommitment: CommitmentEditRequester,
    modifier: Modifier = Modifier,
) {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var visibleMonth by remember { mutableStateOf(YearMonth.now()) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonthSwitcher(
            month = visibleMonth,
            onPrevious = {
                val nextMonth = visibleMonth.minusMonths(1)
                visibleMonth = nextMonth
                selectedDate = nextMonth.atDay(1)
            },
            onNext = {
                val nextMonth = visibleMonth.plusMonths(1)
                visibleMonth = nextMonth
                selectedDate = nextMonth.atDay(1)
            },
            onToday = {
                visibleMonth = YearMonth.now()
                selectedDate = LocalDate.now()
            },
        )
        CalendarMonthGrid(
            month = visibleMonth,
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
                    onEdit = onEditCommitment,
                )
            }
            selectedTodos.forEach { todo ->
                TodoCard(
                    todo = todo,
                    onDelete = { onDeleteCommitment(todo.id) },
                    onEdit = onEditCommitment,
                )
            }
        }
    }
}

@Composable
private fun MonthSwitcher(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextAction(text = "‹", onClick = onPrevious)
        Text(
            text = "${month.year}年${month.monthValue}月",
            color = Ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextAction(text = "今天", onClick = onToday)
            TextAction(text = "›", onClick = onNext)
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
                            color = readableOn(loadColor),
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
    onEditCommitment: CommitmentEditRequester,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val normalizedQuery = query.trim()
    val active = todos
        .filterNot { it.done }
        .filter { it.matchesTodoQuery(normalizedQuery) }
        .sortedByDueDate()
    val done = todos
        .filter { it.done }
        .filter { it.matchesTodoQuery(normalizedQuery) }
        .sortedByDueDate()
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TodoSearchField(
            query = query,
            onQueryChange = { query = it },
        )
        if (active.isEmpty() && done.isEmpty()) {
            EmptyState(
                title = if (normalizedQuery.isBlank()) "—" else "没有匹配",
                detail = if (normalizedQuery.isBlank()) "" else normalizedQuery,
            )
        } else {
            Column(
                modifier = Modifier.animateContentSize(
                    animationSpec = tween(durationMillis = 220),
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                active.forEach { todo ->
                    key(todo.stableUiKey()) {
                        TodoCard(
                            todo = todo,
                            onDelete = { onDeleteCommitment(todo.id) },
                            onEdit = onEditCommitment,
                        )
                    }
                }
                done.forEach { todo ->
                    key(todo.stableUiKey()) {
                        TodoCard(
                            todo = todo,
                            onDelete = { onDeleteCommitment(todo.id) },
                            onEdit = onEditCommitment,
                        )
                    }
                }
            }
        }
    }
}

private fun TodoItem.stableUiKey(): String {
    return id.ifBlank { "${title}:${dueDate}:${detail}" }
}

private fun ScheduleEvent.editTargetOrNull(): CommitmentEditTarget.Schedule? {
    return id.takeIf { it.isNotBlank() }?.let { CommitmentEditTarget.Schedule(this) }
}

private fun TodoItem.editTargetOrNull(): CommitmentEditTarget.Todo? {
    return id.takeIf { it.isNotBlank() }?.let { CommitmentEditTarget.Todo(this) }
}

@Composable
private fun TodoSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        label = { Text("搜索待办", fontSize = 12.sp) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = appTextFieldColors(),
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
    )
}

@Composable
private fun EventCard(
    event: ScheduleEvent,
    onDelete: () -> Unit,
    onEdit: CommitmentEditRequester,
) {
    var expanded by remember(event.id, event.detail) { mutableStateOf(false) }
    val hasNote = event.detail.isNotBlank()
    Surface(
        color = Panel,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 240))
            .border(1.dp, Divider, RoundedCornerShape(16.dp)),
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .commitmentLongPressMenu(
                    target = event.editTargetOrNull(),
                    onEdit = onEdit,
                    onTap = { if (hasNote) expanded = !expanded },
                ),
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = event.title,
                        color = Ink,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (hasNote) {
                        Text(text = if (expanded) "收起" else "备注", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                ExpandableNoteText(note = event.detail, expanded = expanded, collapsedMaxLines = 1, fontSize = 13, lineHeight = 18)
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
    onEdit: CommitmentEditRequester,
) {
    val color = if (todo.done) Success else todo.priority.color()
    val density = LocalDensity.current
    val revealWidthPx = with(density) { 88.dp.toPx() }
    var targetOffsetX by remember(todo.stableUiKey()) { mutableStateOf(0f) }
    var expanded by remember(todo.stableUiKey(), todo.detail) { mutableStateOf(false) }
    val hasNote = todo.detail.isNotBlank()
    val animatedOffsetX by animateFloatAsState(
        targetValue = targetOffsetX,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 520f),
        label = "todo-swipe-offset",
    )
    val revealProgress = (-animatedOffsetX / revealWidthPx).coerceIn(0f, 1f)
    val revealDelete = revealProgress > 0.01f
    val deleteSlideOffsetX = with(density) { (24.dp.toPx() * (1f - revealProgress)).roundToInt() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 78.dp)
            .animateContentSize(animationSpec = tween(durationMillis = 240)),
    ) {
        if (revealDelete) {
            Surface(
                color = Danger,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .height(68.dp)
                    .width(88.dp)
                    .offset { androidx.compose.ui.unit.IntOffset(deleteSlideOffsetX, 0) }
                    .graphicsLayer { alpha = revealProgress }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { onDelete() })
                    },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(text = "删除", color = readableOn(Danger), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Surface(
            color = Panel,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .heightIn(min = 68.dp)
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
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .commitmentLongPressMenu(
                        target = todo.editTargetOrNull(),
                        onEdit = onEdit,
                        onTap = { if (hasNote) expanded = !expanded },
                        onBeforeEdit = { targetOffsetX = 0f },
                    ),
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
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            ExpandableNoteText(
                                note = todo.detail,
                                expanded = expanded,
                                collapsedMaxLines = 1,
                                fontSize = 12,
                                lineHeight = 16,
                            )
                        }
                        if (hasNote) {
                            Text(text = if (expanded) "收起" else "备注", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
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
private fun ConfirmableMiniDeleteButton(
    confirming: Boolean,
    onRequestConfirm: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    if (!confirming) {
        MiniDeleteButton(onClick = onRequestConfirm)
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            color = Danger,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = { onConfirm() })
            },
        ) {
            Text(
                text = "删除",
                color = readableOn(Danger),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
        Surface(
            color = AccentSoft,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = { onCancel() })
            },
        ) {
            Text(
                text = "取消",
                color = Accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }
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
    connectionSettings: AgentConnectionSettings,
    commitmentsProvider: () -> AgentCommitmentsPayload,
    proposalApplier: (AgentProposal) -> AgentApplyResult,
    onCommitmentsChanged: () -> Unit,
    onVoiceRecordingChanged: (Boolean) -> Unit,
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
                connectionSettings = connectionSettings,
                commitmentsProvider = commitmentsProvider,
                proposalApplier = proposalApplier,
                onCommitmentsChanged = onCommitmentsChanged,
                onVoiceRecordingChanged = onVoiceRecordingChanged,
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
    connectionSettings: AgentConnectionSettings,
    commitmentsProvider: () -> AgentCommitmentsPayload,
    proposalApplier: (AgentProposal) -> AgentApplyResult,
    onCommitmentsChanged: () -> Unit,
    onVoiceRecordingChanged: (Boolean) -> Unit,
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
    val hasModelApiKey = connectionSettings.deepseekApiKey.isNotBlank()
    val isSignedIn = connectionSettings.accessToken.isNotBlank() && connectionSettings.userEmail.isNotBlank()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    LaunchedEffect(isListening) {
        onVoiceRecordingChanged(isListening)
    }

    fun submit(value: String) {
        val prompt = value.trim()
        if (prompt.isBlank()) {
            status = "没有识别到内容"
            return
        }
        if (!isSignedIn) {
            phase = ComposerPhase.Error
            status = "请先在设置里绑定邮箱"
            return
        }
        if (!hasModelApiKey) {
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
            val result = client.propose(
                text = prompt,
                commitments = commitmentsProvider(),
                settings = connectionSettings,
            )
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
                onConfirm = { editedProposal, _ ->
                    val current = editedProposal
                    phase = ComposerPhase.Confirming
                    status = "确认中"
                    Thread {
                        val result = proposalApplier(current)
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
            voiceEnabled = asrClient != null &&
                isSignedIn &&
                hasModelApiKey &&
                phase != ComposerPhase.Working &&
                phase != ComposerPhase.Confirming,
            listening = isListening,
            cancelArmed = voiceCancelArmed,
            onPressStart = {
                if (!isSignedIn) {
                    status = "请先在设置里绑定邮箱"
                    phase = ComposerPhase.Error
                    return@BottomVoiceNav
                }
                if (!hasModelApiKey) {
                    return@BottomVoiceNav
                }
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
                if (client == null) {
                    status = "语音暂不可用"
                    phase = ComposerPhase.Error
                    return@BottomVoiceNav
                }
                client.stopAndAwaitFinal(timeoutMillis = 1800L) { finalText ->
                    mainHandler.post {
                        if (releaseRequestId != activeRequestId) {
                            return@post
                        }
                        val text = finalText.ifBlank { voiceText }.trim()
                        if (text.isNotBlank()) {
                            status = "正在整理"
                            submit(text)
                        } else {
                            status = "没有识别到内容"
                            phase = ComposerPhase.Error
                        }
                    }
                }
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
                .background(Canvas.copy(alpha = 0.92f))
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
                    proposal?.presentation() == ProposalPresentation.Confirmable
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
                when (proposal?.takeIf { phase == ComposerPhase.ProposalReady }?.presentation()) {
                    ProposalPresentation.CandidateChoice -> CandidateChoiceList(
                        candidates = proposal.candidates,
                        onCandidateSelected = onCandidateSelected,
                        onClose = onCancel,
                    )

                    ProposalPresentation.Confirmable -> ProposalReviewPanel(
                        proposal = proposal,
                        onConfirm = onConfirm,
                        onClose = onCancel,
                    )

                    ProposalPresentation.ResultOnly -> ProposalResultPanel(
                        proposal = proposal,
                        onClose = onCancel,
                    )

                    null -> Unit
                }
                when {
                    phase == ComposerPhase.Working -> {
                        TextAction(text = "停止", onClick = onCancel)
                    }
                    phase == ComposerPhase.Error -> {
                        Button(
                            onClick = onCancel,
                            colors = ButtonDefaults.buttonColors(containerColor = InkSoft),
                            shape = RoundedCornerShape(999.dp),
                        ) {
                            Text("关闭", color = readableOn(InkSoft))
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
    onClose: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextAction(text = "关闭", onClick = onClose)
        }
        candidates.take(8).forEach { candidate ->
            Surface(
                color = Panel.copy(alpha = 0.72f),
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
                    Text(
                        text = candidate.actionLabel,
                        color = if (candidate.actionLabel == "删除") Danger else Accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
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
        color = Panel.copy(alpha = 0.92f),
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
                Text("确认", color = readableOn(Accent), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
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
                Text(text = patch.notes, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        proposal.todoPatch?.let { patch ->
            Text(text = "截止 ${patch.due}", color = InkSoft, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (patch.notes.isNotBlank()) {
                Text(text = patch.notes, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        if (proposal.schedulePatch == null && proposal.todoPatch == null) {
            Text(
                text = proposal.summary.ifBlank { proposal.impact },
                color = InkSoft,
                fontSize = 17.sp,
                lineHeight = 23.sp,
            )
        }
    }
}

@Composable
private fun ProposalResultPanel(
    proposal: AgentProposal,
    onClose: () -> Unit,
) {
    Surface(
        color = Panel.copy(alpha = 0.92f),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 18.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(28.dp)),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = proposal.title,
                color = Ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 29.sp,
            )
            Text(
                text = proposal.summary,
                color = InkSoft,
                fontSize = 17.sp,
                lineHeight = 24.sp,
            )
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text("关闭", color = readableOn(Ink), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
    singleLine: Boolean = true,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 12.sp) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        shape = RoundedCornerShape(14.dp),
        colors = appTextFieldColors(),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun appTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Ink,
    unfocusedTextColor = Ink,
    focusedLabelColor = Accent,
    unfocusedLabelColor = Muted,
    focusedBorderColor = Accent,
    unfocusedBorderColor = Divider,
    cursorColor = Accent,
    focusedContainerColor = Panel,
    unfocusedContainerColor = Panel,
)

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
                Text(text = symbol, color = readableOn(color), fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
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
        val supportPath = Path().apply {
            moveTo(8.dp.toPx(), 14.dp.toPx())
            cubicTo(
                8.dp.toPx(),
                20.dp.toPx(),
                11.8.dp.toPx(),
                24.dp.toPx(),
                17.dp.toPx(),
                24.dp.toPx(),
            )
            cubicTo(
                22.2.dp.toPx(),
                24.dp.toPx(),
                26.dp.toPx(),
                20.dp.toPx(),
                26.dp.toPx(),
                14.dp.toPx(),
            )
        }
        drawPath(path = supportPath, color = color, style = stroke)
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
            .background(BottomBar)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomTabButton(
            icon = BottomNavIcon.Today,
            label = "日程",
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
        color = if (armed) DangerSoft else AccentSoft,
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
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
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
                    MiniWaveform(color = VoiceGlyph)
                } else {
                    MicGlyph(color = if (enabled) VoiceGlyph else VoiceDisabledGlyph)
                }
            }
        }
    }
}

@Composable
private fun ScheduleRisk.color(): Color {
    return when (this) {
        ScheduleRisk.Normal -> Success
        ScheduleRisk.Deadline -> Risk
        ScheduleRisk.Focus -> Accent
    }
}

@Composable
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

private fun TodoPriority.backendValue(): String {
    return when (this) {
        TodoPriority.High -> "high"
        TodoPriority.Medium -> "medium"
        TodoPriority.Low -> "low"
    }
}

@Composable
private fun String.priorityColor(): Color {
    return when (this) {
        "high" -> Danger
        "low" -> Success
        else -> Risk
    }
}

private fun TodoPriority.sortWeight(): Int {
    return when (this) {
        TodoPriority.High -> 0
        TodoPriority.Medium -> 1
        TodoPriority.Low -> 2
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

private fun List<TodoItem>.sortedByDueDate(): List<TodoItem> {
    return sortedWith(
        compareBy<TodoItem> { todo ->
            runCatching { LocalDate.parse(todo.dueDate) }.getOrNull() ?: LocalDate.MAX
        }.thenBy { todo -> todo.priority.sortWeight() },
    )
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

private fun TodoItem.relativeDueLabel(): String {
    val due = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return dueLabel
    val today = LocalDate.now()
    return when (due) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        today.plusDays(2) -> "后天"
        else -> "${due.monthValue}/${due.dayOfMonth}"
    }
}

private fun TodoItem.matchesTodoQuery(query: String): Boolean {
    if (query.isBlank()) return true
    val haystack = listOf(
        title,
        dueDate,
        dueLabel,
        detail,
        tag,
        priority.label(),
    ).joinToString(" ")
    return haystack.contains(query, ignoreCase = true)
}
