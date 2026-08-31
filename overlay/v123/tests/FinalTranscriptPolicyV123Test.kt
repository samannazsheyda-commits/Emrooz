package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class FinalTranscriptPolicyV123Test {
    @Test
    fun `keeps coherent live text when second pass degenerates`() {
        val live = "امروز رفتم خونه مادرم و باهاش صحبت کردم"
        val badFinal = "رولا رولا رولا رولا رولا"

        assertEquals(live, FinalTranscriptPolicy.choose(live, badFinal))
    }

    @Test
    fun `accepts a coherent richer final transcript`() {
        val live = "امروز رفتم خونه مادرم"
        val goodFinal = "امروز رفتم خونه مادرم و باهاش صحبت کردم"

        assertEquals(goodFinal, FinalTranscriptPolicy.choose(live, goodFinal))
    }
}
