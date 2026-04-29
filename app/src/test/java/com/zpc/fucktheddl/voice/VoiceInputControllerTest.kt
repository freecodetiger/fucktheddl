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
}

private class FakeRealtimeAsrClient : RealtimeAsrClient {
    override fun start(callback: RealtimeAsrCallback) = Unit
    override fun stop() = Unit
    override fun release() = Unit
}

