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
import com.zpc.fucktheddl.BuildConfig
import com.zpc.fucktheddl.agent.AgentApiClient
import com.zpc.fucktheddl.agent.AgentConnectionSettings
import com.zpc.fucktheddl.agent.LocalCommitmentStore
import com.zpc.fucktheddl.agent.mapCommitmentsToScheduleState
import com.zpc.fucktheddl.schedule.StarterScheduleRepository
import com.zpc.fucktheddl.ui.FuckTheDdlApp
import com.zpc.fucktheddl.ui.theme.FuckTheDdlTheme
import com.zpc.fucktheddl.voice.AliyunRealtimeAsrClient

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 10)
        }

        val localCommitmentStore = LocalCommitmentStore(applicationContext)
        val starterState = StarterScheduleRepository().loadInitialState()
        val localCommitments = localCommitmentStore.listCommitments()
        val localUiState = mapCommitmentsToScheduleState(localCommitments)
        val initialState = starterState.copy(
            events = localUiState.events,
            todos = localUiState.todos,
            syncState = starterState.syncState.copy(label = "本地"),
        )
        val settingsStore = AgentSettingsStore(this)
        setContent {
            FuckTheDdlTheme {
                var connectionSettings by remember { mutableStateOf(settingsStore.load()) }
                val agentApiClient = remember(connectionSettings) {
                    AgentApiClient(connectionSettings.toConfig())
                }
                val asrClient = remember(connectionSettings) {
                    AliyunRealtimeAsrClient(
                        context = applicationContext,
                        sessionProvider = { agentApiClient.asrSession() },
                    )
                }
                FuckTheDdlApp(
                    initialState = initialState,
                    connectionSettings = connectionSettings,
                    agentApiClient = agentApiClient,
                    asrClient = asrClient,
                    commitmentsProvider = { localCommitmentStore.listCommitments() },
                    proposalApplier = { proposal -> localCommitmentStore.applyProposal(proposal) },
                    commitmentDeleter = { commitmentId -> localCommitmentStore.deleteCommitment(commitmentId) },
                    onConnectionSettingsSaved = { settings ->
                        settingsStore.save(settings)
                        connectionSettings = settings
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
            accessToken = preferences.getString("access_token", "").orEmpty(),
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
            .putString("access_token", settings.accessToken.trim())
            .putString("deepseek_api_key", settings.deepseekApiKey.trim())
            .putString("deepseek_base_url", settings.deepseekBaseUrl.trim())
            .putString("deepseek_model", settings.deepseekModel.trim())
            .apply()
    }
}
