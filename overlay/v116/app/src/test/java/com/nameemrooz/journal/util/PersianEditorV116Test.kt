package com.nameemrooz.journal.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianEditorV116Test {
    @Test
    fun fixes_common_spelling_without_changing_meaning() {
        assertEquals(
            "من می‌خوام فردا حتماً برم.",
            PersianText.clean("من میخام فردا حتما برم", final = true)
        )
        assertEquals(
            "امروز رفتم خونه‌ی دخترم و پیاده‌روی کردیم.",
            PersianText.clean("امروز رفتم خونه ی دخترم و پیاده روی کردیم", final = true)
        )
        assertEquals("واقعاً خوب بود.", PersianText.clean("واقعا خوب بود", final = true))
    }

    @Test
    fun preserves_vnext_question_and_colloquial_rules() {
        assertEquals("دلت خوشه آ.", PersianText.clean("دلت خوش آ", final = true))
        assertEquals("چرا امروز نیومدی؟", PersianText.clean("چرا امروز نیومدی", final = true))
    }
}
