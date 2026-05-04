package com.zpc.fucktheddl

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.os.PowerManager
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.zpc.fucktheddl.notifications.DailyReminderNotifier
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
                var exactAlarmPermissionGranted by remember {
                    mutableStateOf(reminderScheduler.canScheduleExactAlarms())
                }
                var batteryOptimizationIgnored by remember {
                    mutableStateOf(isIgnoringBatteryOptimizations())
                }
                var dailyReminderChannelEnabled by remember {
                    mutableStateOf(DailyReminderNotifier.isChannelEnabled(this@MainActivity))
                }
                val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    notificationPermissionGranted = granted
                    if (granted && dailyReminderSettings.enabled) {
                        reminderScheduler.apply(dailyReminderSettings)
                    }
                }
                fun refreshReminderReliabilityState() {
                    notificationPermissionGranted = hasNotificationPermission()
                    exactAlarmPermissionGranted = reminderScheduler.canScheduleExactAlarms()
                    batteryOptimizationIgnored = isIgnoringBatteryOptimizations()
                    dailyReminderChannelEnabled = DailyReminderNotifier.isChannelEnabled(this@MainActivity)
                }
                LaunchedEffect(dailyReminderSettings.enabled, dailyReminderSettings.hour, dailyReminderSettings.minute) {
                    refreshReminderReliabilityState()
                    if (dailyReminderSettings.enabled) {
                        reminderScheduler.apply(dailyReminderSettings)
                    }
                }
                DisposableEffect(dailyReminderSettings) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            refreshReminderReliabilityState()
                            if (dailyReminderSettings.enabled) {
                                reminderScheduler.apply(dailyReminderSettings)
                            }
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
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
                    exactAlarmPermissionGranted = exactAlarmPermissionGranted,
                    batteryOptimizationIgnored = batteryOptimizationIgnored,
                    dailyReminderChannelEnabled = dailyReminderChannelEnabled,
                    onDailyReminderSettingsChanged = { settings ->
                        reminderSettingsStore.save(settings)
                        dailyReminderSettings = settings
                        reminderScheduler.apply(settings)
                        if (settings.enabled && !hasNotificationPermission()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else if (settings.enabled && !reminderScheduler.canScheduleExactAlarms()) {
                            requestExactAlarmPermission()
                        }
                        refreshReminderReliabilityState()
                    },
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            notificationPermissionGranted = true
                        }
                    },
                    onRequestExactAlarmPermission = ::requestExactAlarmPermission,
                    onRequestBatteryOptimization = ::requestBatteryOptimizationExemption,
                    onOpenNotificationSettings = ::openDailyReminderNotificationSettings,
                )
            }
        }
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            .setData(Uri.parse("package:$packageName"))
        runCatching {
            startActivity(intent)
        }.onFailure {
            openAppDetailsSettings()
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isIgnoringBatteryOptimizations()) return
        val packageUri = Uri.parse("package:$packageName")
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(packageUri)
        runCatching {
            startActivity(requestIntent)
        }.onFailure {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun openDailyReminderNotificationSettings() {
        DailyReminderNotifier(this).ensureChannel()
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, DailyReminderNotifier.ChannelId)
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        }
        runCatching {
            startActivity(intent)
        }.onFailure {
            openAppDetailsSettings()
        }
    }

    private fun openAppDetailsSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.parse("package:$packageName")),
        )
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
