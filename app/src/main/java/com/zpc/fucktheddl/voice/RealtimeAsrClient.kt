package com.zpc.fucktheddl.voice

interface RealtimeAsrClient {
    fun start(callback: RealtimeAsrCallback)
    fun stop()
    fun stopAndAwaitFinal(timeoutMillis: Long = 1200L, onComplete: (String) -> Unit) {
        stop()
        Thread {
            Thread.sleep(timeoutMillis)
            onComplete("")
        }.start()
    }
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
    private val transcript = TranscriptAccumulator()
    var state: VoiceInputState = VoiceInputState()
        private set

    fun start() {
        transcript.reset()
        state = state.copy(recording = true, readyToSubmit = false, error = null)
        asrClient.start(this)
    }

    fun stop() {
        asrClient.stop()
    }

    fun stopAndAwaitFinal(timeoutMillis: Long = 1200L) {
        state = state.copy(recording = false, readyToSubmit = false)
        asrClient.stopAndAwaitFinal(timeoutMillis) { text ->
            val finalText = text.ifBlank { state.draftText }.trim()
            state = state.copy(
                recording = false,
                draftText = finalText,
                finalText = finalText,
                readyToSubmit = finalText.isNotBlank(),
                error = null,
            )
        }
    }

    override fun onPartial(text: String) {
        val accumulated = transcript.onPartial(text)
        state = state.copy(partialText = accumulated, draftText = accumulated)
    }

    override fun onFinal(text: String) {
        val accumulated = transcript.onFinal(text)
        state = state.copy(
            recording = false,
            draftText = accumulated,
            finalText = accumulated,
            readyToSubmit = accumulated.isNotBlank(),
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
