package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class HybridTranscriptPolicyTest {
    @Test
    fun prefersSaneWhisperPersianOverLiveDraft() {
        assertEquals(
            "روح الله امروز اومد خونه",
            HybridTranscriptPolicy.choose("رولا امروز اومد خونه", " روح الله امروز اومد خونه ")
        )
    }

    @Test
    fun fallsBackToLiveWhenWhisperIsBlank() {
        assertEquals(
            "امروز رفتم خونه",
            HybridTranscriptPolicy.choose("امروز رفتم خونه", "   ")
        )
    }

    @Test
    fun fallsBackToPersianLiveWhenWhisperUnexpectedlySwitchesToLatin() {
        assertEquals(
            "امروز حالم بهتر بود",
            HybridTranscriptPolicy.choose("امروز حالم بهتر بود", "today I felt better")
        )
    }

    @Test
    fun rejectsObviousRepeatedWhisperHallucination() {
        assertEquals(
            "امروز هوا خوب بود",
            HybridTranscriptPolicy.choose(
                "امروز هوا خوب بود",
                "سلام سلام سلام سلام سلام سلام سلام سلام"
            )
        )
    }

    @Test
    fun preservesNaturalDoubleEmphasis() {
        assertEquals(
            "امروز خیلی خیلی خسته بودم",
            HybridTranscriptPolicy.choose(
                "امروز خیلی خسته بودم",
                "امروز خیلی خیلی خسته بودم"
            )
        )
    }

    @Test
    fun stripsWhisperSpecialMarkersAndNormalizesWhitespace() {
        assertEquals(
            "امروز خوب بود",
            HybridTranscriptPolicy.choose("", "<|fa|>   امروز   خوب بود  <|endoftext|>")
        )
    }
}
