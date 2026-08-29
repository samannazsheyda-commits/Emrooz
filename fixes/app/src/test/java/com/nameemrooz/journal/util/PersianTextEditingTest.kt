package com.nameemrooz.journal.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianTextEditingTest {
    @Test
    fun correctsKnownMisheardFullName() {
        assertEquals("سهیل ممدوحی.", PersianText.clean("سهیل ممنوعی", final = true))
    }

    @Test
    fun removesShortWordStutterWithoutChangingMeaning() {
        assertEquals("من امروز رفتم بیرون.", PersianText.clean("من من امروز رفتم بیرون", final = true))
    }

    @Test
    fun collapsesImmediatelyRepeatedPhrase() {
        assertEquals(
            "امروز خیلی خوب بود.",
            PersianText.clean("امروز خیلی خوب بود امروز خیلی خوب بود", final = true)
        )
    }

    @Test
    fun preservesIntentionalNoNo() {
        assertEquals("نه نه من نمی‌خوام.", PersianText.clean("نه نه من نمیخوام", final = true))
    }

    @Test
    fun keepsExistingOrthographyAndPunctuationEditing() {
        assertEquals("راستی، من می‌خوام امروز برم.", PersianText.clean("راستی من میخوام امروز برم", final = true))
    }
}