package com.nameemrooz.journal.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianEditorV123Test {
    @Test
    fun `fixes high confidence lexical typo without paraphrasing`() {
        assertEquals("امروز رفتم اهواز.", PersianText.clean("امروز رفتم اهوار", final = true))
    }

    @Test
    fun `keeps emphasis and spoken numbers intact`() {
        assertEquals("خیلی خیلی خوب بود دو تا کتاب.", PersianText.clean("خیلی خیلی خوب بود دو تا کتاب", final = true))
    }

    @Test
    fun `normalizes productive Persian spacing`() {
        assertEquals("من نمی‌نویسم و کتاب‌های جدید رو می‌بینم.", PersianText.clean("من نمی نویسم و کتاب های جدید رو می بینم", final = true))
    }
}
