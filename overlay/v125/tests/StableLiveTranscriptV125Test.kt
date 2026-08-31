package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StableLiveTranscriptV125Test {
    @Test
    fun `does not expose fast changing raw hypotheses`() {
        val gate = StableLiveTranscript(minStableMs = 850, windowMs = 1100)

        assertNull(gate.offer("امروز", 0))
        assertNull(gate.offer("امروز رفتم", 250))
        assertNull(gate.offer("امروز رفتم خونه", 500))
        assertNull(gate.offer("امروز رفتم خونه مادرم", 750))

        assertEquals("امروز", gate.offer("امروز رفتم خونه مادرم", 900))
    }

    @Test
    fun `grows only the stable prefix instead of replacing it with a new guess`() {
        val gate = StableLiveTranscript(minStableMs = 700, windowMs = 1000)

        gate.offer("امروز رفتم", 0)
        gate.offer("امروز رفتم خونه", 250)
        gate.offer("امروز رفتم خونه مادرم", 500)
        assertEquals("امروز رفتم", gate.offer("امروز رفتم خونه مادرم", 750))

        assertNull(gate.offer("امروز رفته بودم یه چیز", 850))
        assertNull(gate.offer("امروز رفتم خونه مادرم", 1100))
    }

    @Test
    fun `allows a short single word only after it stays unchanged`() {
        val gate = StableLiveTranscript(minStableMs = 700, windowMs = 900)

        assertNull(gate.offer("سلام", 0))
        assertNull(gate.offer("سلام", 350))
        assertEquals("سلام", gate.offer("سلام", 750))
    }

    @Test
    fun `reset starts a fresh live session`() {
        val gate = StableLiveTranscript(minStableMs = 500, windowMs = 800)
        gate.offer("امروز خوبم", 0)
        gate.offer("امروز خوبم", 300)
        assertEquals("امروز خوبم", gate.offer("امروز خوبم", 600))

        gate.reset()
        assertNull(gate.offer("فردا میرم", 700))
    }
}
