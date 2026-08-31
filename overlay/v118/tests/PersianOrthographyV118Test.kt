package com.nameemrooz.journal.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianOrthographyV118Test {
    @Test
    fun fixes_common_voice_spelling_without_paraphrasing() {
        assertEquals(
            "می‌خوام پیاده‌روی کنم.",
            PersianText.clean("میخام پیاده روی کنم", final = true)
        )
    }

    @Test
    fun fixes_ezafe_and_joined_colloquial_words() {
        assertEquals(
            "بعدش هم رفتم خونه‌ی دخترم.",
            PersianText.clean("بعدشم رفتم خونه ی دخترم", final = true)
        )
    }

    @Test
    fun normalizes_common_adverbs() {
        assertEquals(
            "واقعاً خوب بود.",
            PersianText.clean("واقعا خوب بود", final = true)
        )
    }
}
