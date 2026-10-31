package com.nameemrooz.journal.speech

import java.util.Arrays

/** RAM-only sliding PCM buffer. */
class AudioWindowBuffer(
    private val windowSamples: Int,
    private val overlapSamples: Int,
) {
    init {
        require(windowSamples > 0)
        require(overlapSamples in 0 until windowSamples)
    }

    private var data = ShortArray(windowSamples * 2)
    private var size = 0
    val availableSamples: Int get() = size

    fun append(chunk: ShortArray) {
        if (chunk.isEmpty()) return
        ensureCapacity(size + chunk.size)
        System.arraycopy(chunk, 0, data, size, chunk.size)
        size += chunk.size
    }

    fun pollWindow(): ShortArray? {
        if (size < windowSamples) return null
        val out = data.copyOfRange(0, windowSamples)
        val keepStart = windowSamples - overlapSamples
        val remaining = size - keepStart
        System.arraycopy(data, keepStart, data, 0, remaining)
        if (size > remaining) Arrays.fill(data, remaining, size, 0.toShort())
        size = remaining
        return out
    }

    fun takeAll(): ShortArray {
        if (size == 0) return ShortArray(0)
        val out = data.copyOfRange(0, size)
        Arrays.fill(data, 0, size, 0.toShort())
        size = 0
        return out
    }

    fun reset() {
        Arrays.fill(data, 0, size, 0.toShort())
        size = 0
    }

    private fun ensureCapacity(required: Int) {
        if (required <= data.size) return
        data = data.copyOf(maxOf(required, data.size * 2))
    }
}
