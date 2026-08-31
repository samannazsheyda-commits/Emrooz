package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmMemoryBufferV122Test {
    @Test fun caps_samples_and_converts_to_float() {
        val b = PcmMemoryBuffer(5)
        b.append(shortArrayOf(32767, 0, -32768))
        b.append(shortArrayOf(1000, 2000, 3000))
        assertEquals(5, b.sampleCount())
        val f = b.toFloatArray()
        assertEquals(5, f.size)
        assertTrue(f[0] > 0.99f)
        assertEquals(0f, f[1], 0.0001f)
        assertTrue(f[2] <= -0.99f)
        b.clear()
        assertEquals(0, b.sampleCount())
    }
}
