package com.zpc.fucktheddl.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputControllerTest {
    @Test
    fun partialResultsUpdateDraftWhileRecording() {
        val controller = VoiceInputController(FakeRealtimeAsrClient())

        controller.start()
        controller.onPartial("明天下午")
        controller.onPartial("明天下午三点开会")

        assertTrue(controller.state.recording)
        assertEquals("明天下午三点开会", controller.state.draftText)
        assertEquals("明天下午三点开会", controller.state.partialText)
    }

    @Test
    fun finalResultStopsRecordingAndMarksReadyToSubmit() {
        val controller = VoiceInputController(FakeRealtimeAsrClient())

        controller.start()
        controller.onFinal("明天下午三点开会")

        assertFalse(controller.state.recording)
        assertEquals("明天下午三点开会", controller.state.draftText)
        assertEquals("明天下午三点开会", controller.state.finalText)
        assertTrue(controller.state.readyToSubmit)
    }

    @Test
    fun errorsKeepRecognizedDraftText() {
        val controller = VoiceInputController(FakeRealtimeAsrClient())

        controller.start()
        controller.onPartial("周五前完成")
        controller.onError("network failed")

        assertFalse(controller.state.recording)
        assertEquals("周五前完成", controller.state.draftText)
        assertEquals("network failed", controller.state.error)
    }

    @Test
    fun stopWaitsForFinalResultBeforeMarkingReady() {
        val fakeClient = FakeRealtimeAsrClient()
        val controller = VoiceInputController(fakeClient)

        controller.start()
        controller.onPartial("明天下午三点")
        controller.stopAndAwaitFinal(timeoutMillis = 1000)
        assertFalse(controller.state.readyToSubmit)

        fakeClient.completeStop("明天下午三点开会")

        assertEquals("明天下午三点开会", controller.state.finalText)
        assertTrue(controller.state.readyToSubmit)
    }
}

private class FakeRealtimeAsrClient : RealtimeAsrClient {
    private var callback: RealtimeAsrCallback? = null
    private var stopCompletion: ((String) -> Unit)? = null

    override fun start(callback: RealtimeAsrCallback) {
        this.callback = callback
    }

    override fun stop() = Unit

    override fun stopAndAwaitFinal(timeoutMillis: Long, onComplete: (String) -> Unit) {
        stopCompletion = onComplete
    }

    fun completeStop(text: String) {
        callback?.onFinal(text)
        stopCompletion?.invoke(text)
    }

    override fun release() = Unit
}
