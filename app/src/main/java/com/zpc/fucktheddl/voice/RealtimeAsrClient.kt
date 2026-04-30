package com.zpc.fucktheddl.voice

interface RealtimeAsrClient {
    fun start(callback: RealtimeAsrCallback)
    fun stop()
    fun cancel() {
        stop()
    }
    fun release()
}

interface RealtimeAsrCallback {
    fun onPartial(text: String)
    fun onFinal(text: String)
    fun onError(message: String)
}

data class VoiceInputState(
    val recording: Boolean = false,
    val draftText: String = "",
    val partialText: String = "",
    val finalText: String = "",
    val readyToSubmit: Boolean = false,
    val error: String? = null,
)

class VoiceInputController(
    private val asrClient: RealtimeAsrClient,
) : RealtimeAsrCallback {
    var state: VoiceInputState = VoiceInputState()
        private set

    fun start() {
        state = state.copy(recording = true, readyToSubmit = false, error = null)
        asrClient.start(this)
    }

    fun stop() {
        asrClient.stop()
    }

    override fun onPartial(text: String) {
        state = state.copy(partialText = text, draftText = text)
    }

    override fun onFinal(text: String) {
        state = state.copy(
            recording = false,
            draftText = text,
            finalText = text,
            readyToSubmit = text.isNotBlank(),
            error = null,
        )
    }

    override fun onError(message: String) {
        state = state.copy(recording = false, readyToSubmit = false, error = message)
    }

    fun release() {
        asrClient.release()
    }
}
