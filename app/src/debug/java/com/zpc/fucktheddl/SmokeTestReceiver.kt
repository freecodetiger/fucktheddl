package com.zpc.fucktheddl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.zpc.fucktheddl.agent.AgentCommitmentsPayload
import com.zpc.fucktheddl.agent.AgentConnectionSettings
import com.zpc.fucktheddl.agent.DEFAULT_ALIYUN_ASR_URL
import com.zpc.fucktheddl.agent.LocalAgentClient
import com.zpc.fucktheddl.agent.localAliyunAsrSession

class SmokeTestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val text = intent.getStringExtra("text") ?: "due tomorrow finish android smoke test"
        Thread {
            runCatching {
                val settings = AgentConnectionSettings(
                    deepseekApiKey = intent.getStringExtra("deepseek_api_key").orEmpty(),
                    deepseekBaseUrl = intent.getStringExtra("deepseek_base_url")
                        ?.takeIf { it.isNotBlank() }
                        ?: "https://api.deepseek.com/v1",
                    deepseekModel = intent.getStringExtra("deepseek_model")
                        ?.takeIf { it.isNotBlank() }
                        ?: "deepseek-v4-flash",
                    aliyunApiKey = intent.getStringExtra("aliyun_api_key").orEmpty(),
                    aliyunAsrUrl = intent.getStringExtra("aliyun_asr_url")
                        ?.takeIf { it.isNotBlank() }
                        ?: DEFAULT_ALIYUN_ASR_URL,
                )
                val submitResult = LocalAgentClient().propose(
                    text = text,
                    sessionId = "android-smoke",
                    commitments = AgentCommitmentsPayload(emptyList(), emptyList()),
                    settings = settings,
                )
                val proposal = submitResult.proposal
                    ?: error(submitResult.error ?: "proposal missing")
                val asrSession = localAliyunAsrSession(settings)
                Log.i(
                    TAG,
                    "ok proposalType=${proposal.commitmentType} " +
                        "requiresConfirmation=${proposal.requiresConfirmation} " +
                        "asrModel=${asrSession.getString("model")} " +
                        "asrKeyPresent=${asrSession.optString("api_key").isNotBlank()}",
                )
            }.onFailure { error ->
                Log.e(TAG, "failed local smoke: ${error.message}", error)
            }
            pendingResult.finish()
        }.start()
    }

    private companion object {
        const val TAG = "FuckTheDdlSmoke"
    }
}
