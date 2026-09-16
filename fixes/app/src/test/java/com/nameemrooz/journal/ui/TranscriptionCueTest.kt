package com.nameemrooz.journal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptionCueTest {
    @Test
    fun cueIsActiveOnlyWhileListening() {
        assertEquals(TranscriptionCueState.ACTIVE, transcriptionCueState(listening = true, ready = true))
        assertEquals(TranscriptionCueState.PREPARING, transcriptionCueState(listening = false, ready = false))
        assertEquals(TranscriptionCueState.IDLE, transcriptionCueState(listening = false, ready = true))
    }
}
