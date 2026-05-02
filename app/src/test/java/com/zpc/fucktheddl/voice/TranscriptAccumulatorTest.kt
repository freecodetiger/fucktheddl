package com.zpc.fucktheddl.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptAccumulatorTest {
    @Test
    fun finalSegmentsAreAppendedInsteadOfReplacingEarlierSpeech() {
        val accumulator = TranscriptAccumulator()

        assertEquals("明天下午三点开会", accumulator.onFinal("明天下午三点开会"))
        assertEquals("明天下午三点开会 备注带电脑", accumulator.onFinal("备注带电脑"))
    }

    @Test
    fun repeatedFinalSegmentsAreKeptWithoutDeduplication() {
        val accumulator = TranscriptAccumulator()

        accumulator.onFinal("备注带电脑")

        assertEquals("备注带电脑 备注带电脑", accumulator.onFinal("备注带电脑"))
    }

    @Test
    fun partialSegmentIsDisplayedAfterCommittedSpeech() {
        val accumulator = TranscriptAccumulator()

        accumulator.onFinal("明天下午三点开会")

        assertEquals("明天下午三点开会 备注", accumulator.onPartial("备注"))
        assertEquals("明天下午三点开会 备注带电脑", accumulator.onPartial("备注带电脑"))
    }

    @Test
    fun resetClearsPreviousRecording() {
        val accumulator = TranscriptAccumulator()
        accumulator.onFinal("明天下午三点开会")

        accumulator.reset()

        assertEquals("备注带电脑", accumulator.onPartial("备注带电脑"))
    }
}
