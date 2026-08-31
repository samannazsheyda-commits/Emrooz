package com.nameemrooz.journal.speech

import com.nameemrooz.journal.util.PersianEditorV2
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class V126CoreBehaviorTest {
    @Test fun liveCommitsAfterTwoConsistentHypothesesWithoutLongFixedDelay() {
        val s = LiveTranscriptStabilizer(minAgreementMs = 260)
        assertNull(s.offer("امروز رفتم", 0))
        assertEquals("امروز رفتم", s.offer("امروز رفتم خرید", 300))
    }

    @Test fun liveDoesNotExposeOneOffGarbage() {
        val s = LiveTranscriptStabilizer(minAgreementMs = 260)
        assertNull(s.offer("روکا شیمی فند", 0))
        assertNull(s.offer("امروز رفتم خرید", 300))
    }

    @Test fun liveCommittedPrefixIsMonotonic() {
        val s = LiveTranscriptStabilizer(minAgreementMs = 250)
        s.offer("امروز رفتم", 0)
        assertEquals("امروز رفتم", s.offer("امروز رفتم بیرون", 300))
        assertNull(s.offer("امروز اومدم", 600))
        assertNull(s.offer("امروز اومدم خونه", 900))
    }

    @Test fun hardSplitRetainsOnlyBoundedOverlap() {
        val b = SegmentPcmBuffer(sampleRate = 16_000, hardLimitSeconds = 12, overlapMs = 250)
        val chunk = ShortArray(2_048) { 7 }
        while (!b.shouldForceClose()) b.append(chunk, chunk.size)
        val closed = b.forceClose()
        assertTrue(closed.size >= 12 * 16_000)
        assertEquals(4_000, b.sampleCount())
        closed.fill(0)
        b.clear()
    }

    @Test fun repeatedSegmentsDoNotAccumulatePcm() {
        val b = SegmentPcmBuffer(sampleRate = 16_000, hardLimitSeconds = 12, overlapMs = 250)
        repeat(100) {
            val piece = ShortArray(8_000) { 1 }
            b.append(piece, piece.size)
            val closed = b.closeWithoutOverlap()
            closed.fill(0)
        }
        assertEquals(0, b.sampleCount())
    }

    @Test fun overlapMergeDeduplicatesWords() {
        val m = SegmentTranscriptMerger()
        assertEquals(
            "امروز رفتم خرید و برگشتم خونه",
            m.merge("امروز رفتم خرید و برگشتم", "خرید و برگشتم خونه")
        )
    }

    @Test fun brokenFinalCannotEraseGoodStableLiveSegment() {
        val m = SegmentTranscriptMerger()
        assertEquals("امروز رفتم خرید و برگشتم", m.chooseSegment("امروز رفتم خرید و برگشتم", "رولا رولا رولا رولا"))
    }

    @Test fun editorPreservesRepetitionNamesAndSpokenNumbers() {
        val out = PersianEditorV2.editFinal("من خیلی خیلی خوشحال بودم سهیل ممدوحی ساعت هشت اومد")
        assertTrue(out.contains("خیلی خیلی"))
        assertTrue(out.contains("سهیل ممدوحی"))
        assertTrue(out.contains("هشت"))
        assertFalse(out.contains("۸"))
    }

    @Test fun editorAppliesSafePersianOrthography() {
        val out = PersianEditorV2.editFinal("من می خوام فردا کتاب های جدید رو ببینم")
        assertTrue(out.contains("می‌خوام"))
        assertTrue(out.contains("کتاب‌های"))
    }

    @Test fun editorDoesNotTurnDeclarativeChiIntoQuestion() {
        val out = PersianEditorV2.editFinal("من نمی دونم چی باید بگم")
        assertFalse(out.endsWith("؟"))
    }

    @Test fun editorRecognizesClearQuestionStarts() {
        assertTrue(PersianEditorV2.editFinal("چرا امروز دیر اومدی").endsWith("؟"))
    }
}
