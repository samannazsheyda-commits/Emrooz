package com.nameemrooz.journal.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSessionGateTest {
    @Test
    fun secondSessionCannotStartUntilFinalizationFinishes() {
        val gate = SpeechSessionGate()
        assertTrue(gate.tryBegin())
        assertTrue(gate.isActive())

        // stop() ends capture, but finalization is still part of the same active session.
        assertFalse(gate.tryBegin())

        gate.finish()
        assertFalse(gate.isActive())
        assertTrue(gate.tryBegin())
    }
}
