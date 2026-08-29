package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechTuningTest {
    @Test
    fun microphoneChunksStayAtOrBelow128Milliseconds() {
        assertEquals(16_000, SpeechTuning.SAMPLE_RATE)
        assertEquals(2_048, SpeechTuning.READ_SAMPLES)
        val blockMs = SpeechTuning.READ_SAMPLES * 1000.0 / SpeechTuning.SAMPLE_RATE
        assertTrue("input block is ${blockMs}ms", blockMs <= 128.0)
    }

    @Test
    fun finalFlushUsesShorterRightContext() {
        assertEquals(1_600, SpeechTuning.FINAL_SILENCE_SAMPLES)
        assertTrue(SpeechTuning.FINAL_SILENCE_SAMPLES <= 2_400)
    }
}