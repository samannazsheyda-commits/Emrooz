package com.nameemrooz.journal.speech

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class AudioWindowBufferTest {
    @Test fun emitsWindowAndKeepsOverlap() {
        val buffer = AudioWindowBuffer(windowSamples = 6, overlapSamples = 2)
        buffer.append(shortArrayOf(1, 2, 3, 4))
        assertNull(buffer.pollWindow())
        buffer.append(shortArrayOf(5, 6, 7, 8))
        assertArrayEquals(shortArrayOf(1, 2, 3, 4, 5, 6), buffer.pollWindow())
        assertEquals(4, buffer.availableSamples)
        assertArrayEquals(shortArrayOf(5, 6, 7, 8), buffer.takeAll())
        assertEquals(0, buffer.availableSamples)
    }

    @Test fun resetClearsBufferedAudio() {
        val buffer = AudioWindowBuffer(windowSamples = 4, overlapSamples = 1)
        buffer.append(shortArrayOf(9, 8, 7))
        buffer.reset()
        assertEquals(0, buffer.availableSamples)
        assertNull(buffer.pollWindow())
    }
}
