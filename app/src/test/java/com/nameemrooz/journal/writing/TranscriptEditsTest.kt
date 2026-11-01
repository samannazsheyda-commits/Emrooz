package com.nameemrooz.journal.writing

import org.junit.Assert.*
import org.junit.Test

class TranscriptEditsTest {
    @Test fun an_old_session_cannot_overwrite_new_recording() {
        val edits = TranscriptEdits()
        val old = edits.ticket("متن قدیمی")
        edits.reset()
        edits.partial("متن جدید")
        assertNull(edits.apply(old, "متن قدیمی."))
        assertEquals("متن جدید", edits.display())
    }

    @Test fun delivery_uses_the_latest_partial_instead_of_an_old_snapshot() {
        val edits = TranscriptEdits()
        val ticket = edits.ticket("سلام دنیا")
        edits.partial("سلام دنیا امروز خوبم")
        assertEquals("سلام، دنیا. امروز خوبم", edits.apply(ticket, "سلام، دنیا."))
    }

    @Test fun out_of_order_edit_results_do_not_regress_the_display() {
        val edits = TranscriptEdits()
        val old = edits.ticket("سلام دنیا")
        val latest = edits.ticket("سلام دنیا امروز خوبم")
        assertEquals("سلام دنیا امروز خوبم!", edits.apply(latest, "سلام دنیا امروز خوبم!"))
        assertNull(edits.apply(old, "سلام دنیا."))
    }
}
