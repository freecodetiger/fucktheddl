package com.zpc.fucktheddl

import android.Manifest
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.zpc.fucktheddl.BuildConfig
import com.zpc.fucktheddl.agent.AgentApiClient
import com.zpc.fucktheddl.agent.AgentApiConfig
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

        val initialState = StarterScheduleRepository().loadInitialState()
        val agentApiClient = AgentApiClient(AgentApiConfig(BuildConfig.AGENT_BASE_URL))
        val asrClient = AliyunRealtimeAsrClient(
            context = applicationContext,
            sessionProvider = { agentApiClient.asrSession() },
        )
        setContent {
            FuckTheDdlTheme {
                FuckTheDdlApp(
                    initialState = initialState,
                    agentApiClient = agentApiClient,
                    asrClient = asrClient,
                )
            }
        }
    }
}
