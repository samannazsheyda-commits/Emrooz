package com.nameemrooz.journal.speech

import kotlin.math.sqrt

/** Bounded, nonoverlapping utterances. PCM is owned by the caller after emit. */
internal class WhisperAudioSegmenter(
    private val sampleRate: Int = 16000,
    private val maxSamples: Int = sampleRate * 8,
    private val pauseSamples: Int = sampleRate * 65 / 100,
    private val minVoiceSamples: Int = sampleRate * 8 / 100,
    private val threshold: Double = 0.0003,
) {
    private val buffer = ShortArray(maxSamples)
    private val preRoll = ShortArray(sampleRate * 16 / 100)
    private var preSize = 0
    private var prePosition = 0
    private var size = 0
    private var voiceSamples = 0
    private var silenceSamples = 0
    private var active = false
    private var continuingSpeech = false
    private var idleSilence = 0

    init {
        require(sampleRate >= 50 && maxSamples > preRoll.size && pauseSamples > 0 && minVoiceSamples > 0)
        require(threshold.isFinite() && threshold >= 0.0)
    }

    fun append(input: ShortArray, count: Int = input.size, emit: (ShortArray) -> Unit) {
        require(count in 0..input.size)
        // Capture uses 20 ms frames, with a shorter frame permitted at the end.
        var offset = 0
        val frameSamples = sampleRate / 50
        while (offset < count) {
            val end = minOf(count, offset + frameSamples)
            var energy = 0.0
            for (i in offset until end) {
                val value = input[i] / 32768.0
                energy += value * value
            }
            val voiced = sqrt(energy / (end - offset)) >= threshold
            for (i in offset until end) {
                if (!active) {
                    if (!voiced) {
                        preRoll[prePosition] = input[i]
                        prePosition = (prePosition + 1) % preRoll.size
                        preSize = minOf(preSize + 1, preRoll.size)
                        if (++idleSilence >= pauseSamples) continuingSpeech = false
                        continue
                    }
                    active = true
                    idleSilence = 0
                    for (j in 0 until preSize) {
                        buffer[size++] = preRoll[(prePosition - preSize + j + preRoll.size) % preRoll.size]
                    }
                    preRoll.fill(0)
                    preSize = 0
                    prePosition = 0
                }
                buffer[size++] = input[i]
                if (voiced) { voiceSamples++; silenceSamples = 0 } else silenceSamples++
                if (size == maxSamples) flush(emit, continues = voiced)
                else if (silenceSamples >= pauseSamples) flush(emit)
            }
            offset = end
        }
    }

    fun finish(emit: (ShortArray) -> Unit) { flush(emit); reset() }

    private fun flush(emit: (ShortArray) -> Unit, continues: Boolean = false) {
        // Keep the short final tail of a long utterance after a forced length boundary.
        val result = if (voiceSamples >= minVoiceSamples || (continuingSpeech && voiceSamples > 0)) buffer.copyOf(size) else null
        buffer.fill(0, 0, size)
        size = 0
        voiceSamples = 0
        silenceSamples = 0
        active = false
        continuingSpeech = continues && result != null
        idleSilence = 0
        if (result != null) emit(result)
    }

    fun reset() {
        buffer.fill(0)
        preRoll.fill(0)
        preSize = 0
        prePosition = 0
        size = 0
        voiceSamples = 0
        silenceSamples = 0
        active = false
        continuingSpeech = false
        idleSilence = 0
    }
}

/** Chunks never overlap; ids prevent a callback retry from appending the same chunk twice. */
internal class WhisperTranscript {
    private var lastChunk = -1L
    private var text = ""
    fun append(chunkId: Long, value: String): String {
        if (chunkId <= lastChunk) return text
        lastChunk = chunkId
        val clean = value.replace(Regex("\\s+"), " ").trim()
        if (clean.isNotEmpty()) text = if (text.isEmpty()) clean else "$text $clean"
        return text
    }
    fun reset() { lastChunk = -1; text = "" }
}
