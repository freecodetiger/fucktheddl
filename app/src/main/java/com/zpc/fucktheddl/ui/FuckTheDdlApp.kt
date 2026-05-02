package com.zpc.fucktheddl.ui

import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.zpc.fucktheddl.BuildConfig
import com.zpc.fucktheddl.R
import com.zpc.fucktheddl.about.AboutRepositoryInfo
import com.zpc.fucktheddl.agent.AgentApplyResult
import com.zpc.fucktheddl.agent.AgentClient
import com.zpc.fucktheddl.agent.AgentCommitmentsPayload
import com.zpc.fucktheddl.agent.AgentConnectionSettings
import com.zpc.fucktheddl.agent.AgentProposal
import com.zpc.fucktheddl.agent.AgentProposalCandidate
import com.zpc.fucktheddl.agent.AgentSchedulePatch
import com.zpc.fucktheddl.agent.AgentTodoPatch
import com.zpc.fucktheddl.agent.AgentUpdatePatch
import com.zpc.fucktheddl.agent.CommitmentType
import com.zpc.fucktheddl.agent.DEFAULT_ALIYUN_ASR_URL
import com.zpc.fucktheddl.agent.LocalAgentClient
import com.zpc.fucktheddl.agent.ProposalPresentation
import com.zpc.fucktheddl.agent.createScheduleProposal
import com.zpc.fucktheddl.agent.createTodoProposal
import com.zpc.fucktheddl.agent.mapCommitmentsToScheduleState
import com.zpc.fucktheddl.agent.presentation
import com.zpc.fucktheddl.agent.toScheduleUpdateProposal
import com.zpc.fucktheddl.agent.toTodoUpdateProposal
import com.zpc.fucktheddl.notifications.DailyReminderSettings
import com.zpc.fucktheddl.quests.QuestBook
import com.zpc.fucktheddl.quests.QuestBookKind
import com.zpc.fucktheddl.quests.QuestBookTree
import com.zpc.fucktheddl.quests.QuestNode
import com.zpc.fucktheddl.schedule.ScheduleEvent
import com.zpc.fucktheddl.schedule.ScheduleRisk
import com.zpc.fucktheddl.schedule.ScheduleShellState
import com.zpc.fucktheddl.schedule.ScheduleTab
import com.zpc.fucktheddl.schedule.TabDestination
import com.zpc.fucktheddl.schedule.TodoItem
import com.zpc.fucktheddl.schedule.TodoPriority
import com.zpc.fucktheddl.voice.RealtimeAsrCallback
import com.zpc.fucktheddl.voice.RealtimeAsrClient
import com.zpc.fucktheddl.updates.UpdateChecker
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
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
    val amber: Color,
    val amberSoft: Color,
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
    amber = Color(0xFFC8832E),
    amberSoft = Color(0xFFFFF4E6),
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
    amber = Color(0xFFD4913D),
    amberSoft = Color(0xFF2C2418),
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
    amber = Color(0xFFC8832E),
    amberSoft = Color(0xFFFFF4E6),
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
private val Amber: Color
    @Composable get() = LocalAppColors.current.amber
private val AmberSoft: Color
    @Composable get() = LocalAppColors.current.amberSoft
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
    Connection,
    DailyReminder,
    Theme,
    Statistics,
}

private enum class CreateCommitmentKind {
    Schedule,
    Todo,
}

internal enum class WorkspacePage(val index: Int) {
    Calendar(0),
    Today(1),
    Todo(2),
    Quest(3),
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

internal data class StatisticsDisplayModel(
    val startedAtLabel: String,
    val completedSchedules: Int,
    val completedTodos: Int,
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

internal data class QuestVisibleNode(
    val node: QuestNode,
    val depth: Int,
    val childCount: Int,
)

internal const val QuestNodeIndentStepDp = 18

internal fun questNodeIndentDp(depth: Int): Int = depth.coerceAtLeast(0) * QuestNodeIndentStepDp

private data class QuestBookEditorState(
    val kind: QuestBookKind,
    val book: QuestBook? = null,
)

private data class QuestNodeEditorState(
    val node: QuestNode,
)

private data class UpdatePromptState(
    val latestVersion: String,
)

internal fun buildVoiceRefinementPrompt(
    originalText: String,
    proposal: AgentProposal,
    correctionText: String,
): String {
    return listOf(
        "用户正在修正上一条语音指令。请在原始请求基础上应用用户修正，输出一个完整的新日程或待办意图，不要只处理修正句本身。",
        "原始请求：${originalText.trim()}",
        "当前理解：${proposal.voiceRefinementSummary()}",
        "用户修正：${correctionText.trim()}",
    ).joinToString("\n")
}

private fun AgentProposal.voiceRefinementSummary(): String {
    schedulePatch?.let { patch ->
        return "日程，标题=${patch.title}，开始=${patch.start}，结束=${patch.end}，地点=${patch.location}，备注=${patch.notes}"
    }
    todoPatch?.let { patch ->
        return "待办，标题=${patch.title}，截止=${patch.due}，优先级=${patch.priority}，备注=${patch.notes}"
    }
    return "${commitmentType.name}，标题=$title，摘要=$summary"
}

internal fun newVoiceConversationSessionId(): String = "voice-${UUID.randomUUID()}"

private sealed interface CommitmentEditTarget {
    data class Schedule(val event: ScheduleEvent) : CommitmentEditTarget
    data class Todo(val todo: TodoItem) : CommitmentEditTarget
}

private typealias CommitmentEditRequester = (CommitmentEditTarget) -> Unit

private fun Modifier.commitmentLongPressMenu(
    target: CommitmentEditTarget?,
    onEdit: CommitmentEditRequester,
    onTap: () -> Unit = {},
    onBeforeEdit: () -> Unit = {},
): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    // Auto-reset scale after brief press animation
    LaunchedEffect(pressed) {
        if (pressed) {
            kotlinx.coroutines.delay(400)
            pressed = false
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.3f, stiffness = 800f),
        label = "longPressScale",
    )
    scale(scale)
        .pointerInput(target) {
            detectTapGestures(
                onLongPress = {
                    target?.let {
                        pressed = true
                        onBeforeEdit()
                        onEdit(it)
                    }
                },
                onTap = { onTap() },
            )
        }
}

private fun Modifier.pressFeedbackClick(
    onClick: () -> Unit,
    enabled: Boolean = true,
    pressedScale: Float = 0.94f,
    hapticFeedback: Int = HapticFeedbackConstants.CLOCK_TICK,
): Modifier = composed {
    val view = LocalView.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 760f),
        label = "press-feedback-scale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.pointerInput(enabled, onClick) {
        detectTapGestures(
            onPress = {
                if (enabled) {
                    view.performHapticFeedback(hapticFeedback)
                    pressed = true
                    val released = tryAwaitRelease()
                    pressed = false
                    if (released) onClick()
                }
            },
        )
    }
}

@Composable
private fun hapticClick(
    onClick: () -> Unit,
    hapticFeedback: Int = HapticFeedbackConstants.CLOCK_TICK,
): () -> Unit {
    val view = LocalView.current
    return {
        view.performHapticFeedback(hapticFeedback)
        onClick()
    }
}

internal fun settleWorkspacePage(
    currentPage: WorkspacePage,
    totalDragX: Float,
    thresholdPx: Float,
): WorkspacePage {
    val targetIndex = when {
        totalDragX > thresholdPx -> currentPage.index - 1
        totalDragX < -thresholdPx -> currentPage.index + 1
        else -> currentPage.index
    }.coerceIn(WorkspacePage.Calendar.index, WorkspacePage.Quest.index)
    return WorkspacePage.values().first { it.index == targetIndex }
}

internal fun shouldShowQuestEdgeHint(currentPage: WorkspacePage): Boolean {
    return currentPage == WorkspacePage.Todo
}

@Composable
fun FuckTheDdlApp(
    initialState: ScheduleShellState,
    connectionSettings: AgentConnectionSettings,
    themeMode: AppThemeMode = AppThemeMode.ClassicLight,
    dailyReminderSettings: DailyReminderSettings = DailyReminderSettings(),
    notificationPermissionGranted: Boolean = true,
    agentClient: AgentClient? = LocalAgentClient(),
    asrClient: RealtimeAsrClient? = null,
    commitmentsProvider: () -> AgentCommitmentsPayload = { AgentCommitmentsPayload(emptyList(), emptyList()) },
    proposalApplier: (AgentProposal) -> AgentApplyResult = { AgentApplyResult("failed", "", "本地存储不可用") },
    commitmentDeleter: (String) -> AgentApplyResult = { AgentApplyResult("failed", "", "本地存储不可用") },
    questBooksProvider: (QuestBookKind) -> List<QuestBook> = { emptyList() },
    questTreeProvider: (String) -> QuestBookTree? = { null },
    questBookCreator: (QuestBookKind, String, String, String, String) -> QuestBook = { kind, title, description, location, targetDate ->
        QuestBook("", kind, title, description, location, targetDate, false, "", "")
    },
    questBookUpdater: (QuestBook) -> QuestBook = { it },
    questBookDeleter: (String) -> Unit = {},
    questNodeCreator: (String, String?, String) -> QuestNode = { bookId, parentId, title ->
        QuestNode("", bookId, parentId, title, "", "", "", false, true, 0, "", "")
    },
    questNodeUpdater: (QuestNode) -> QuestNode = { it },
    questNodeDeleter: (String) -> Unit = {},
    onConnectionSettingsSaved: (AgentConnectionSettings) -> Unit = {},
    onThemeModeChanged: (AppThemeMode) -> Unit = {},
    onDailyReminderSettingsChanged: (DailyReminderSettings) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
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
    var workspacePage by remember {
        mutableStateOf(
            if (initialState.selectedTab.destination == TabDestination.Todo) {
                WorkspacePage.Todo
            } else {
                WorkspacePage.Today
            }
        )
    }
    val selectedTab = if (workspacePage == WorkspacePage.Todo) todoTab else todayTab
    var showingSettings by remember { mutableStateOf(false) }
    var activeEditTarget by remember { mutableStateOf<CommitmentEditTarget?>(null) }
    var createMenuVisible by remember { mutableStateOf(false) }
    var activeCreateKind by remember { mutableStateOf<CreateCommitmentKind?>(null) }
    var voiceRecording by remember { mutableStateOf(false) }
    var backendConnectionState by remember { mutableStateOf(BackendConnectionState()) }
    var questKind by remember { mutableStateOf(QuestBookKind.Main) }
    var questBooks by remember { mutableStateOf<List<QuestBook>>(emptyList()) }
    var activeQuestTree by remember { mutableStateOf<QuestBookTree?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    fun testLocalServices(settings: AgentConnectionSettings) {
        backendConnectionState = BackendConnectionState(
            indicator = ConnectionIndicator.Checking,
            label = "检测中",
        )
        Thread {
            val result = (agentClient ?: LocalAgentClient()).testService(settings)
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

    fun refreshQuestBooks(kind: QuestBookKind = questKind) {
        Thread {
            runCatching {
                questBooksProvider(kind)
            }.onSuccess { books ->
                mainHandler.post {
                    questBooks = books
                    activeQuestTree?.let { tree ->
                        if (books.none { it.id == tree.book.id }) {
                            activeQuestTree = null
                        }
                    }
                }
            }.onFailure { error ->
                mainHandler.post {
                    backendConnectionState = BackendConnectionState(
                        indicator = ConnectionIndicator.Failed,
                        label = error.message?.takeIf { it.isNotBlank() } ?: "任务书读取失败",
                    )
                }
            }
        }.start()
    }

    fun refreshQuestTree(bookId: String) {
        Thread {
            runCatching {
                questTreeProvider(bookId)
            }.onSuccess { tree ->
                mainHandler.post {
                    activeQuestTree = tree
                }
            }.onFailure { error ->
                mainHandler.post {
                    backendConnectionState = BackendConnectionState(
                        indicator = ConnectionIndicator.Failed,
                        label = error.message?.takeIf { it.isNotBlank() } ?: "任务树读取失败",
                    )
                }
            }
        }.start()
    }

    fun runQuestMutation(
        action: () -> Unit,
        refreshBookId: String? = activeQuestTree?.book?.id,
    ) {
        Thread {
            runCatching {
                action()
            }.onSuccess {
                refreshQuestBooks()
                refreshBookId?.let { refreshQuestTree(it) }
            }.onFailure { error ->
                mainHandler.post {
                    backendConnectionState = BackendConnectionState(
                        indicator = ConnectionIndicator.Failed,
                        label = error.message?.takeIf { it.isNotBlank() } ?: "任务书保存失败",
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
        refreshQuestBooks()
        backendConnectionState = connectionSettings.localConfigState()
    }

    CompositionLocalProvider(LocalAppColors provides themeMode.colors()) {
        Scaffold(
            containerColor = Canvas,
            bottomBar = {
                BottomWorkspace(
                    currentPage = workspacePage,
                    selectedTab = selectedTab,
                    onTodaySelected = {
                        workspacePage = WorkspacePage.Today
                    },
                    onTodoSelected = {
                        workspacePage = WorkspacePage.Todo
                    },
                    agentClient = agentClient,
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
                            workspacePage = WorkspacePage.Calendar
                        },
                        onSettingsClick = { showingSettings = true },
                    )
                    WorkspacePager(
                        currentPage = workspacePage,
                        events = shellState.events,
                        todos = shellState.todos,
                        questKind = questKind,
                        questBooks = questBooks,
                        activeQuestTree = activeQuestTree,
                        onQuestKindChange = { kind ->
                            questKind = kind
                            activeQuestTree = null
                            refreshQuestBooks(kind)
                        },
                        onQuestBookOpen = { book ->
                            refreshQuestTree(book.id)
                        },
                        onQuestBookClose = {
                            activeQuestTree = null
                            refreshQuestBooks()
                        },
                        onQuestBookCreate = { kind, title, description, location, targetDate ->
                            runQuestMutation(
                                action = { questBookCreator(kind, title, description, location, targetDate) },
                                refreshBookId = null,
                            )
                        },
                        onQuestBookUpdate = { book ->
                            runQuestMutation(
                                action = { questBookUpdater(book) },
                                refreshBookId = book.id,
                            )
                        },
                        onQuestBookDelete = { book ->
                            activeQuestTree = null
                            runQuestMutation(
                                action = { questBookDeleter(book.id) },
                                refreshBookId = null,
                            )
                        },
                        onQuestNodeCreate = { bookId, parentId, title ->
                            runQuestMutation(
                                action = { questNodeCreator(bookId, parentId, title) },
                                refreshBookId = bookId,
                            )
                        },
                        onQuestNodeUpdate = { node ->
                            runQuestMutation(
                                action = { questNodeUpdater(node) },
                                refreshBookId = node.bookId,
                            )
                        },
                        onQuestNodeDelete = { node ->
                            runQuestMutation(
                                action = { questNodeDeleter(node.id) },
                                refreshBookId = node.bookId,
                            )
                        },
                        onDeleteCommitment = ::deleteCommitment,
                        onEditCommitment = { target ->
                            activeEditTarget = target
                        },
                        onToggleTodo = { todo -> updateCommitment(todo.toggleDoneProposal()) },
                        onPageSettled = { page ->
                            workspacePage = page
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (shouldShowQuestEdgeHint(workspacePage) && !voiceRecording && !showingSettings && activeEditTarget == null && activeCreateKind == null) {
                    QuestEdgeHint(
                        onClick = { workspacePage = WorkspacePage.Quest },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 0.dp),
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
                if (!voiceRecording && !showingSettings && activeEditTarget == null && activeCreateKind == null) {
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
                    dailyReminderSettings = dailyReminderSettings,
                    notificationPermissionGranted = notificationPermissionGranted,
                    events = shellState.events,
                    todos = shellState.todos,
                    onThemeModeChanged = onThemeModeChanged,
                    onDailyReminderSettingsChanged = onDailyReminderSettingsChanged,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onTestConnection = ::testLocalServices,
                    onSave = { settings ->
                        val shouldRetest = settings.serviceTestKey() != connectionSettings.serviceTestKey()
                        onConnectionSettingsSaved(settings)
                        if (shouldRetest) {
                            backendConnectionState = settings.localConfigState()
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
            modifier = Modifier.pressFeedbackClick(onClick = onDateClick, pressedScale = 0.98f),
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
            .pressFeedbackClick(onClick = onClick),
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
    dailyReminderSettings: DailyReminderSettings,
    notificationPermissionGranted: Boolean,
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    onDailyReminderSettingsChanged: (DailyReminderSettings) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onTestConnection: (AgentConnectionSettings) -> Unit,
    onSave: (AgentConnectionSettings) -> Unit,
    onClose: () -> Unit,
) {
    var panel by remember { mutableStateOf(SettingsPanel.Root) }
    var deepseekApiKey by remember(settings) { mutableStateOf(settings.deepseekApiKey) }
    var deepseekBaseUrl by remember(settings) { mutableStateOf(settings.deepseekBaseUrl) }
    var deepseekModel by remember(settings) { mutableStateOf(settings.deepseekModel) }
    var aliyunApiKey by remember(settings) { mutableStateOf(settings.aliyunApiKey) }
    var aliyunAsrUrl by remember(settings) { mutableStateOf(settings.aliyunAsrUrl) }
    var versionLabel by remember { mutableStateOf(BuildConfig.VERSION_NAME) }
    var updateChecking by remember { mutableStateOf(false) }
    var updatePrompt by remember { mutableStateOf<UpdatePromptState?>(null) }
    var repositoryPromptVisible by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val deepseekUrlValid = deepseekBaseUrl.trim().let { it.startsWith("http://") || it.startsWith("https://") }
    val aliyunUrlValid = aliyunAsrUrl.trim().ifBlank { DEFAULT_ALIYUN_ASR_URL }.startsWith("wss://") ||
        aliyunAsrUrl.trim().ifBlank { DEFAULT_ALIYUN_ASR_URL }.startsWith("https://")
    val urlValid = deepseekUrlValid && aliyunUrlValid
    val settingsToTest = AgentConnectionSettings(
        deepseekApiKey = deepseekApiKey.trim(),
        deepseekBaseUrl = deepseekBaseUrl.trim().ifBlank { "https://api.deepseek.com/v1" },
        deepseekModel = deepseekModel.trim().ifBlank { "deepseek-v4-flash" },
        aliyunApiKey = aliyunApiKey.trim(),
        aliyunAsrUrl = aliyunAsrUrl.trim().ifBlank { DEFAULT_ALIYUN_ASR_URL },
    )

    fun checkForUpdates() {
        if (updateChecking) return
        updateChecking = true
        versionLabel = "${BuildConfig.VERSION_NAME} · 检查中"
        Thread {
            val result = UpdateChecker().check(BuildConfig.VERSION_NAME)
            mainHandler.post {
                updateChecking = false
                when {
                    result.error != null -> versionLabel = "${BuildConfig.VERSION_NAME} · 检查失败"
                    result.updateAvailable -> {
                        versionLabel = "${BuildConfig.VERSION_NAME} · 有更新"
                        updatePrompt = UpdatePromptState(result.latestVersion)
                    }
                    else -> versionLabel = "${BuildConfig.VERSION_NAME} · 已是最新版"
                }
            }
        }.start()
    }
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
                            dailyReminderSettings = dailyReminderSettings,
                            notificationPermissionGranted = notificationPermissionGranted,
                            versionLabel = versionLabel,
                            onConnectionClick = { panel = SettingsPanel.Connection },
                            onDailyReminderClick = { panel = SettingsPanel.DailyReminder },
                            onThemeClick = { panel = SettingsPanel.Theme },
                            onStatisticsClick = { panel = SettingsPanel.Statistics },
                            onVersionClick = ::checkForUpdates,
                            onRepositoryClick = { repositoryPromptVisible = true },
                            onClose = onClose,
                        )

                        SettingsPanel.Connection -> ConnectionSettingsMenu(
                            deepseekApiKey = deepseekApiKey,
                            onDeepseekApiKeyChange = { deepseekApiKey = it },
                            deepseekBaseUrl = deepseekBaseUrl,
                            onDeepseekBaseUrlChange = { deepseekBaseUrl = it },
                            deepseekModel = deepseekModel,
                            onDeepseekModelChange = { deepseekModel = it },
                            aliyunApiKey = aliyunApiKey,
                            onAliyunApiKeyChange = { aliyunApiKey = it },
                            aliyunAsrUrl = aliyunAsrUrl,
                            onAliyunAsrUrlChange = { aliyunAsrUrl = it },
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

                        SettingsPanel.DailyReminder -> DailyReminderSettingsMenu(
                            settings = dailyReminderSettings,
                            notificationPermissionGranted = notificationPermissionGranted,
                            onSettingsChanged = onDailyReminderSettingsChanged,
                            onRequestNotificationPermission = onRequestNotificationPermission,
                            onBack = { panel = SettingsPanel.Root },
                        )

                        SettingsPanel.Statistics -> StatisticsPanel(
                            events = events,
                            todos = todos,
                            onBack = { panel = SettingsPanel.Root },
                        )
                    }
                }
            }
            updatePrompt?.let { prompt ->
                UpdateAvailableOverlay(
                    latestVersion = prompt.latestVersion,
                    onDismiss = { updatePrompt = null },
                )
            }
            if (repositoryPromptVisible) {
                GitHubRepositoryOverlay(
                    onDismiss = { repositoryPromptVisible = false },
                )
            }
        }
    }
}

@Composable
private fun SettingsRootMenu(
    connectionState: BackendConnectionState,
    themeMode: AppThemeMode,
    dailyReminderSettings: DailyReminderSettings,
    notificationPermissionGranted: Boolean,
    versionLabel: String,
    onConnectionClick: () -> Unit,
    onDailyReminderClick: () -> Unit,
    onThemeClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onVersionClick: () -> Unit,
    onRepositoryClick: () -> Unit,
    onClose: () -> Unit,
) {
    SettingsHeader(title = "设置")
    SettingsMenuRow(
        title = "本地服务",
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
    SettingsMenuRow(
        title = "每日提醒",
        detail = dailyReminderSettings.reminderDetail(notificationPermissionGranted),
        swatch = if (dailyReminderSettings.enabled) Amber else Muted,
        onClick = onDailyReminderClick,
    )
    SettingsMenuRow(
        title = "统计",
        detail = "已完成日程与待办",
        swatch = Success,
        onClick = onStatisticsClick,
    )
    SettingsGroupTitle("关于")
    SettingsInfoRow(
        label = "版本",
        value = versionLabel,
        leadingColor = Ink,
        onClick = onVersionClick,
    )
    GitHubRepositoryRow(onClick = onRepositoryClick)
    Button(
        onClick = hapticClick(onClick = onClose),
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
    deepseekApiKey: String,
    onDeepseekApiKeyChange: (String) -> Unit,
    deepseekBaseUrl: String,
    onDeepseekBaseUrlChange: (String) -> Unit,
    deepseekModel: String,
    onDeepseekModelChange: (String) -> Unit,
    aliyunApiKey: String,
    onAliyunApiKeyChange: (String) -> Unit,
    aliyunAsrUrl: String,
    onAliyunAsrUrlChange: (String) -> Unit,
    urlValid: Boolean,
    connectionState: BackendConnectionState,
    onBack: () -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
) {
    SettingsHeader(title = "本地服务", onBack = onBack)
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
    OutlinedTextField(
        value = aliyunApiKey,
        onValueChange = onAliyunApiKeyChange,
        label = { Text("阿里云语音 API Key", fontSize = 12.sp) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = appTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = aliyunAsrUrl,
        onValueChange = onAliyunAsrUrlChange,
        label = { Text("阿里云语音 URL", fontSize = 12.sp) },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = appTextFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
    if (!urlValid) {
        Text(
            text = "DeepSeek URL 需要以 http:// 或 https:// 开头；阿里云语音 URL 需要以 wss:// 或 https:// 开头",
            color = Danger,
            fontSize = 12.sp,
        )
    }
    Button(
        onClick = hapticClick(onClick = onTest),
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
            text = if (connectionState.indicator == ConnectionIndicator.Checking) "测试中..." else "测试本地服务",
            color = Accent,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
    ConnectionTestResult(connectionState = connectionState)
    Button(
        onClick = hapticClick(onClick = onSave),
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
private fun DailyReminderSettingsMenu(
    settings: DailyReminderSettings,
    notificationPermissionGranted: Boolean,
    onSettingsChanged: (DailyReminderSettings) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onBack: () -> Unit,
) {
    var timeLabel by remember(settings) { mutableStateOf(settings.timeLabel) }
    SettingsHeader(title = "每日提醒", onBack = onBack)
    Surface(
        color = AccentSoft.copy(alpha = 0.42f),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(18.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = "每天一次", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (settings.enabled) "将在 ${settings.timeLabel} 提醒今天还需要处理的事项" else "关闭后不会发送每日通知",
                    color = Muted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
            }
            Switch(
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    onSettingsChanged(DailyReminderSettings.fromTimeLabel(enabled = enabled, timeLabel = timeLabel))
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Accent,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = Divider,
                ),
            )
        }
    }
    TimePickerField(
        value = timeLabel,
        onTimeChange = { selectedTime ->
            timeLabel = selectedTime
            onSettingsChanged(DailyReminderSettings.fromTimeLabel(enabled = settings.enabled, timeLabel = selectedTime))
        },
        label = "提醒时间",
        dialogTitle = "选择每日提醒时间",
        modifier = Modifier.fillMaxWidth(),
    )
    SettingsInfoRow(
        label = "提醒范围",
        value = "仅显示今天未过期事项",
        leadingColor = Accent,
    )
    if (!notificationPermissionGranted) {
        SettingsInfoRow(
            label = "通知权限",
            value = "需要开启后才能弹出提醒",
            leadingColor = Danger,
        )
        Button(
            onClick = hapticClick(onClick = onRequestNotificationPermission),
            colors = ButtonDefaults.buttonColors(containerColor = Ink),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("开启通知权限", color = readableOn(Ink), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun StatisticsPanel(
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
    onBack: () -> Unit,
) {
    val stats = buildStatisticsDisplayModel(events = events, todos = todos)
    SettingsHeader(title = "统计", onBack = onBack)
    SettingsGroupTitle("自 ${stats.startedAtLabel} 起，一共完成")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FlipCalendarCounter(
            title = "日程",
            count = stats.completedSchedules,
            color = Amber,
            modifier = Modifier.weight(1f),
        )
        FlipCalendarCounter(
            title = "待办",
            count = stats.completedTodos,
            color = Success,
            startDelayMillis = 90,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FlipCalendarCounter(
    title: String,
    count: Int,
    color: Color,
    modifier: Modifier = Modifier,
    startDelayMillis: Long = 0,
) {
    val view = LocalView.current
    val counter = remember(count) { Animatable(0f) }
    val displayedCount = counter.value.roundToInt().coerceIn(0, count.coerceAtLeast(0))
    val counterFontSize = statisticsCounterFontSize(count)
    val flipPulse = 1f - abs((counter.value % 1f) - 0.5f) * 2f
    LaunchedEffect(count) {
        counter.snapTo(0f)
        delay(startDelayMillis)
        val tickJob = launch {
            val ticks = if (count <= 0) 0 else count.coerceIn(4, 22)
            repeat(ticks) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                delay(34)
            }
        }
        counter.animateTo(
            targetValue = count.toFloat(),
            animationSpec = tween(durationMillis = 880),
        )
        tickJob.cancel()
    }
    Surface(
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.border(1.dp, Divider, RoundedCornerShape(18.dp)),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(bottom = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .background(color.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(modifier = Modifier.size(5.dp).background(color, RoundedCornerShape(999.dp)))
                    Text(text = title, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Box(modifier = Modifier.size(5.dp).background(color, RoundedCornerShape(999.dp)))
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Canvas),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Divider.copy(alpha = 0.82f)),
                )
                Text(
                    text = displayedCount.toString(),
                    color = Ink,
                    fontSize = counterFontSize.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer {
                        rotationX = flipPulse * 42f
                        cameraDistance = 18f * density
                    },
                )
            }
            Text(text = "已完成", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun StatItem(label: String, count: Int, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(999.dp)))
        Text(text = "$label  $count 项", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
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
                    .pressFeedbackClick(onClick = onBack),
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
            .pressFeedbackClick(onClick = onClick, pressedScale = 0.98f),
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
        deepseekApiKey.trim(),
        deepseekBaseUrl.trim(),
        deepseekModel.trim(),
        aliyunApiKey.trim(),
        aliyunAsrUrl.trim(),
    ).joinToString(separator = "\u0000")
}

private fun AgentConnectionSettings.localConfigState(): BackendConnectionState {
    val missing = listOfNotNull(
        "DeepSeek".takeIf { deepseekApiKey.isBlank() },
        "阿里云语音".takeIf { aliyunApiKey.isBlank() },
    )
    return if (missing.isEmpty()) {
        BackendConnectionState(
            indicator = ConnectionIndicator.Connected,
            label = "本地配置就绪",
            detail = "语音和模型将由本机直连用户配置的服务",
        )
    } else {
        BackendConnectionState(
            indicator = ConnectionIndicator.NotConnected,
            label = "缺少${missing.joinToString("、")} Key",
            detail = "填写后即可本地使用",
        )
    }
}

private fun AppThemeMode.swatchColor(): Color = when (this) {
    AppThemeMode.ClassicLight -> Color(0xFF2F7D8C)
    AppThemeMode.Dark -> Color(0xFF000000)
    AppThemeMode.FogBlue -> Color(0xFF5F7E9B)
}

private fun DailyReminderSettings.reminderDetail(notificationPermissionGranted: Boolean): String {
    return when {
        !enabled -> "关闭"
        !notificationPermissionGranted -> "需要通知权限"
        else -> timeLabel
    }
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
            .pressFeedbackClick(onClick = onClick, pressedScale = 0.98f),
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
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .border(1.dp, Divider, RoundedCornerShape(16.dp))
        .let { base ->
            if (onClick == null) base else base.pressFeedbackClick(onClick = onClick, pressedScale = 0.98f)
        }
    Surface(
        color = AccentSoft.copy(alpha = 0.42f),
        shape = RoundedCornerShape(16.dp),
        modifier = rowModifier,
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
            if (onClick != null) {
                Text(text = "›", color = Muted, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun GitHubRepositoryRow(
    onClick: () -> Unit,
) {
    Surface(
        color = AccentSoft.copy(alpha = 0.42f),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(16.dp))
            .pressFeedbackClick(onClick = onClick, pressedScale = 0.98f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_github_mark),
                contentDescription = "GitHub",
                colorFilter = ColorFilter.tint(Ink),
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "GitHub 仓库",
                color = Ink,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "freecodetiger",
                color = Muted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(text = "›", color = Muted, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun UpdateAvailableOverlay(
    latestVersion: String,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        onDismissRequest = onDismiss,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas.copy(alpha = 0.72f))
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = Panel,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 18.dp,
                modifier = Modifier.fillMaxWidth().border(1.dp, Divider, RoundedCornerShape(24.dp)),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = "发现新版本", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "最新版 v$latestVersion 已发布，点击获取最新版。",
                        color = Muted,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Button(
                        onClick = hapticClick(onClick = {
                            uriHandler.openUri(UpdateChecker.ProductReleasePageUrl)
                            onDismiss()
                        }),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text("打开发布页面", color = readableOn(Ink), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = hapticClick(onClick = {
                            uriHandler.openUri(UpdateChecker.GitHubReleasePageUrl)
                            onDismiss()
                        }),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentSoft),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text("打开 GitHub Release", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TextAction(text = "稍后再说", onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun GitHubRepositoryOverlay(
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        onDismissRequest = onDismiss,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas.copy(alpha = 0.72f))
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = Panel,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 18.dp,
                modifier = Modifier.fillMaxWidth().border(1.dp, Divider, RoundedCornerShape(24.dp)),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_github_mark),
                            contentDescription = "GitHub",
                            colorFilter = ColorFilter.tint(Ink),
                            modifier = Modifier.size(24.dp),
                        )
                        Text(text = "GitHub 仓库", color = Ink, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        text = AboutRepositoryInfo.signatureText,
                        color = Muted,
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                    )
                    Button(
                        onClick = hapticClick(onClick = {
                            uriHandler.openUri(AboutRepositoryInfo.RepositoryUrl)
                            onDismiss()
                        }),
                        colors = ButtonDefaults.buttonColors(containerColor = Ink),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Text("为项目点star(推荐)", color = readableOn(Ink), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                    TextAction(text = "关闭", onClick = onDismiss)
                }
            }
        }
    }
}

internal fun buildStatisticsDisplayModel(
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
    today: LocalDate = LocalDate.now(),
): StatisticsDisplayModel {
    val completedScheduleDates = events.mapNotNull { event ->
        runCatching { LocalDate.parse(event.date) }.getOrNull()?.takeIf { it < today }
    }
    val completedTodoDates = todos.mapNotNull { todo ->
        runCatching { LocalDate.parse(todo.dueDate) }.getOrNull()?.takeIf { todo.done }
    }
    val startDate = (completedScheduleDates + completedTodoDates).minOrNull() ?: today
    return StatisticsDisplayModel(
        startedAtLabel = startDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),
        completedSchedules = completedScheduleDates.size,
        completedTodos = todos.count { it.done },
    )
}

internal fun statisticsCounterFontSize(count: Int): Int {
    return when {
        count >= 10000 -> 28
        count >= 1000 -> 32
        else -> 36
    }
}

@Composable
private fun WorkspacePager(
    currentPage: WorkspacePage,
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
    questKind: QuestBookKind,
    questBooks: List<QuestBook>,
    activeQuestTree: QuestBookTree?,
    onQuestKindChange: (QuestBookKind) -> Unit,
    onQuestBookOpen: (QuestBook) -> Unit,
    onQuestBookClose: () -> Unit,
    onQuestBookCreate: (QuestBookKind, String, String, String, String) -> Unit,
    onQuestBookUpdate: (QuestBook) -> Unit,
    onQuestBookDelete: (QuestBook) -> Unit,
    onQuestNodeCreate: (String, String?, String) -> Unit,
    onQuestNodeUpdate: (QuestNode) -> Unit,
    onQuestNodeDelete: (QuestNode) -> Unit,
    onDeleteCommitment: (String) -> Unit,
    onEditCommitment: CommitmentEditRequester,
    onToggleTodo: (TodoItem) -> Unit,
    onPageSettled: (WorkspacePage) -> Unit,
    modifier: Modifier = Modifier,
) {
    val thresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    var widthPx by remember { mutableStateOf(0f) }
    var dragOffsetX by remember { mutableStateOf(0f) }
    val trackOffsetX = remember { Animatable(0f) }
    var trackInitialized by remember { mutableStateOf(false) }
    val animatedDragOffsetX by animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 700f),
        label = "workspace-drag-offset",
    )
    LaunchedEffect(currentPage, widthPx) {
        if (widthPx <= 0f) {
            return@LaunchedEffect
        }
        val target = -currentPage.index * widthPx
        if (!trackInitialized) {
            trackOffsetX.snapTo(target)
            trackInitialized = true
        } else {
            trackOffsetX.animateTo(
                targetValue = target,
                animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
            )
        }
    }
    val pageOffsetX = (trackOffsetX.value + animatedDragOffsetX)
        .coerceIn(-WorkspacePage.Quest.index * widthPx, 0f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onGloballyPositioned { coordinates ->
                widthPx = coordinates.size.width.toFloat()
            }
            .pointerInput(currentPage, thresholdPx, widthPx) {
                detectHorizontalDragGestures(
                    onDragStart = { dragOffsetX = 0f },
                    onDragCancel = { dragOffsetX = 0f },
                    onDragEnd = {
                        val nextPage = settleWorkspacePage(
                            currentPage = currentPage,
                            totalDragX = dragOffsetX,
                            thresholdPx = thresholdPx,
                        )
                        onPageSettled(nextPage)
                        dragOffsetX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        val canMoveTrack = dragOffsetX != 0f ||
                            (dragAmount > 0f && currentPage.index > WorkspacePage.Calendar.index) ||
                            (dragAmount < 0f && currentPage.index < WorkspacePage.Quest.index)
                        if (!canMoveTrack) {
                            return@detectHorizontalDragGestures
                        }
                        change.consume()
                        dragOffsetX = (dragOffsetX + dragAmount).coerceIn(-widthPx, widthPx)
                    },
                )
            },
    ) {
        if (widthPx <= 0f) {
            when (currentPage) {
                WorkspacePage.Calendar -> CalendarSurface(
                    events = events,
                    todos = todos,
                    onDeleteCommitment = onDeleteCommitment,
                    onEditCommitment = onEditCommitment,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                )
                WorkspacePage.Today -> TodayTimeline(
                    events = events,
                    todos = todos,
                    onDeleteCommitment = onDeleteCommitment,
                    onEditCommitment = onEditCommitment,
                    onToggleTodo = onToggleTodo,
                    modifier = Modifier.fillMaxSize(),
                )
                WorkspacePage.Todo -> TodoSurface(
                    todos = todos,
                    onDeleteCommitment = onDeleteCommitment,
                    onEditCommitment = onEditCommitment,
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                )
                WorkspacePage.Quest -> QuestSurface(
                    selectedKind = questKind,
                    books = questBooks,
                    activeTree = activeQuestTree,
                    onKindChange = onQuestKindChange,
                    onBookOpen = onQuestBookOpen,
                    onBookClose = onQuestBookClose,
                    onBookCreate = onQuestBookCreate,
                    onBookUpdate = onQuestBookUpdate,
                    onBookDelete = onQuestBookDelete,
                    onNodeCreate = onQuestNodeCreate,
                    onNodeUpdate = onQuestNodeUpdate,
                    onNodeDelete = onQuestNodeDelete,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            return@Box
        }
        CalendarSurface(
            events = events,
            todos = todos,
            onDeleteCommitment = onDeleteCommitment,
            onEditCommitment = onEditCommitment,
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(
                        x = (WorkspacePage.Calendar.index * widthPx + pageOffsetX).roundToInt(),
                        y = 0,
                    )
                }
                .verticalScroll(rememberScrollState()),
        )
        TodayTimeline(
            events = events,
            todos = todos,
            onDeleteCommitment = onDeleteCommitment,
            onEditCommitment = onEditCommitment,
            onToggleTodo = onToggleTodo,
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(
                        x = (WorkspacePage.Today.index * widthPx + pageOffsetX).roundToInt(),
                        y = 0,
                    )
                },
        )
        TodoSurface(
            todos = todos,
            onDeleteCommitment = onDeleteCommitment,
            onEditCommitment = onEditCommitment,
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(
                        x = (WorkspacePage.Todo.index * widthPx + pageOffsetX).roundToInt(),
                        y = 0,
                    )
                }
                .verticalScroll(rememberScrollState()),
        )
        QuestSurface(
            selectedKind = questKind,
            books = questBooks,
            activeTree = activeQuestTree,
            onKindChange = onQuestKindChange,
            onBookOpen = onQuestBookOpen,
            onBookClose = onQuestBookClose,
            onBookCreate = onQuestBookCreate,
            onBookUpdate = onQuestBookUpdate,
            onBookDelete = onQuestBookDelete,
            onNodeCreate = onQuestNodeCreate,
            onNodeUpdate = onQuestNodeUpdate,
            onNodeDelete = onQuestNodeDelete,
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(
                        x = (WorkspacePage.Quest.index * widthPx + pageOffsetX).roundToInt(),
                        y = 0,
                    )
                },
        )
    }
}

@Composable
private fun QuestEdgeHint(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pulse = rememberInfiniteTransition(label = "quest-edge-pulse")
    val alpha by pulse.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.72f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1300),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "quest-edge-alpha",
    )
    Box(
        modifier = modifier
            .width(18.dp)
            .height(78.dp)
            .pressFeedbackClick(onClick = onClick, pressedScale = 0.96f),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "›",
                color = Amber.copy(alpha = 0.34f * alpha),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.offset(x = (-1).dp),
            )
            Text(
                text = "›",
                color = Amber.copy(alpha = 0.34f * alpha),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.offset(x = (-1).dp),
            )
        }
    }
}

@Composable
private fun QuestSurface(
    selectedKind: QuestBookKind,
    books: List<QuestBook>,
    activeTree: QuestBookTree?,
    onKindChange: (QuestBookKind) -> Unit,
    onBookOpen: (QuestBook) -> Unit,
    onBookClose: () -> Unit,
    onBookCreate: (QuestBookKind, String, String, String, String) -> Unit,
    onBookUpdate: (QuestBook) -> Unit,
    onBookDelete: (QuestBook) -> Unit,
    onNodeCreate: (String, String?, String) -> Unit,
    onNodeUpdate: (QuestNode) -> Unit,
    onNodeDelete: (QuestNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bookEditor by remember { mutableStateOf<QuestBookEditorState?>(null) }
    var nodeEditor by remember { mutableStateOf<QuestNodeEditorState?>(null) }
    var deleteBookCandidate by remember { mutableStateOf<QuestBook?>(null) }
    var deleteNodeCandidate by remember { mutableStateOf<QuestNode?>(null) }
    var quickAddParentId by remember(activeTree?.book?.id) { mutableStateOf<String?>(null) }
    var quickTitle by remember(activeTree?.book?.id, quickAddParentId) { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        if (activeTree == null) {
            QuestBookshelf(
                selectedKind = selectedKind,
                books = books,
                onKindChange = onKindChange,
                onBookOpen = onBookOpen,
                onCreateBook = { bookEditor = QuestBookEditorState(selectedKind) },
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            )
        } else {
            QuestTreeBook(
                tree = activeTree,
                quickAddParentId = quickAddParentId,
                quickTitle = quickTitle,
                onQuickTitleChange = { quickTitle = it },
                onBack = onBookClose,
                onEditBook = { bookEditor = QuestBookEditorState(activeTree.book.kind, activeTree.book) },
                onDeleteBook = { deleteBookCandidate = activeTree.book },
                onSetQuickParent = { quickAddParentId = it },
                onQuickAdd = {
                    val title = quickTitle.trim()
                    if (title.isNotBlank()) {
                        onNodeCreate(activeTree.book.id, quickAddParentId, title)
                        quickTitle = ""
                    }
                },
                onToggleNode = { node -> onNodeUpdate(node.copy(done = !node.done)) },
                onToggleExpanded = { node -> onNodeUpdate(node.copy(expanded = !node.expanded)) },
                onEditNode = { nodeEditor = QuestNodeEditorState(it) },
                onDeleteNode = { deleteNodeCandidate = it },
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            )
        }
        bookEditor?.let { editor ->
            QuestBookEditorOverlay(
                state = editor,
                onDismiss = { bookEditor = null },
                onSave = { title, description, location, targetDate ->
                    val book = editor.book
                    if (book == null) {
                        onBookCreate(editor.kind, title, description, location, targetDate)
                    } else {
                        onBookUpdate(
                            book.copy(
                                title = title.trim().ifBlank { book.title },
                                description = description.trim(),
                                location = location.trim(),
                                targetDate = targetDate.trim(),
                            ),
                        )
                    }
                    bookEditor = null
                },
            )
        }
        nodeEditor?.let { editor ->
            QuestNodeEditorOverlay(
                node = editor.node,
                onDismiss = { nodeEditor = null },
                onSave = { title, description, location, targetDate ->
                    onNodeUpdate(
                        editor.node.copy(
                            title = title.trim().ifBlank { editor.node.title },
                            description = description.trim(),
                            location = location.trim(),
                            targetDate = targetDate.trim(),
                        ),
                    )
                    nodeEditor = null
                },
            )
        }
        deleteBookCandidate?.let { book ->
            QuestDeleteConfirmOverlay(
                title = "删除任务书",
                message = "会删除「${book.title}」和里面的所有小目标。",
                onDismiss = { deleteBookCandidate = null },
                onConfirm = {
                    onBookDelete(book)
                    deleteBookCandidate = null
                },
            )
        }
        deleteNodeCandidate?.let { node ->
            QuestDeleteConfirmOverlay(
                title = "删除小目标",
                message = "会删除「${node.title}」和它下面的所有子目标。",
                onDismiss = { deleteNodeCandidate = null },
                onConfirm = {
                    onNodeDelete(node)
                    deleteNodeCandidate = null
                },
            )
        }
    }
}

@Composable
private fun QuestBookshelf(
    selectedKind: QuestBookKind,
    books: List<QuestBook>,
    onKindChange: (QuestBookKind) -> Unit,
    onBookOpen: (QuestBook) -> Unit,
    onCreateBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuestKindChip(
                label = "主线",
                selected = selectedKind == QuestBookKind.Main,
                onClick = { onKindChange(QuestBookKind.Main) },
                modifier = Modifier.weight(1f),
            )
            QuestKindChip(
                label = "支线",
                selected = selectedKind == QuestBookKind.Side,
                onClick = { onKindChange(QuestBookKind.Side) },
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "任务书架", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Text(text = "${selectedKind.label}任务，每本书都是一棵独立目标树", color = Muted, fontSize = 13.sp)
            }
            Button(
                onClick = hapticClick(onClick = onCreateBook),
                colors = ButtonDefaults.buttonColors(containerColor = Ink),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("+ 新书", color = readableOn(Ink), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (books.isEmpty()) {
            EmptyState(
                title = if (selectedKind == QuestBookKind.Main) "还没有主线任务书" else "还没有支线任务书",
                detail = "创建一本书，把长期追求拆成可以不断展开的小目标。",
            )
        } else {
            books.forEach { book ->
                QuestBookCard(book = book, onOpen = { onBookOpen(book) })
            }
        }
        Box(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun QuestKindChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) Ink else AccentSoft,
        shape = RoundedCornerShape(999.dp),
        modifier = modifier
            .height(42.dp)
            .pressFeedbackClick(onClick = onClick, pressedScale = 0.97f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (selected) readableOn(Ink) else Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun QuestBookCard(
    book: QuestBook,
    onOpen: () -> Unit,
) {
    val accent = if (book.kind == QuestBookKind.Main) Amber else Success
    Surface(
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Divider, RoundedCornerShape(18.dp))
            .pressFeedbackClick(onClick = onOpen, pressedScale = 0.98f),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(color = accent.copy(alpha = 0.16f), shape = RoundedCornerShape(14.dp)) {
                Box(modifier = Modifier.size(width = 46.dp, height = 58.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (book.kind == QuestBookKind.Main) "主" else "支",
                        color = accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(text = book.title, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                QuestMetaLine(location = book.location, targetDate = book.targetDate)
                if (book.description.isNotBlank()) {
                    Text(text = book.description, color = Muted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                }
            }
            Text(text = "›", color = Muted, fontSize = 24.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun QuestTreeBook(
    tree: QuestBookTree,
    quickAddParentId: String?,
    quickTitle: String,
    onQuickTitleChange: (String) -> Unit,
    onBack: () -> Unit,
    onEditBook: () -> Unit,
    onDeleteBook: () -> Unit,
    onSetQuickParent: (String?) -> Unit,
    onQuickAdd: () -> Unit,
    onToggleNode: (QuestNode) -> Unit,
    onToggleExpanded: (QuestNode) -> Unit,
    onEditNode: (QuestNode) -> Unit,
    onDeleteNode: (QuestNode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleRows = remember(tree.nodes) { visibleQuestRows(tree.nodes) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsHeader(title = tree.book.title, onBack = onBack)
        Surface(
            color = Panel,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth().border(1.dp, Divider, RoundedCornerShape(20.dp)),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(text = "${tree.book.kind.label}任务书", color = Amber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = "${tree.completedNodeCount}/${tree.totalNodeCount}", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                QuestMetaLine(location = tree.book.location, targetDate = tree.book.targetDate)
                if (tree.book.description.isNotBlank()) {
                    Text(text = tree.book.description, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                }
                Surface(color = Divider, shape = RoundedCornerShape(999.dp), modifier = Modifier.fillMaxWidth().height(8.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(tree.progress.coerceIn(0f, 1f))
                            .background(Amber, RoundedCornerShape(999.dp)),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextAction(text = "编辑书", onClick = onEditBook)
                    TextAction(text = "删除书", color = Danger, onClick = onDeleteBook)
                }
            }
        }
        if (quickAddParentId == null) {
            QuickQuestAddRow(
                parentLabel = "顶层目标",
                title = quickTitle,
                onTitleChange = onQuickTitleChange,
                onSubmit = onQuickAdd,
                onTopLevel = { onSetQuickParent(null) },
            )
        }
        if (visibleRows.isEmpty()) {
            EmptyState(title = "这本书还没有目标", detail = "先添加一个顶层目标，再继续拆成子目标。")
        } else {
            visibleRows.forEach { row ->
                QuestNodeRow(
                    row = row,
                    onToggleDone = { onToggleNode(row.node) },
                    onToggleExpanded = { onToggleExpanded(row.node) },
                    onAddChild = {
                        if (!row.node.expanded) {
                            onToggleExpanded(row.node)
                        }
                        onSetQuickParent(row.node.id)
                    },
                    onEdit = { onEditNode(row.node) },
                    onDelete = { onDeleteNode(row.node) },
                    quickAddActive = quickAddParentId == row.node.id,
                    quickTitle = quickTitle,
                    onQuickTitleChange = onQuickTitleChange,
                    onQuickAdd = onQuickAdd,
                    onTopLevelAdd = { onSetQuickParent(null) },
                )
            }
        }
        Box(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun QuickQuestAddRow(
    parentLabel: String,
    title: String,
    onTitleChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onTopLevel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = AccentSoft.copy(alpha = 0.5f),
        shape = RoundedCornerShape(18.dp),
        modifier = modifier.fillMaxWidth().border(1.dp, Divider, RoundedCornerShape(18.dp)),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = "添加到：$parentLabel", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextAction(text = "顶层", onClick = onTopLevel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    placeholder = { Text("快速写下一个小目标", fontSize = 13.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = appTextFieldColors(),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = hapticClick(onClick = onSubmit),
                    enabled = title.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Ink),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(52.dp),
                ) {
                    Text("+", color = readableOn(Ink), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun QuestNodeRow(
    row: QuestVisibleNode,
    onToggleDone: () -> Unit,
    onToggleExpanded: () -> Unit,
    onAddChild: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    quickAddActive: Boolean,
    quickTitle: String,
    onQuickTitleChange: (String) -> Unit,
    onQuickAdd: () -> Unit,
    onTopLevelAdd: () -> Unit,
) {
    val node = row.node
    val indent = questNodeIndentDp(row.depth).dp
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            color = if (node.done) SuccessSoft.copy(alpha = 0.62f) else Panel,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = indent)
                .border(1.dp, Divider, RoundedCornerShape(16.dp)),
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (row.childCount > 0) {
                            if (node.expanded) "⌄" else "›"
                        } else {
                            "•"
                        },
                        color = Muted,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .size(28.dp)
                            .pressFeedbackClick(
                                onClick = if (row.childCount > 0) onToggleExpanded else ({ }),
                                pressedScale = 0.9f,
                            ),
                        textAlign = TextAlign.Center,
                    )
                    Surface(
                        color = if (node.done) Success else Color.Transparent,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .size(24.dp)
                            .border(1.5.dp, if (node.done) Success else Muted, RoundedCornerShape(999.dp))
                            .pressFeedbackClick(onClick = onToggleDone, pressedScale = 0.88f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (node.done) {
                                Text("✓", color = readableOn(Success), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = node.title,
                            color = if (node.done) Muted else Ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (node.done) TextDecoration.LineThrough else TextDecoration.None,
                        )
                        QuestMetaLine(location = node.location, targetDate = node.targetDate)
                    }
                }
                if (node.description.isNotBlank()) {
                    Text(text = node.description, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextAction(text = "+ 子目标", onClick = onAddChild)
                    TextAction(text = "编辑", onClick = onEdit)
                    TextAction(text = "删除", color = Danger, onClick = onDelete)
                }
            }
        }
        if (quickAddActive) {
            QuickQuestAddRow(
                parentLabel = node.title,
                title = quickTitle,
                onTitleChange = onQuickTitleChange,
                onSubmit = onQuickAdd,
                onTopLevel = onTopLevelAdd,
                modifier = Modifier.padding(start = questNodeIndentDp(row.depth + 1).dp),
            )
        }
    }
}

@Composable
private fun QuestMetaLine(
    location: String,
    targetDate: String,
) {
    val parts = listOfNotNull(
        location.takeIf { it.isNotBlank() }?.let { "地点 $it" },
        targetDate.takeIf { it.isNotBlank() }?.let { "预期 $it" },
    )
    if (parts.isNotEmpty()) {
        Text(
            text = parts.joinToString("  ·  "),
            color = Muted,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun QuestBookEditorOverlay(
    state: QuestBookEditorState,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    QuestFieldsOverlay(
        title = if (state.book == null) "新建${state.kind.label}任务书" else "编辑任务书",
        initialTitle = state.book?.title.orEmpty(),
        initialDescription = state.book?.description.orEmpty(),
        initialLocation = state.book?.location.orEmpty(),
        initialTargetDate = state.book?.targetDate.orEmpty(),
        onDismiss = onDismiss,
        onSave = onSave,
    )
}

@Composable
private fun QuestNodeEditorOverlay(
    node: QuestNode,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    QuestFieldsOverlay(
        title = "编辑小目标",
        initialTitle = node.title,
        initialDescription = node.description,
        initialLocation = node.location,
        initialTargetDate = node.targetDate,
        onDismiss = onDismiss,
        onSave = onSave,
    )
}

@Composable
private fun QuestFieldsOverlay(
    title: String,
    initialTitle: String,
    initialDescription: String,
    initialLocation: String,
    initialTargetDate: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit,
) {
    var questTitle by remember(initialTitle) { mutableStateOf(initialTitle) }
    var description by remember(initialDescription) { mutableStateOf(initialDescription) }
    var location by remember(initialLocation) { mutableStateOf(initialLocation) }
    var targetDate by remember(initialTargetDate) { mutableStateOf(initialTargetDate) }
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        onDismissRequest = onDismiss,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas.copy(alpha = 0.72f))
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = Panel,
                shape = RoundedCornerShape(24.dp),
                shadowElevation = 18.dp,
                modifier = Modifier.fillMaxWidth().border(1.dp, Divider, RoundedCornerShape(24.dp)),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsHeader(title = title)
                    OutlinedTextField(
                        value = questTitle,
                        onValueChange = { questTitle = it },
                        label = { Text("标题", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = appTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("描述", fontSize = 12.sp) },
                        shape = RoundedCornerShape(14.dp),
                        colors = appTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("地点", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = appTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = targetDate,
                        onValueChange = { targetDate = it },
                        label = { Text("预期达成时间（可不填）", fontSize = 12.sp) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = appTextFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = hapticClick(onClick = onDismiss),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentSoft),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) {
                            Text("取消", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = hapticClick(onClick = { onSave(questTitle, description, location, targetDate) }),
                            enabled = questTitle.trim().isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Ink),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) {
                            Text("保存", color = readableOn(Ink), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuestDeleteConfirmOverlay(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    Popup(
        alignment = Alignment.Center,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
        onDismissRequest = onDismiss,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Canvas.copy(alpha = 0.72f))
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                color = Panel,
                shape = RoundedCornerShape(22.dp),
                shadowElevation = 18.dp,
                modifier = Modifier.fillMaxWidth().border(1.dp, Divider, RoundedCornerShape(22.dp)),
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = title, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(text = message, color = Muted, fontSize = 14.sp, lineHeight = 20.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = hapticClick(onClick = onDismiss),
                            colors = ButtonDefaults.buttonColors(containerColor = AccentSoft),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) {
                            Text("取消", color = Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Button(
                            onClick = hapticClick(onClick = onConfirm),
                            colors = ButtonDefaults.buttonColors(containerColor = Danger),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) {
                            Text("删除", color = readableOn(Danger), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

internal fun visibleQuestRows(nodes: List<QuestNode>): List<QuestVisibleNode> {
    val byParent = nodes.groupBy { it.parentId }
    val result = mutableListOf<QuestVisibleNode>()

    fun visit(parentId: String?, depth: Int) {
        byParent[parentId]
            .orEmpty()
            .sortedWith(compareBy<QuestNode> { it.sortOrder }.thenBy { it.createdAt })
            .forEach { node ->
                val childCount = byParent[node.id].orEmpty().size
                result += QuestVisibleNode(node = node, depth = depth, childCount = childCount)
                if (node.expanded) {
                    visit(node.id, depth + 1)
                }
            }
    }

    visit(parentId = null, depth = 0)
    return result
}

@Composable
private fun TodayTimeline(
    events: List<ScheduleEvent>,
    todos: List<TodoItem>,
    onDeleteCommitment: (String) -> Unit,
    onEditCommitment: CommitmentEditRequester,
    onToggleTodo: (TodoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val todayEvents = events
        .filterForDate(today)
        .sortedBy { event -> event.timeRange }
    val upcomingEvents = events
        .filterAfter(today)
        .sortedWith(compareBy<ScheduleEvent> { it.date.ifBlank { today.toString() } }.thenBy { it.timeRange })
    val homeTodos = todos
        .visibleForHome(today)
        .sortedForHome(today)
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
            onToggleDone = onToggleTodo,
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
    onToggleDone: (TodoItem) -> Unit,
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
                if (todos.isEmpty()) {
                    QuietTimelineEmpty(text = "没有待办")
                } else {
                    todos.forEachIndexed { index, todo ->
                        HomeTodoTimelineRow(
                            todo = todo,
                            showConnector = index != todos.lastIndex,
                            onDelete = { onDeleteCommitment(todo.id) },
                            onEdit = onEditCommitment,
                            onToggleDone = { onToggleDone(todo) },
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
    onToggleDone: (() -> Unit)? = null,
) {
    val color = if (todo.done) Muted else todo.priority.color()
    val today = LocalDate.now()
    val lockOverdue = !todo.canEditOrDelete(today)
    val allowToggle = todo.canToggle(today) && !todo.done // only toggle to done, not back (re-open via uncheck)
    val allowToggleOff = todo.done && todo.canEditOrDelete(today) // cannot uncheck overdue done
    var expanded by remember(todo.stableUiKey(), todo.detail) { mutableStateOf(false) }
    var confirmingDelete by remember(todo.stableUiKey()) { mutableStateOf(false) }
    val hasNote = todo.detail.isNotBlank()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 240))
            .padding(horizontal = 6.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                    .border(2.dp, if (todo.done) Muted else color, RoundedCornerShape(4.dp)),
            )
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(if (showConnector) 22.dp else 9.dp)
                    .background(Color(0xFFCADADD), RoundedCornerShape(999.dp)),
            )
        }
        // Circular checkbox
        if (onToggleDone != null && (allowToggle || allowToggleOff)) {
            val checkColor = if (todo.done) Success else Divider
            val checkScale by animateFloatAsState(
                targetValue = if (todo.done) 1f else 0.3f,
                animationSpec = spring(dampingRatio = 0.45f, stiffness = 620f),
                label = "check-scale",
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (todo.done) checkColor.copy(alpha = 0.12f) else Color.Transparent)
                    .border(1.5.dp, checkColor, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onToggleDone() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "✓",
                    color = Success,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.graphicsLayer {
                        alpha = if (todo.done) 1f else 0f
                        scaleX = checkScale
                        scaleY = checkScale
                    },
                )
            }
        }
        Text(
            text = todo.relativeDueLabel(),
            color = if (todo.done) Muted else color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(52.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .then(
                    if (lockOverdue) Modifier
                    else Modifier.commitmentLongPressMenu(
                        target = todo.editTargetOrNull(),
                        onEdit = onEdit,
                        onTap = { if (hasNote) expanded = !expanded },
                    )
                ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = todo.title,
                    color = if (todo.done) Muted else Ink,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                )
                if (hasNote && !todo.done) {
                    Text(text = if (expanded) "收起" else "备注", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (!todo.done) {
                ExpandableNoteText(
                    note = todo.detail,
                    expanded = expanded,
                    collapsedMaxLines = 1,
                    fontSize = 12,
                    lineHeight = 15,
                )
            }
        }
        if (todo.id.isNotBlank() && !lockOverdue && !todo.done) {
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
            TimePickerField(value = startTime, onTimeChange = onStartTimeChange, label = "开始", modifier = Modifier.weight(1f), dialogTitle = "选择开始时间")
            TimePickerField(value = endTime, onTimeChange = onEndTimeChange, label = "结束", modifier = Modifier.weight(1f), dialogTitle = "选择结束时间")
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
        PriorityQuadrantPicker(priority = priority, onPriorityChange = onPriorityChange)
        CompactEditField(value = notes, onValueChange = onNotesChange, label = "备注", singleLine = false)
        InlineEditActions(onSave = onSave, onCancel = onCancel)
    }
}

@Composable
private fun PriorityQuadrantPicker(
    priority: String,
    onPriorityChange: (String) -> Unit,
) {
    val quadrants = listOf(
        "q1" to QuadrantInfo(label = "重要且紧急", sub = "立刻做", color = Danger, icon = "⇧"),
        "q2" to QuadrantInfo(label = "重要不紧急", sub = "计划做", color = Ink, icon = "⇧"),
        "q3" to QuadrantInfo(label = "紧急不重要", sub = "委派做", color = Risk, icon = "⤓"),
        "q4" to QuadrantInfo(label = "不重要不紧急", sub = "最后做", color = Success, icon = "⤓"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Header labels
            Box(modifier = Modifier.weight(0.15f))
            Text(
                text = "紧急",
                fontSize = 10.sp,
                color = Muted,
                modifier = Modifier.weight(0.425f),
                textAlign = TextAlign.Center,
            )
            Text(
                text = "不紧急",
                fontSize = 10.sp,
                color = Muted,
                modifier = Modifier.weight(0.425f),
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Row label
            Text(
                text = "重要",
                fontSize = 10.sp,
                color = Muted,
                modifier = Modifier.weight(0.15f).align(Alignment.CenterVertically),
            )
            // Q1: Urgent + Important
            val q1 = quadrants[0]
            QuadrantCell(
                info = q1.second,
                selected = priority == q1.first,
                onClick = { onPriorityChange(q1.first) },
                modifier = Modifier.weight(0.425f),
            )
            // Q2: Important + Not Urgent
            val q2 = quadrants[1]
            QuadrantCell(
                info = q2.second,
                selected = priority == q2.first,
                onClick = { onPriorityChange(q2.first) },
                modifier = Modifier.weight(0.425f),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Row label
            Text(
                text = "不重要",
                fontSize = 10.sp,
                color = Muted,
                modifier = Modifier.weight(0.15f).align(Alignment.CenterVertically),
            )
            // Q3: Urgent + Not Important
            val q3 = quadrants[2]
            QuadrantCell(
                info = q3.second,
                selected = priority == q3.first,
                onClick = { onPriorityChange(q3.first) },
                modifier = Modifier.weight(0.425f),
            )
            // Q4: Not Urgent + Not Important
            val q4 = quadrants[3]
            QuadrantCell(
                info = q4.second,
                selected = priority == q4.first,
                onClick = { onPriorityChange(q4.first) },
                modifier = Modifier.weight(0.425f),
            )
        }
    }
}

private data class QuadrantInfo(
    val label: String,
    val sub: String,
    val color: Color,
    val icon: String,
)

@Composable
private fun QuadrantCell(
    info: QuadrantInfo,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (selected) info.color.copy(alpha = 0.15f) else Panel,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
            .border(
                1.5.dp,
                if (selected) info.color else Divider,
                RoundedCornerShape(10.dp),
            )
            .pressFeedbackClick(onClick = onClick, pressedScale = 0.96f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = info.label,
                color = if (selected) info.color else Muted,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
            Text(
                text = info.sub,
                color = if (selected) info.color.copy(alpha = 0.7f) else Muted.copy(alpha = 0.6f),
                fontSize = 9.sp,
            )
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
                .pressFeedbackClick(onClick = onCancel),
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
            modifier = Modifier.pressFeedbackClick(onClick = onSave),
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

    PickerFieldContainer(modifier = modifier) {
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
        PickerFieldTapTarget(onClick = { showDialog = true })
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
    dialogTitle: String = "选择时间",
) {
    var showDialog by remember { mutableStateOf(false) }

    val (hour, minute) = remember(value) {
        val parts = value.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val m = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        h to m
    }

    PickerFieldContainer(modifier = modifier) {
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
        PickerFieldTapTarget(onClick = { showDialog = true })
    }

    if (showDialog) {
        val view = LocalView.current
        val timePickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true,
        )
        var lastHapticTime by remember { mutableStateOf(hour to minute) }
        LaunchedEffect(timePickerState.hour, timePickerState.minute) {
            val current = timePickerState.hour to timePickerState.minute
            if (current != lastHapticTime) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                lastHapticTime = current
            }
        }

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
                title = { Text(dialogTitle, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Ink) },
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
private fun PickerFieldContainer(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier, content = content)
}

@Composable
private fun BoxScope.PickerFieldTapTarget(
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .pressFeedbackClick(onClick = onClick, pressedScale = 0.98f),
    )
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
                .pressFeedbackClick(onClick = onToggle, pressedScale = 0.9f),
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
            .pressFeedbackClick(onClick = onClick, pressedScale = 0.97f),
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
    var priority by remember { mutableStateOf("q2") }
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
        val today = LocalDate.now()
        val isPastDate = selectedDate < today
        val selectedEvents = events.filter { it.date == selectedDate.toString() }
        val selectedTodos = todos.filter { it.dueDate == selectedDate.toString() }
        SectionHeader(
            title = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日",
            caption = "",
        )
        if (selectedEvents.isEmpty() && selectedTodos.isEmpty()) {
            EmptyState(title = "—", detail = "")
        } else {
            selectedEvents.forEach { event ->
                if (isPastDate) {
                    // Overdue schedule - read only card
                    CalendarOverdueEventRow(event = event)
                } else {
                    EventCard(
                        event = event,
                        onDelete = { onDeleteCommitment(event.id) },
                        onEdit = onEditCommitment,
                    )
                }
            }
            selectedTodos.forEach { todo ->
                if (isPastDate) {
                    // Overdue todo - read only card
                    CalendarOverdueTodoRow(todo = todo)
                } else {
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
    val todosByDate = todos.groupBy { it.dueDate }
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
    val today = LocalDate.now()
    val eventCount = events.size
    val hasActiveTodo = todos.any { !it.done }
    val hasOverdueSchedule = date != null && date < today && events.isNotEmpty()
    val hasOverdueTodo = date != null && date < today && todos.any { !it.done }
    val isToday = date == today
    val isPast = date != null && date < today
    val loadColor = when {
        hasActiveTodo && !isPast -> Danger
        eventCount >= 4 -> Risk
        eventCount >= 2 -> Accent
        eventCount == 1 -> Success
        else -> Muted
    }
    val background = when {
        selected -> AccentSoft
        hasActiveTodo && !isPast -> DangerSoft
        eventCount >= 4 -> RiskSoft
        eventCount > 0 -> SuccessSoft
        isPast && (hasOverdueSchedule || hasOverdueTodo) -> Canvas.copy(alpha = 0.6f)
        else -> Canvas
    }
    val animatedBg by animateColorAsState(
        targetValue = background,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "day-bg",
    )
    val borderTarget = if (isToday || selected) 2.dp else 0.dp
    val animatedBorder by animateFloatAsState(
        targetValue = if (isToday || selected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 450f),
        label = "day-border",
    )
    val borderColor = if (hasActiveTodo && !isPast) Danger else Accent
    val cellScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "day-scale",
    )
    Surface(
        color = animatedBg,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .height(58.dp)
            .graphicsLayer {
                scaleX = cellScale
                scaleY = cellScale
            }
            .border(
                width = (borderTarget * animatedBorder),
                color = borderColor.copy(alpha = animatedBorder),
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
                    color = if (date == null) Divider else if (isPast && (hasOverdueSchedule || hasOverdueTodo)) Muted else loadColor,
                    fontSize = 13.sp,
                    fontWeight = if (eventCount > 0 || hasActiveTodo || isToday || (isPast && (hasOverdueSchedule || hasOverdueTodo))) FontWeight.SemiBold else FontWeight.Normal,
                )
                if ((eventCount > 0 || hasActiveTodo) && !isPast) {
                    Surface(color = loadColor, shape = RoundedCornerShape(999.dp)) {
                        Text(
                            text = if (hasActiveTodo) "!" else eventCount.toString(),
                            color = readableOn(loadColor),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                if (hasOverdueSchedule) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Amber, RoundedCornerShape(999.dp)),
                    )
                }
                if (hasOverdueTodo) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Muted.copy(alpha = 0.5f), RoundedCornerShape(999.dp)),
                    )
                }
                if (!isPast) {
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
}

@Composable
private fun CalendarOverdueEventRow(event: ScheduleEvent) {
    Surface(
        color = AmberSoft.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(6.dp).background(Amber, RoundedCornerShape(999.dp)))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = event.title, color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = "${event.timeRange} · 已完成",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun CalendarOverdueTodoRow(todo: TodoItem) {
    Surface(
        color = if (todo.done) SuccessSoft.copy(alpha = 0.3f) else Panel.copy(alpha = 0.5f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (todo.done) Success else Muted.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.title,
                    color = if (todo.done) Muted else Ink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (todo.done) TextDecoration.LineThrough else TextDecoration.None,
                )
                Text(
                    text = if (todo.done) "已完成" else "未完成",
                    color = if (todo.done) Success else Muted,
                    fontSize = 12.sp,
                )
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
    val today = LocalDate.now()
    val active = todos
        .visibleForTodos(today)
        .filterNot { it.done }
        .filter { it.matchesTodoQuery(normalizedQuery) }
        .sortedByDueDate()
    val done = todos
        .visibleForTodos(today)
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
    var confirmingDelete by remember(event.id) { mutableStateOf(false) }
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
                    .pressFeedbackClick(
                        onClick = onDelete,
                        pressedScale = 0.96f,
                        hapticFeedback = HapticFeedbackConstants.CONFIRM,
                    ),
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
                .pointerInput(todo.stableUiKey()) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (!todo.done) {
                                targetOffsetX = if (targetOffsetX < -revealWidthPx * 0.42f) {
                                    -revealWidthPx
                                } else {
                                    0f
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (!todo.done) {
                                targetOffsetX = (targetOffsetX + dragAmount).coerceIn(-revealWidthPx, 0f)
                            }
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
        modifier = Modifier.pressFeedbackClick(onClick = onClick),
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
            modifier = Modifier.pressFeedbackClick(
                onClick = onConfirm,
                hapticFeedback = HapticFeedbackConstants.CONFIRM,
            ),
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
            modifier = Modifier.pressFeedbackClick(onClick = onCancel),
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
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(title, detail) { visible = true }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "empty-fade",
    )
    Surface(
        color = Panel,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
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
    currentPage: WorkspacePage,
    selectedTab: ScheduleTab,
    onTodaySelected: () -> Unit,
    onTodoSelected: () -> Unit,
    agentClient: AgentClient?,
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
                currentPage = currentPage,
                selectedTab = selectedTab,
                onTodaySelected = onTodaySelected,
                onTodoSelected = onTodoSelected,
                agentClient = agentClient,
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
    currentPage: WorkspacePage,
    selectedTab: ScheduleTab,
    onTodaySelected: () -> Unit,
    onTodoSelected: () -> Unit,
    agentClient: AgentClient?,
    asrClient: RealtimeAsrClient?,
    connectionSettings: AgentConnectionSettings,
    commitmentsProvider: () -> AgentCommitmentsPayload,
    proposalApplier: (AgentProposal) -> AgentApplyResult,
    onCommitmentsChanged: () -> Unit,
    onVoiceRecordingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var status by remember { mutableStateOf(if (agentClient == null) "本地 Agent 不可用" else "准备说话") }
    var submittedText by remember { mutableStateOf<String?>(null) }
    var phase by remember { mutableStateOf(ComposerPhase.Idle) }
    var proposal by remember { mutableStateOf<AgentProposal?>(null) }
    var activeRequestId by remember { mutableStateOf(0) }
    var isListening by remember { mutableStateOf(false) }
    var voiceText by remember { mutableStateOf("") }
    var voiceCancelArmed by remember { mutableStateOf(false) }
    var refinementBaseText by remember { mutableStateOf<String?>(null) }
    var refinementBaseProposal by remember { mutableStateOf<AgentProposal?>(null) }
    var conversationSessionId by remember { mutableStateOf(newVoiceConversationSessionId()) }
    val hasModelApiKey = connectionSettings.deepseekApiKey.isNotBlank()
    val hasAsrApiKey = connectionSettings.aliyunApiKey.isNotBlank()
    val voiceAvailableOnPage = currentPage != WorkspacePage.Quest
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    LaunchedEffect(isListening) {
        onVoiceRecordingChanged(isListening)
    }

    fun clearConversationTurn(
        nextStatus: String = "准备说话",
        nextPhase: ComposerPhase = ComposerPhase.Idle,
        startNewSession: Boolean = true,
    ) {
        submittedText = null
        proposal = null
        voiceText = ""
        voiceCancelArmed = false
        refinementBaseText = null
        refinementBaseProposal = null
        isListening = false
        phase = nextPhase
        status = nextStatus
        if (startNewSession) {
            conversationSessionId = newVoiceConversationSessionId()
        }
    }

    fun submit(value: String, displayText: String = value) {
        val prompt = value.trim()
        if (prompt.isBlank()) {
            status = "没有识别到内容"
            return
        }
        if (!hasModelApiKey) {
            phase = ComposerPhase.Error
            status = "请先在设置里填写 DeepSeek API Key"
            return
        }
        submittedText = displayText.trim().ifBlank { prompt }
        proposal = null
        val client = agentClient
        if (client == null) {
            phase = ComposerPhase.Error
            status = "本地 Agent 不可用"
            return
        }
        phase = ComposerPhase.Working
        status = "正在整理"
        activeRequestId += 1
        val requestId = activeRequestId
        Thread {
            val result = client.propose(
                text = prompt,
                sessionId = conversationSessionId,
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

    fun startVoiceCapture(refining: Boolean) {
        if (!hasModelApiKey) {
            status = "请先在设置里填写 DeepSeek API Key"
            phase = ComposerPhase.Error
            return
        }
        if (!hasAsrApiKey) {
            status = "请先在设置里填写阿里云语音 API Key"
            phase = ComposerPhase.Error
            return
        }
        val client = asrClient
        if (client == null) {
            status = "语音暂不可用"
            phase = ComposerPhase.Error
            return
        }
        activeRequestId += 1
        if (refining) {
            refinementBaseText = submittedText?.takeIf { it.isNotBlank() } ?: voiceText
            refinementBaseProposal = proposal
        } else {
            clearConversationTurn(startNewSession = true)
        }
        proposal = null
        voiceText = ""
        voiceCancelArmed = false
        isListening = true
        phase = ComposerPhase.Idle
        status = if (refining) "正在听修正" else "正在听"
        client.start(object : RealtimeAsrCallback {
            override fun onPartial(text: String) {
                mainHandler.post {
                    voiceText = text
                    status = if (refining) "正在听修正" else "正在听"
                }
            }

            override fun onFinal(text: String) {
                mainHandler.post {
                    voiceText = text
                    status = if (refining) "已识别修正，松手重整" else "已识别，松手发送"
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
    }

    fun finishVoiceCapture() {
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
            return
        }
        client.stopAndAwaitFinal(timeoutMillis = 1800L) { finalText ->
            mainHandler.post {
                if (releaseRequestId != activeRequestId) {
                    return@post
                }
                val text = finalText.ifBlank { voiceText }.trim()
                if (text.isBlank()) {
                    status = "没有识别到内容"
                    phase = ComposerPhase.Error
                    return@post
                }
                val baseText = refinementBaseText
                val baseProposal = refinementBaseProposal
                refinementBaseText = null
                refinementBaseProposal = null
                if (baseText != null && baseProposal != null) {
                    status = "正在按修正重新整理"
                    submit(
                        value = buildVoiceRefinementPrompt(baseText, baseProposal, text),
                        displayText = "$baseText\n修正：$text",
                    )
                } else {
                    status = "正在整理"
                    submit(text)
                }
            }
        }
    }

    fun cancelVoiceCapture(cancelStatus: String) {
        activeRequestId += 1
        asrClient?.cancel()
        clearConversationTurn(nextStatus = cancelStatus, startNewSession = true)
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
                                clearConversationTurn(
                                    nextStatus = "已确认并刷新",
                                    nextPhase = ComposerPhase.Confirmed,
                                    startNewSession = true,
                                )
                                onCommitmentsChanged()
                            } else {
                                phase = ComposerPhase.Error
                            }
                        }
                    }.start()
                },
                onCancel = {
                    activeRequestId += 1
                    val wasListening = isListening
                    clearConversationTurn(startNewSession = true)
                    if (wasListening) {
                        asrClient?.cancel()
                    }
                },
                onCandidateSelected = { candidate ->
                    submit(candidate.resolutionText)
                },
                canRefineVoice = asrClient != null &&
                    hasModelApiKey &&
                    hasAsrApiKey &&
                    phase == ComposerPhase.ProposalReady &&
                    proposal != null,
                onRefinePressStart = { startVoiceCapture(refining = true) },
                onRefineCancelMove = { armed ->
                    voiceCancelArmed = armed
                    status = if (armed) "松手取消修正" else "正在听修正"
                },
                onRefinePressEnd = { finishVoiceCapture() },
                onRefineCancel = { cancelVoiceCapture("已取消修正") },
            )
        }
        BottomVoiceNav(
            currentPage = currentPage,
            selectedTab = selectedTab,
            onTodaySelected = onTodaySelected,
            onTodoSelected = onTodoSelected,
            voiceEnabled = asrClient != null &&
                voiceAvailableOnPage &&
                hasModelApiKey &&
                hasAsrApiKey &&
                phase != ComposerPhase.Working &&
                phase != ComposerPhase.Confirming,
            listening = isListening,
            cancelArmed = voiceCancelArmed,
            onPressStart = {
                startVoiceCapture(refining = false)
            },
            onCancelMove = { armed ->
                voiceCancelArmed = armed
                status = if (armed) "松手取消" else "正在听"
            },
            onPressEnd = {
                finishVoiceCapture()
            },
            onCancel = {
                cancelVoiceCapture("已取消语音")
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
    canRefineVoice: Boolean,
    onRefinePressStart: () -> Unit,
    onRefineCancelMove: (Boolean) -> Unit,
    onRefinePressEnd: () -> Unit,
    onRefineCancel: () -> Unit,
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
                if (canRefineVoice) {
                    VoiceRefinementControl(
                        onPressStart = onRefinePressStart,
                        onCancelMove = onRefineCancelMove,
                        onPressEnd = onRefinePressEnd,
                        onCancel = onRefineCancel,
                    )
                }
                when {
                    phase == ComposerPhase.Working -> {
                        TextAction(text = "停止", onClick = onCancel)
                    }
                    phase == ComposerPhase.Error -> {
                        Button(
                            onClick = hapticClick(onClick = onCancel),
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
private fun VoiceRefinementControl(
    onPressStart: () -> Unit,
    onCancelMove: (Boolean) -> Unit,
    onPressEnd: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "按住麦克风说修正",
            color = Muted,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        VoicePrimaryButton(
            enabled = true,
            listening = false,
            cancelArmed = false,
            onPressStart = onPressStart,
            onCancelMove = onCancelMove,
            onPressEnd = onPressEnd,
            onCancel = onCancel,
        )
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
                    .pressFeedbackClick(
                        onClick = { onCandidateSelected(candidate) },
                        pressedScale = 0.98f,
                    ),
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
                onClick = hapticClick(onClick = { onConfirm(if (edited) editedProposal else proposal, edited) }),
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
                onClick = hapticClick(onClick = onClose),
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
            DatePickerField(
                value = draft.date,
                onDateChange = { onDraftChanged(draft.copy(date = it)) },
                label = "日期",
                modifier = Modifier.weight(1.25f),
            )
            TimePickerField(
                value = draft.startTime,
                onTimeChange = { onDraftChanged(draft.copy(startTime = it)) },
                label = "开始",
                modifier = Modifier.weight(1f),
                dialogTitle = "选择开始时间",
            )
            TimePickerField(
                value = draft.endTime,
                onTimeChange = { onDraftChanged(draft.copy(endTime = it)) },
                label = "结束",
                modifier = Modifier.weight(1f),
                dialogTitle = "选择结束时间",
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
        DatePickerField(
            value = draft.due,
            onDateChange = { onDraftChanged(draft.copy(due = it)) },
            label = "截止日期",
        )
        PriorityQuadrantPicker(
            priority = draft.priority.ifBlank { "q2" },
            onPriorityChange = { onDraftChanged(draft.copy(priority = it)) },
        )
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
    color: Color = Accent,
    onClick: () -> Unit,
) {
    Text(
        text = text,
        color = color,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .pressFeedbackClick(onClick = onClick, pressedScale = 0.94f),
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
                .pressFeedbackClick(onClick = onClick, pressedScale = 0.9f),
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
    currentPage: WorkspacePage,
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
            selected = currentPage == WorkspacePage.Today && selectedTab.destination == TabDestination.Today,
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
            selected = currentPage == WorkspacePage.Todo && selectedTab.destination == TabDestination.Todo,
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
            .pressFeedbackClick(onClick = onClick, pressedScale = 0.92f),
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
    val view = LocalView.current
    val diagonalCancelX = with(LocalDensity.current) { 34.dp.toPx() }
    val diagonalCancelY = with(LocalDensity.current) { 22.dp.toPx() }
    val forgivingCancelY = with(LocalDensity.current) { 58.dp.toPx() }
    val upwardFallbackY = with(LocalDensity.current) { 96.dp.toPx() }
    val voiceScale by animateFloatAsState(
        targetValue = if (listening) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 760f),
        label = "voice-press-scale",
    )
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
                .graphicsLayer {
                    scaleX = voiceScale
                    scaleY = voiceScale
                }
                .pointerInput(enabled, diagonalCancelX, diagonalCancelY, forgivingCancelY, upwardFallbackY) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (!enabled) {
                            return@awaitEachGesture
                        }
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
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
        TodoPriority.UrgentImportant -> Danger
        TodoPriority.ImportantNotUrgent -> Ink
        TodoPriority.UrgentNotImportant -> Risk
        TodoPriority.NotUrgentNotImportant -> Success
    }
}

private fun TodoPriority.label(): String {
    return when (this) {
        TodoPriority.UrgentImportant -> "重要且紧急"
        TodoPriority.ImportantNotUrgent -> "重要不紧急"
        TodoPriority.UrgentNotImportant -> "紧急不重要"
        TodoPriority.NotUrgentNotImportant -> "不重要不紧急"
    }
}

private fun TodoPriority.backendValue(): String {
    return when (this) {
        TodoPriority.UrgentImportant -> "q1"
        TodoPriority.ImportantNotUrgent -> "q2"
        TodoPriority.UrgentNotImportant -> "q3"
        TodoPriority.NotUrgentNotImportant -> "q4"
    }
}

@Composable
private fun String.priorityColor(): Color {
    return when (this) {
        "high", "q1" -> Danger
        "medium", "q2" -> Ink
        "low", "q3" -> Risk
        "q4" -> Success
        else -> Risk
    }
}

private fun TodoPriority.sortWeight(): Int {
    return when (this) {
        TodoPriority.UrgentImportant -> 0
        TodoPriority.ImportantNotUrgent -> 1
        TodoPriority.UrgentNotImportant -> 2
        TodoPriority.NotUrgentNotImportant -> 3
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
            priority = patch.priority.toQuadrantValue(),
        )
    }
    return EditableProposalDraft(
        title = title,
        date = "",
        startTime = "",
        endTime = "",
        due = "",
        notes = "",
        priority = "q2",
    )
}

private fun String.toQuadrantValue(): String {
    return when (lowercase()) {
        "high", "q1" -> "q1"
        "medium", "q2" -> "q2"
        "low", "q3" -> "q3"
        "q4" -> "q4"
        else -> "q2"
    }
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

// --- Overdue helpers ---

private fun TodoItem.isOverdue(today: LocalDate = LocalDate.now()): Boolean {
    if (done) return false
    val due = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return false
    return due < today
}

private fun TodoItem.shouldHideFromHome(today: LocalDate = LocalDate.now()): Boolean {
    // Hide when deadline has passed (even by 1 day), regardless of done status
    val due = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return false
    // No deadline: never hide
    if (dueDate.isBlank()) return false
    // Done: hide the day after deadline
    if (done) return today > due
    // Not done: hide the day after deadline
    return today > due
}

private fun ScheduleEvent.isOverdue(today: LocalDate = LocalDate.now()): Boolean {
    val eventDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
    return eventDate < today
}

private fun TodoItem.canToggle(today: LocalDate = LocalDate.now()): Boolean {
    val due = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return true
    return due >= today
}

private fun TodoItem.canEditOrDelete(today: LocalDate = LocalDate.now()): Boolean {
    val due = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return true
    return due >= today
}

private fun ScheduleEvent.canEditOrDelete(today: LocalDate = LocalDate.now()): Boolean {
    val eventDate = runCatching { LocalDate.parse(date) }.getOrNull() ?: return true
    return eventDate >= today
}

// --- Visibility filters ---

private fun List<TodoItem>.visibleForHome(today: LocalDate = LocalDate.now()): List<TodoItem> {
    return filterNot { it.shouldHideFromHome(today) }
}

private fun List<TodoItem>.visibleForTodos(today: LocalDate = LocalDate.now()): List<TodoItem> {
    return filterNot { it.shouldHideFromHome(today) }
}

// --- Todo toggle ---

private fun TodoItem.wasNoDeadline(): Boolean {
    // No-deadline todos have IDs like "todo__<slug>" (empty due between underscores)
    return id.startsWith("todo__")
}

private fun TodoItem.isExpiredDone(today: LocalDate = LocalDate.now()): Boolean {
    if (!done) return false
    val due = runCatching { LocalDate.parse(dueDate) }.getOrNull() ?: return false
    return due < today
}

private fun TodoItem.toggleDoneProposal(today: LocalDate = LocalDate.now()): AgentProposal {
    val newDone = !done
    val effectiveDue = when {
        dueDate.isBlank() && newDone -> today.toString()
        !newDone && wasNoDeadline() -> ""
        else -> dueDate
    }
    val patch = AgentTodoPatch(
        title = title,
        due = effectiveDue,
        timezone = "Asia/Shanghai",
        priority = priority.backendValue(),
        notes = detail,
        tags = listOf(tag),
        done = newDone,
    )
    return AgentProposal(
        id = "local_toggle_${id.ifBlank { title.hashCode().toString() }}",
        commitmentType = CommitmentType.Update,
        title = title,
        summary = if (newDone) "完成 $title" else "重开 $title",
        impact = if (newDone) "标记完成" else "重新打开",
        requiresConfirmation = false,
        todoPatch = patch,
        updatePatch = AgentUpdatePatch(
            targetId = id,
            targetType = "todo",
            targetTitle = title,
            todoPatch = patch,
        ),
    )
}

private fun List<TodoItem>.sortedForHome(today: LocalDate = LocalDate.now()): List<TodoItem> {
    // Active todos first (sorted by due), then done todos at bottom
    val active = filterNot { it.done }.sortedWith(
        compareBy<TodoItem> { todo ->
            runCatching { LocalDate.parse(todo.dueDate) }.getOrNull() ?: LocalDate.MAX
        }.thenBy { todo -> todo.priority.sortWeight() }
    )
    val done = filter { it.done }.sortedByDescending { todo ->
        runCatching { LocalDate.parse(todo.dueDate) }.getOrNull() ?: LocalDate.MIN
    }
    return active + done
}
