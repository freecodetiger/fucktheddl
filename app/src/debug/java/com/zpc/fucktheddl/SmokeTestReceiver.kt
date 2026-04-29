package com.zpc.fucktheddl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zpc.fucktheddl.agent.AgentApiClient
import com.zpc.fucktheddl.agent.AgentApiConfig

class SmokeTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val text = intent.getStringExtra("text") ?: "due tomorrow finish android smoke test"
        Thread {
            runCatching {
                val client = AgentApiClient(AgentApiConfig(BuildConfig.AGENT_BASE_URL))
                val submitResult = client.propose(text, sessionId = "android-smoke")
                val proposal = submitResult.proposal
                    ?: error(submitResult.error ?: "proposal missing")
                val asrSession = client.asrSession()
                Log.i(
                    TAG,
                    "ok baseUrl=${BuildConfig.AGENT_BASE_URL} " +
                        "proposalType=${proposal.commitmentType} " +
                        "requiresConfirmation=${proposal.requiresConfirmation} " +
                        "asrModel=${asrSession.getString("model")} " +
                        "asrKeyPresent=${asrSession.optString("api_key").isNotBlank()}",
                )
            }.onFailure { error ->
                Log.e(TAG, "failed baseUrl=${BuildConfig.AGENT_BASE_URL}: ${error.message}", error)
            }
            pendingResult.finish()
        }.start()
    }

    private companion object {
        const val TAG = "FuckTheDdlSmoke"
    }
}
