package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptContinuityTest {
    @Test fun fallbackKeepsAcceptedPrefixWithoutRepeatingOverlap() {
        assertEquals(
            "سلام امروز رفتم پارک هوا خوب بود",
            TranscriptContinuity.combine("سلام امروز رفتم پارک", "رفتم پارک هوا خوب بود")
        )
    }

    @Test fun emptyFallbackCandidateKeepsPrefix() {
        assertEquals("سلام دنیا", TranscriptContinuity.combine("سلام دنیا", ""))
    }
}
