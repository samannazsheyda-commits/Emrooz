package com.nameemrooz.journal.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioWindowBufferV116Test {
    @Test
    fun keeps_overlap_without_dropping_new_samples() {
        val b = AudioWindowBuffer(windowSamples = 8, overlapSamples = 2)
        b.append(shortArrayOf(1, 2, 3, 4, 5))
        assertNull(b.pollWindow())
        b.append(shortArrayOf(6, 7, 8, 9, 10))
        assertArrayEquals(shortArrayOf(1, 2, 3, 4, 5, 6, 7, 8), b.pollWindow())
        b.append(shortArrayOf(11, 12, 13, 14))
        assertArrayEquals(shortArrayOf(7, 8, 9, 10, 11, 12, 13, 14), b.pollWindow())
        assertArrayEquals(shortArrayOf(13, 14), b.takeAll())
    }
}
