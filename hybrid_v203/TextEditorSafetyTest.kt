package com.nameemrooz.journal.writing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorSafetyTest {
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
