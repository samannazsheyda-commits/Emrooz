package com.nameemrooz.journal.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianQualityV119Test {
    @Test
    fun preserves_spoken_numbers_instead_of_rewriting_them() {
        assertEquals(
            "من دو تا کتاب خریدم.",
            PersianText.clean("من دو تا کتاب خریدم", final = true)
        )
    }

    @Test
    fun preserves_intentional_repetition() {
        assertEquals(
            "امروز خیلی خیلی خوب بود.",
            PersianText.clean("امروز خیلی خیلی خوب بود", final = true)
        )
    }

    @Test
    fun keeps_colloquial_voice_while_fixing_orthography() {
        assertEquals(
            "بعدش هم رفتم خونه‌ی دخترم.",
            PersianText.clean("بعدشم رفتم خونه ی دخترم", final = true)
        )
    }

    @Test
    fun normalizes_compound_given_name_when_words_are_recognized() {
        assertEquals(
            "روح‌الله اومد.",
            PersianText.clean("روح الله اومد", final = true)
        )
    }

    @Test
    fun recovers_roohollah_from_known_asr_alias() {
        val corrector = PersianNameCorrector(
            firstNames = emptySet(),
            surnames = emptyList()
        )
        assertEquals("روح‌الله", corrector.correct("رولا"))
        assertEquals("امروز روح‌الله رو دیدم", corrector.correct("امروز روحلا رو دیدم"))
    }
}
