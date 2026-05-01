package com.zpc.fucktheddl

import android.Manifest
import android.os.Bundle
import android.content.pm.PackageManager
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.zpc.fucktheddl.BuildConfig
import com.zpc.fucktheddl.agent.AgentApiClient
import com.zpc.fucktheddl.agent.AgentConnectionSettings
import com.zpc.fucktheddl.auth.AuthRepository
import com.zpc.fucktheddl.auth.AuthSession
import com.zpc.fucktheddl.auth.AuthSessionStore
import com.zpc.fucktheddl.commitments.room.CommitmentDatabase
import com.zpc.fucktheddl.commitments.room.RoomCommitmentRepository
import com.zpc.fucktheddl.schedule.StarterScheduleRepository
import com.zpc.fucktheddl.ui.AppThemeMode
import com.zpc.fucktheddl.ui.FuckTheDdlApp
import com.zpc.fucktheddl.ui.LoginScreen
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
        ).build()
        val commitmentRepository = RoomCommitmentRepository(commitmentDatabase)
        val authSessionStore = AuthSessionStore(this)
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        val settingsStore = AgentSettingsStore(this)
        setContent {
            FuckTheDdlTheme {
                var connectionSettings by remember { mutableStateOf(settingsStore.load()) }
                var themeMode by remember { mutableStateOf(settingsStore.loadThemeMode()) }
                var authSession by remember { mutableStateOf(authSessionStore.load()) }
                var loginSending by remember { mutableStateOf(false) }
                var loginVerifying by remember { mutableStateOf(false) }
                var loginMessage by remember { mutableStateOf("") }
                val effectiveSettings = connectionSettings.copy(
                    accessToken = authSession.accessToken,
                    userEmail = authSession.email,
                )
                if (!authSession.isLoggedIn) {
                    LoginScreen(
                        sending = loginSending,
                        verifying = loginVerifying,
                        message = loginMessage,
                        onRequestCode = { email ->
                            if (loginSending) return@LoginScreen
                            loginSending = true
                            loginMessage = "验证码发送中..."
                            Thread {
                                val error = AuthRepository(AgentApiClient(connectionSettings.toConfig()))
                                    .requestCode(email)
                                mainHandler.post {
                                    loginSending = false
                                    loginMessage = error ?: "验证码已发送，请查看邮箱"
                                }
                            }.start()
                        },
                        onVerifyCode = { email, code ->
                            if (loginVerifying) return@LoginScreen
                            loginVerifying = true
                            loginMessage = "登录中..."
                            Thread {
                                val result = AuthRepository(AgentApiClient(connectionSettings.toConfig()))
                                    .verifyCode(email, code)
                                mainHandler.post {
                                    loginVerifying = false
                                    if (result.error == null && result.accessToken.isNotBlank()) {
                                        val session = AuthSession(
                                            userId = result.userId,
                                            email = result.email.ifBlank { email },
                                            accessToken = result.accessToken,
                                        )
                                        authSessionStore.save(session)
                                        authSession = session
                                        loginMessage = ""
                                    } else {
                                        loginMessage = result.error ?: "登录失败"
                                    }
                                }
                            }.start()
                        },
                    )
                    return@FuckTheDdlTheme
                }
                val agentApiClient = remember(effectiveSettings) {
                    AgentApiClient(effectiveSettings.toConfig())
                }
                val asrClient = remember(effectiveSettings) {
                    AliyunRealtimeAsrClient(
                        context = applicationContext,
                        sessionProvider = { agentApiClient.asrSession() },
                    )
                }
                FuckTheDdlApp(
                    initialState = initialState,
                    connectionSettings = effectiveSettings,
                    themeMode = themeMode,
                    agentApiClient = agentApiClient,
                    asrClient = asrClient,
                    commitmentsProvider = { commitmentRepository.listCommitments(authSession.userId) },
                    proposalApplier = { proposal -> commitmentRepository.applyProposal(authSession.userId, proposal) },
                    commitmentDeleter = { commitmentId -> commitmentRepository.deleteCommitment(authSession.userId, commitmentId) },
                    userEmail = authSession.email,
                    onConnectionSettingsSaved = { settings ->
                        val persisted = settings.copy(accessToken = "", userEmail = "")
                        settingsStore.save(persisted)
                        connectionSettings = persisted
                    },
                    onThemeModeChanged = { mode ->
                        settingsStore.saveThemeMode(mode)
                        themeMode = mode
                    },
                )
            }
        }
    }
}

private class AgentSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences("agent_connection", Context.MODE_PRIVATE)

    fun load(): AgentConnectionSettings {
        return AgentConnectionSettings(
            baseUrl = preferences.getString("base_url", BuildConfig.AGENT_BASE_URL)
                ?.takeIf { it.isNotBlank() }
                ?: BuildConfig.AGENT_BASE_URL,
            accessToken = "",
            userEmail = "",
            deepseekApiKey = preferences.getString("deepseek_api_key", "").orEmpty(),
            deepseekBaseUrl = preferences.getString("deepseek_base_url", "https://api.deepseek.com/v1")
                ?.takeIf { it.isNotBlank() }
                ?: "https://api.deepseek.com/v1",
            deepseekModel = preferences.getString("deepseek_model", "deepseek-v4-flash")
                ?.takeIf { it.isNotBlank() }
                ?: "deepseek-v4-flash",
        )
    }

    fun save(settings: AgentConnectionSettings) {
        preferences.edit()
            .putString("base_url", settings.baseUrl.trim())
            .putString("deepseek_api_key", settings.deepseekApiKey.trim())
            .putString("deepseek_base_url", settings.deepseekBaseUrl.trim())
            .putString("deepseek_model", settings.deepseekModel.trim())
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
