package com.nameemrooz.journal.writing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorSafetyTest {
    @Test fun verb_half_space_rules_never_match_inside_a_name() {
        val editor = TextEditor()
        assertEquals("امروز با امیرمحمد صحبت کردم", editor.live("امروز با امیرمحمد صحبت کردم", "fa-IR"))
        assertEquals("من می‌رم", editor.live("من میرم", "fa-IR"))
    }
    @Test fun english_cleanup_keeps_decimals_urls_and_email_intact() = runBlocking {
        val text = "Paid 2.1 at 08:30; open https://example.com?q=1.5 or a@b.com"
        val editor = TextEditor()
        assertEquals(text, editor.live(text, "en-US"))
        assertEquals(text, editor.stable(text, "en-US"))
    }
    @Test fun preserves_emphasis_and_persian_decimal_amounts() {
        val editor = TextEditor()
        assertEquals("خیلی خیلی خسته بودم", editor.live("خیلی خیلی خسته بودم", "fa-IR"))
        assertEquals("قیمت ۱۲٫۵ و ساعت ۸:۳۰", editor.live("قیمت ۱۲٫۵ و ساعت ۸:۳۰", "fa-IR"))
    }

    @Test fun punctuation_model_cannot_change_a_link() = runBlocking {
        val editor = TextEditor(PunctuationRestorer { "به a.b.com برو" })
        assertEquals("به a-b.com برو", editor.stable("به a-b.com برو", "fa-IR"))
    }
    @Test fun livePersianCleanupNormalizesHalfSpacesAndWhitespace() {
        val editor = TextEditor()
        assertEquals("من می‌خوام برم", editor.live("من مي خوام   برم", "fa-IR"))
    }

    @Test fun punctuationOnlyModelOutputIsAccepted() = runBlocking {
        val editor = TextEditor(PunctuationRestorer { "سلام، دنیا." })
        assertEquals("سلام، دنیا.", editor.stable("سلام دنیا", "fa-IR"))
    }

    @Test fun lexicalRewriteFromModelIsRejected() = runBlocking {
        val editor = TextEditor(PunctuationRestorer { "سلام زیبای دنیا." })
        assertEquals("سلام دنیا", editor.stable("سلام دنیا", "fa-IR"))
    }

    @Test fun lexicalSafetyAllowsPunctuationButRejectsWordChanges() {
        assertTrue(LexicalSafety.isPunctuationOnlyChange("سلام دنیا", "سلام، دنیا."))
        assertFalse(LexicalSafety.isPunctuationOnlyChange("سلام دنیا", "سلام زیبای دنیا."))
    }
}
