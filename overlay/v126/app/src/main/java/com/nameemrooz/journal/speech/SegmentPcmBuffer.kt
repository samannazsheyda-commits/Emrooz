package com.nameemrooz.journal.speech

/**
 * Bounded RAM-only PCM buffer for a single speech segment.
 * No session-length audio history is retained.
 */
class SegmentPcmBuffer(
    private val sampleRate: Int = 16_000,
    hardLimitSeconds: Int = 12,
    overlapMs: Int = 250,
    private val readSlackSamples: Int = 4_096,
) {
    private val hardLimit = sampleRate * hardLimitSeconds
    private val overlap = sampleRate * overlapMs / 1_000
    private val data = ShortArray(hardLimit + readSlackSamples + overlap)
    private var size = 0

    fun append(samples: ShortArray, length: Int) {
        require(length in 0..samples.size)
        if (length == 0) return
        val room = data.size - size
        require(length <= room) { "segment PCM overflow: size=$size length=$length capacity=${data.size}" }
        samples.copyInto(data, destinationOffset = size, startIndex = 0, endIndex = length)
        size += length
    }

    fun shouldForceClose(): Boolean = size >= hardLimit

    /** Close an endpointed segment and retain no audio. */
    fun closeWithoutOverlap(): ShortArray {
        if (size == 0) return ShortArray(0)
        val out = data.copyOfRange(0, size)
        java.util.Arrays.fill(data, 0, size, 0.toShort())
        size = 0
        return out
    }

    /**
     * Close a hard-capped segment and retain only a short overlap for the next
     * segment, preventing a word from being cut at an artificial timer boundary.
     */
    fun forceClose(): ShortArray {
        if (size == 0) return ShortArray(0)
        val closedSize = size
        val out = data.copyOfRange(0, closedSize)
        val keep = minOf(overlap, closedSize)
        if (keep > 0) {
            val start = closedSize - keep
            data.copyInto(data, destinationOffset = 0, startIndex = start, endIndex = closedSize)
        }
        java.util.Arrays.fill(data, keep, closedSize, 0.toShort())
        size = keep
        return out
    }

    fun sampleCount(): Int = size
    fun hasSpeechSizedTail(minSamples: Int = 1_600): Boolean = size >= minSamples

    fun clear() {
        data.fill(0)
        size = 0
    }
}
