package com.nameemrooz.journal.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianEditorV121Test {
    @Test
    fun `repairs common Persian half spaces and final punctuation`() {
        val input = "من نمی بینم چرا کتاب های جدید رو نمی خونم"
        val expected = "من نمی‌بینم چرا کتاب‌های جدید رو نمی‌خونم؟"

        assertEquals(expected, PersianText.clean(input, final = true))
    }

    @Test
    fun `keeps meaning while fixing common colloquial orthography`() {
        val input = "امروز می بینم که حال مادرم واقعا بهتره"
        val expected = "امروز می‌بینم که حال مادرم واقعاً بهتره."

        assertEquals(expected, PersianText.clean(input, final = true))
    }
}
