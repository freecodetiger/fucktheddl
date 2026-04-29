package com.zpc.fucktheddl.voice

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.alibaba.idst.nui.AsrResult
import com.alibaba.idst.nui.Constants
import com.alibaba.idst.nui.INativeNuiCallback
import com.alibaba.idst.nui.KwsResult
import com.alibaba.idst.nui.NativeNui
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class AliyunRealtimeAsrClient(
    private val context: Context,
    private val sessionProvider: () -> JSONObject,
) : RealtimeAsrClient {
    private val nui = NativeNui.GetInstance()
    private val recording = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var initialized = false
    private var callback: RealtimeAsrCallback? = null

    override fun start(callback: RealtimeAsrCallback) {
        this.callback = callback
        Thread {
            runCatching {
                ensurePermission()
                val session = sessionProvider()
                initializeIfNeeded(session)
                nui.setParams(buildRealtimeParams(session).toString())
                nui.startDialog(Constants.VadMode.TYPE_P2T, "")
            }.onFailure { error ->
                callback.onError(error.message ?: "启动语音识别失败")
            }
        }.start()
    }

    override fun stop() {
        Thread {
            nui.cancelDialog()
            stopRecorder()
        }.start()
    }

    override fun release() {
        stopRecorder()
        nui.release()
        initialized = false
    }

    private fun ensurePermission() {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        check(granted == PackageManager.PERMISSION_GRANTED) { "需要麦克风权限" }
    }

    private fun initializeIfNeeded(session: JSONObject) {
        if (initialized) return
        ensureWorkspaceAssets()
        val initParams = JSONObject()
            .put("apikey", session.getString("api_key"))
            .put("url", session.getString("url"))
            .put("device_id", "android_device")
            .put("workspace", context.filesDir.absolutePath)
            .put("service_mode", "1")
        val result = nui.initialize(
            nuiCallback,
            initParams.toString(),
            Constants.LogLevel.LOG_LEVEL_ERROR,
            false,
        )
        check(result == 0) { "阿里云语音识别初始化失败：$result" }
        initialized = true
    }

    private fun ensureWorkspaceAssets() {
        val workspace = context.filesDir
        val copyList = context.assets.open("copylist.txt")
            .bufferedReader()
            .useLines { lines ->
                lines.map { it.trim() }
                    .filter { it.isNotBlank() }
                    .toList()
            }
        copyList.forEach { assetPath ->
            copyAssetPath(assetPath, File(workspace, assetPath))
        }
    }

    private fun copyAssetPath(
        assetPath: String,
        target: File,
    ) {
        val children = context.assets.list(assetPath).orEmpty()
        if (children.isNotEmpty()) {
            target.mkdirs()
            children.forEach { child ->
                copyAssetPath("$assetPath/$child", File(target, child))
            }
            return
        }
        target.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            target.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun buildRealtimeParams(session: JSONObject): JSONObject {
        return JSONObject()
            .put("service_type", session.getInt("service_type"))
            .put(
                "nls_config",
                JSONObject()
                    .put("model", session.getString("model"))
                    .put("sample_rate", session.getInt("sample_rate"))
                    .put("enable_intermediate_result", true),
            )
    }

    private val nuiCallback = object : INativeNuiCallback {
        override fun onNuiEventCallback(
            event: Constants.NuiEvent,
            resultCode: Int,
            arg2: Int,
            kwsResult: KwsResult?,
            asrResult: AsrResult?,
        ) {
            val text = extractAsrText(asrResult?.asrResult.orEmpty())
            when (event) {
                Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT -> if (text.isNotBlank()) callback?.onPartial(text) else Unit
                Constants.NuiEvent.EVENT_SENTENCE_END,
                Constants.NuiEvent.EVENT_ASR_RESULT,
                -> if (text.isNotBlank()) callback?.onFinal(text) else Unit
                Constants.NuiEvent.EVENT_ASR_ERROR,
                Constants.NuiEvent.EVENT_MIC_ERROR,
                Constants.NuiEvent.EVENT_DIALOG_ERROR,
                -> callback?.onError("语音识别错误：$resultCode")
                else -> Unit
            }
        }

        override fun onNuiNeedAudioData(buffer: ByteArray, len: Int): Int {
            val recorder = audioRecord ?: return 0
            return if (recording.get()) recorder.read(buffer, 0, len) else 0
        }

        override fun onNuiAudioStateChanged(state: Constants.AudioState) {
            when (state) {
                Constants.AudioState.STATE_OPEN -> startRecorder()
                Constants.AudioState.STATE_CLOSE,
                Constants.AudioState.STATE_PAUSE,
                -> stopRecorder()
            }
        }

        override fun onNuiAudioRMSChanged(value: Float) = Unit
        override fun onNuiVprEventCallback(event: Constants.NuiVprEvent?) = Unit
    }

    private fun startRecorder() {
        if (recording.get()) return
        val minSize = AudioRecord.getMinBufferSize(
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            callback?.onError("需要麦克风权限")
            return
        }
        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minSize.coerceAtLeast(3200),
        )
        audioRecord?.startRecording()
        recording.set(true)
    }

    private fun stopRecorder() {
        recording.set(false)
        audioRecord?.runCatching {
            stop()
            release()
        }
        audioRecord = null
    }
}

internal fun extractAsrText(rawResult: String): String {
    if (rawResult.isBlank()) return ""
    val textMatch = """"text"\s*:\s*"((?:\\.|[^"\\])*)"""".toRegex().find(rawResult)
    return textMatch?.groupValues?.getOrNull(1)
        ?.replace("\\\"", "\"")
        ?.replace("\\n", "\n")
        ?.replace("\\t", "\t")
        ?.takeIf { it.isNotBlank() }
        ?: rawResult
}
