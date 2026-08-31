package com.nameemrooz.journal.speech

/** RAM-only PCM store for one recording session. Nothing is written to disk. */
class PcmMemoryBuffer(private val maxSamples: Int) {
    private val chunks = ArrayList<ShortArray>()
    private var size = 0

    @Synchronized
    fun append(samples: ShortArray, count: Int = samples.size) {
        if (count <= 0 || size >= maxSamples) return
        val take = minOf(count, samples.size, maxSamples - size)
        if (take <= 0) return
        chunks += samples.copyOf(take)
        size += take
    }

    @Synchronized
    fun sampleCount(): Int = size

    @Synchronized
    fun toFloatArray(): FloatArray {
        val out = FloatArray(size)
        var offset = 0
        for (chunk in chunks) {
            for (v in chunk) out[offset++] = v / 32768.0f
        }
        return out
    }

    @Synchronized
    fun clear() {
        chunks.forEach { java.util.Arrays.fill(it, 0.toShort()) }
        chunks.clear()
        size = 0
    }
}
