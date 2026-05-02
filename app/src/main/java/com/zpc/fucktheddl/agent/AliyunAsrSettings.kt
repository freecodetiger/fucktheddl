package com.zpc.fucktheddl.agent

import org.json.JSONObject

fun localAliyunAsrSession(settings: AgentConnectionSettings): JSONObject {
    val apiKey = settings.aliyunApiKey.trim()
    check(apiKey.isNotBlank()) { "请在设置里填写阿里云语音 API Key" }
    return JSONObject()
        .put("api_key", apiKey)
        .put("url", settings.aliyunAsrUrl.trim().ifBlank { DEFAULT_ALIYUN_ASR_URL })
        .put("model", "fun-asr-realtime-2025-09-15")
        .put("sample_rate", 16000)
        .put("service_type", 4)
}
