package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class FinalTranscriptPolicyV122Test {
    @Test fun prefers_plausible_full_context_persian() {
        assertEquals(
            "امروز رفتم خونه مادرم و خیلی خوش گذشت",
            FinalTranscriptPolicy.choose(
                "امروز رفتم خونه مادرم خیلی خوش گزشت",
                "امروز رفتم خونه مادرم و خیلی خوش گذشت"
            )
        )
    }

    @Test fun rejects_empty_or_non_persian_final() {
        val live = "امروز حالم خیلی خوب بود"
        assertEquals(live, FinalTranscriptPolicy.choose(live, ""))
        assertEquals(live, FinalTranscriptPolicy.choose(live, "hello random output"))
    }

    @Test fun rejects_implausibly_short_final() {
        val live = "امروز صبح رفتم خونه مادرم بعد با هم رفتیم بیرون و خرید کردیم"
        assertEquals(live, FinalTranscriptPolicy.choose(live, "امروز"))
    }
}
