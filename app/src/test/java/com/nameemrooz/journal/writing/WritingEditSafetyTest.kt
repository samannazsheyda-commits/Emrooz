package com.nameemrooz.journal.writing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingEditSafetyTest {
    @Test fun numeric_sign_and_percentage_cannot_be_removed() {
        assertFalse(WritingEditSafety.accept("دما -۵ بود", "دما ۵ بود"))
        assertFalse(WritingEditSafety.accept("رشد ۵٪ بود", "رشد ۵ بود"))
    }
    @Test fun rejects_nearby_names_and_negation_changes() {
        assertFalse(WritingEditSafety.accept("امروز سارا آمد", "امروز سانا آمد"))
        assertFalse(WritingEditSafety.accept("من دارو نخوردم", "من دارو خوردم"))
    }

    @Test fun rejects_changed_urls_and_decimal_separators() {
        assertFalse(WritingEditSafety.accept("به a-b.com برو", "به a.b.com برو"))
        assertFalse(WritingEditSafety.accept("مبلغ ۱۲٫۵ بود", "مبلغ ۱۲،۵ بود"))
    }
    @Test fun accepts_punctuation_only() {
        assertTrue(WritingEditSafety.accept("من امروز رفتم خونه", "من امروز رفتم خونه."))
    }

    @Test fun accepts_half_space_only() {
        assertTrue(WritingEditSafety.accept("می روم خونه", "می‌روم خونه"))
    }

    @Test fun accepts_one_small_persian_spelling_fix() {
        assertTrue(WritingEditSafety.accept("امروز هوا عاللی بود", "امروز هوا عالی بود"))
    }

    @Test fun rejects_number_change() {
        assertFalse(WritingEditSafety.accept("ساعت ۸ رسیدم", "ساعت ۹ رسیدم"))
    }

    @Test fun rejects_latin_token_change() {
        assertFalse(WritingEditSafety.accept("به WhatsApp پیام دادم", "به Whatsapp پیام دادم"))
    }

    @Test fun rejects_removed_repeated_emphasis() {
        assertFalse(WritingEditSafety.accept("خیلی خیلی خسته بودم", "خیلی خسته بودم"))
    }

    @Test fun rejects_inserted_or_deleted_words() {
        assertFalse(WritingEditSafety.accept("امروز رفتم بیرون", "امروز با دوستم رفتم بیرون"))
        assertFalse(WritingEditSafety.accept("امروز خیلی خوب بود", "امروز خوب بود"))
    }

    @Test fun rejects_widespread_rewrite() {
        assertFalse(
            WritingEditSafety.accept(
                "امروز هوا خوب بود و رفتم بیرون",
                "امروز روز فوق العاده ای داشتم و بیرون قدم زدم",
            ),
        )
    }
}
