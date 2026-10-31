package com.nameemrooz.journal.writing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DraftControllerTest {
    @Test fun second_recording_appends_without_removing_intentional_repeat() {
        var persisted = ""
        val draft = DraftController("سلام", { persisted = it })
        val token = draft.beginRecording()
        draft.acceptSpeech(token, "سلام دنیا")
        assertEquals("سلام سلام دنیا", draft.text.value)
        assertEquals("سلام سلام دنیا", persisted)
    }

    @Test fun manual_edit_rejects_a_late_transcript() {
        val draft = DraftController("", {})
        val token = draft.beginRecording()
        draft.acceptSpeech(token, "متن اولیه")
        draft.edit("متن دستی")
        draft.acceptSpeech(token, "متن قدیمی")
        assertEquals("متن دستی", draft.text.value)
    }

    @Test fun failed_insert_preserves_the_draft() = runBlocking {
        val draft = DraftController("متن من", {})
        assertFalse(draft.save { error("disk full") })
        assertEquals("متن من", draft.text.value)
    }

    @Test fun successful_insert_only_clears_the_saved_revision() = runBlocking {
        val draft = DraftController("متن اول", {})
        assertTrue(draft.save { saved ->
            assertEquals("متن اول", saved)
            draft.edit("متن بعدی")
        })
        assertEquals("متن بعدی", draft.text.value)
        assertTrue(draft.save { assertEquals("متن بعدی", it) })
        assertEquals("", draft.text.value)
    }
}
