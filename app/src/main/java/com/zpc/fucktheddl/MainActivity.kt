package com.zpc.fucktheddl

import android.Manifest
import android.os.Bundle
import android.os.Build
import android.content.pm.PackageManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.zpc.fucktheddl.agent.AgentConnectionSettings
import com.zpc.fucktheddl.agent.DEFAULT_ALIYUN_ASR_URL
import com.zpc.fucktheddl.agent.LocalAgentClient
import com.zpc.fucktheddl.agent.localAliyunAsrSession
import com.zpc.fucktheddl.commitments.room.CommitmentDatabase
import com.zpc.fucktheddl.commitments.room.LocalOwnerUserId
import com.zpc.fucktheddl.commitments.room.MIGRATION_1_2
import com.zpc.fucktheddl.commitments.room.RoomCommitmentRepository
import com.zpc.fucktheddl.notifications.DailyReminderScheduler
import com.zpc.fucktheddl.notifications.DailyReminderSettingsStore
import com.zpc.fucktheddl.quests.RoomQuestRepository
import com.zpc.fucktheddl.schedule.StarterScheduleRepository
import com.zpc.fucktheddl.ui.AppThemeMode
import com.zpc.fucktheddl.ui.FuckTheDdlApp
import com.zpc.fucktheddl.ui.theme.FuckTheDdlTheme
import com.zpc.fucktheddl.voice.AliyunRealtimeAsrClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }

        val starterState = StarterScheduleRepository().loadInitialState()
        val initialState = starterState.copy(syncState = starterState.syncState.copy(label = "本地"))
        val commitmentDatabase = Room.databaseBuilder(
            applicationContext,
            CommitmentDatabase::class.java,
            "fucktheddl_commitments.db",
        ).addMigrations(MIGRATION_1_2).build()
        val commitmentRepository = RoomCommitmentRepository(commitmentDatabase)
        val questRepository = RoomQuestRepository(commitmentDatabase)
        val settingsStore = AgentSettingsStore(this)
        val reminderSettingsStore = DailyReminderSettingsStore(this)
        val reminderScheduler = DailyReminderScheduler(this)
        setContent {
            FuckTheDdlTheme {
                var connectionSettings by remember { mutableStateOf(settingsStore.load()) }
                var themeMode by remember { mutableStateOf(settingsStore.loadThemeMode()) }
                var dailyReminderSettings by remember { mutableStateOf(reminderSettingsStore.load()) }
                var notificationPermissionGranted by remember {
                    mutableStateOf(hasNotificationPermission())
                }
                val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    notificationPermissionGranted = granted
                    if (granted && dailyReminderSettings.enabled) {
                        reminderScheduler.apply(dailyReminderSettings)
                    }
                }
                val localAgentClient = remember { LocalAgentClient() }
                val asrClient = remember(connectionSettings.aliyunApiKey, connectionSettings.aliyunAsrUrl) {
                    AliyunRealtimeAsrClient(
                        context = applicationContext,
                        sessionProvider = { localAliyunAsrSession(connectionSettings) },
                    )
                }
                DisposableEffect(asrClient) {
                    onDispose {
                        asrClient.release()
                    }
                }
                FuckTheDdlApp(
                    initialState = initialState,
                    connectionSettings = connectionSettings,
                    themeMode = themeMode,
                    agentClient = localAgentClient,
                    asrClient = asrClient,
                    commitmentsProvider = { commitmentRepository.listCommitments(LocalOwnerUserId) },
                    proposalApplier = { proposal -> commitmentRepository.applyProposal(LocalOwnerUserId, proposal) },
                    commitmentDeleter = { commitmentId -> commitmentRepository.deleteCommitment(LocalOwnerUserId, commitmentId) },
                    questBooksProvider = { kind -> questRepository.listBooks(kind) },
                    questTreeProvider = { bookId -> questRepository.getBookTree(bookId) },
                    questBookCreator = { kind, title, description, location, targetDate ->
                        questRepository.createBook(kind, title, description, location, targetDate)
                    },
                    questBookUpdater = { book -> questRepository.updateBook(book) },
                    questBookDeleter = { bookId -> questRepository.deleteBook(bookId) },
                    questNodeCreator = { bookId, parentId, title -> questRepository.createNode(bookId, parentId, title) },
                    questNodeUpdater = { node -> questRepository.updateNode(node) },
                    questNodeDeleter = { nodeId -> questRepository.deleteNode(nodeId) },
                    onConnectionSettingsSaved = { settings ->
                        settingsStore.save(settings)
                        connectionSettings = settings
                    },
                    onThemeModeChanged = { mode ->
                        settingsStore.saveThemeMode(mode)
                        themeMode = mode
                    },
                    dailyReminderSettings = dailyReminderSettings,
                    notificationPermissionGranted = notificationPermissionGranted,
                    onDailyReminderSettingsChanged = { settings ->
                        reminderSettingsStore.save(settings)
                        dailyReminderSettings = settings
                        reminderScheduler.apply(settings)
                        if (settings.enabled && !hasNotificationPermission()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            notificationPermissionGranted = hasNotificationPermission()
                        }
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationPermissionGranted = true
                        }
                    },
                )
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}

private class AgentSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("agent_connection", Context.MODE_PRIVATE)

    fun load(): AgentConnectionSettings {
        return AgentConnectionSettings(
            deepseekApiKey = preferences.getString("deepseek_api_key", "").orEmpty(),
            deepseekBaseUrl = preferences.getString("deepseek_base_url", "https://api.deepseek.com/v1")
                ?.takeIf { it.isNotBlank() }
                ?: "https://api.deepseek.com/v1",
            deepseekModel = preferences.getString("deepseek_model", "deepseek-v4-flash")
                ?.takeIf { it.isNotBlank() }
                ?: "deepseek-v4-flash",
            aliyunApiKey = preferences.getString("aliyun_api_key", "").orEmpty(),
            aliyunAsrUrl = preferences.getString("aliyun_asr_url", DEFAULT_ALIYUN_ASR_URL)
                ?.takeIf { it.isNotBlank() }
                ?: DEFAULT_ALIYUN_ASR_URL,
        )
    }

    fun save(settings: AgentConnectionSettings) {
        preferences.edit()
            .putString("deepseek_api_key", settings.deepseekApiKey.trim())
            .putString("deepseek_base_url", settings.deepseekBaseUrl.trim())
            .putString("deepseek_model", settings.deepseekModel.trim())
            .putString("aliyun_api_key", settings.aliyunApiKey.trim())
            .putString("aliyun_asr_url", settings.aliyunAsrUrl.trim())
            .apply()
    }

    fun loadThemeMode(): AppThemeMode {
        return AppThemeMode.fromStorage(preferences.getString("theme_mode", null))
    }

    fun saveThemeMode(mode: AppThemeMode) {
        preferences.edit()
            .putString("theme_mode", mode.storageKey)
            .apply()
    }
}
