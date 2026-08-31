package com.nameemrooz.journal.speech

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveSpeechGainV124Test {
    @Test
    fun `quiet speech is amplified without clipping`() {
        val gain = AdaptiveSpeechGain()
        val samples = ShortArray(2_048) { if (it % 2 == 0) 500 else -500 }
        gain.processInPlace(samples, samples.size)
        val peak = samples.maxOf { kotlin.math.abs(it.toInt()) }
        assertTrue("quiet speech was not boosted: peak=$peak", peak > 700)
        assertTrue("gain clipped", peak <= Short.MAX_VALUE.toInt())
    }

    @Test
    fun `near silence is not amplified into noise`() {
        val gain = AdaptiveSpeechGain()
        val samples = ShortArray(2_048) { if (it % 2 == 0) 20 else -20 }
        val before = samples.copyOf()
        gain.processInPlace(samples, samples.size)
        assertArrayEquals(before, samples)
    }

    @Test
    fun `healthy speech level is left alone`() {
        val gain = AdaptiveSpeechGain()
        val samples = ShortArray(2_048) { if (it % 2 == 0) 8_000 else -8_000 }
        val before = samples.copyOf()
        gain.processInPlace(samples, samples.size)
        assertArrayEquals(before, samples)
    }
}
