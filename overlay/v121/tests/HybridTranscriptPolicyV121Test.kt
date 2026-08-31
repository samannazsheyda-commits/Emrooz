package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class HybridTranscriptPolicyV121Test {
    @Test
    fun `does not replace a usable live Persian transcript with a second-pass guess`() {
        val live = "امروز رفتم خونه مادرم و با هم چای خوردیم"
        val secondPass = "امروز رفتم دریا مادرم و با هم جای خوردیم"

        assertEquals(live, HybridTranscriptPolicy.choose(live, secondPass))
    }
}
