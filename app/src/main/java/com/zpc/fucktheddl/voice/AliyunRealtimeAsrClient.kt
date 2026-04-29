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
                callback.onError(error.message ?: "Failed to start speech recognition")
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
        check(granted == PackageManager.PERMISSION_GRANTED) { "Microphone permission is required" }
    }

    private fun initializeIfNeeded(session: JSONObject) {
        if (initialized) return
        val initParams = JSONObject()
            .put("app_key", session.getString("app_key"))
            .put("token", session.getString("token"))
            .put("url", session.getString("url"))
            .put("device_id", "android_device")
            .put("workspace", context.filesDir.absolutePath)
            .put("service_mode", "1")
        val result = nui.initialize(
            nuiCallback,
            initParams.toString(),
            Constants.LogLevel.LOG_LEVEL_INFO,
            false,
        )
        check(result == 0) { "Aliyun ASR initialize failed: $result" }
        initialized = true
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
            val text = asrResult?.asrResult.orEmpty()
            when (event) {
                Constants.NuiEvent.EVENT_ASR_PARTIAL_RESULT -> callback?.onPartial(text)
                Constants.NuiEvent.EVENT_SENTENCE_END,
                Constants.NuiEvent.EVENT_ASR_RESULT,
                -> callback?.onFinal(text)
                Constants.NuiEvent.EVENT_ASR_ERROR,
                Constants.NuiEvent.EVENT_MIC_ERROR,
                Constants.NuiEvent.EVENT_DIALOG_ERROR,
                -> callback?.onError("ASR error: $resultCode")
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
            callback?.onError("Microphone permission is required")
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

