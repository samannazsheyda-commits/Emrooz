package com.nameemrooz.journal.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianEditorV122Test {
    @Test fun joins_generic_spoken_verb_prefix() {
        assertEquals("امروز می‌نویسم و بعد نمی‌نویسم.", PersianText.clean("امروز می نویسم و بعد نمی نویسم", final = true))
    }

    @Test fun joins_comparative_and_ezafe_without_paraphrasing() {
        assertEquals("این زیبا‌ترین عکس خونه‌ی ماست.", PersianText.clean("این زیبا ترین عکس خونه ی ماست", final = true))
    }

    @Test fun preserves_natural_emphasis() {
        assertEquals("خیلی خیلی خوب بود.", PersianText.clean("خیلی خیلی خوب بود", final = true))
    }
}
