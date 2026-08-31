package com.nameemrooz.journal.speech

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Bounded software gain for quiet speech. It deliberately leaves near-silence
 * and already-healthy speech untouched so background noise is not blindly amplified.
 */
class AdaptiveSpeechGain(
    private val targetRms: Double = 3_600.0,
    private val noiseFloorRms: Double = 180.0,
    private val maxGain: Double = 2.4
) {
    private var smoothedGain = 1.0

    fun processInPlace(samples: ShortArray, length: Int): Float {
        require(length in 0..samples.size)
        if (length == 0) return 1.0f

        var energy = 0.0
        for (i in 0 until length) {
            val v = samples[i].toDouble()
            energy += v * v
        }
        val rms = sqrt(energy / length)
        val desiredGain = when {
            rms < noiseFloorRms -> 1.0
            rms >= targetRms -> 1.0
            else -> min(maxGain, targetRms / rms)
        }

        // Faster attack for quiet speech, slower release to avoid pumping.
        val alpha = if (desiredGain > smoothedGain) 0.65 else 0.25
        smoothedGain += alpha * (desiredGain - smoothedGain)
        if (smoothedGain < 1.02) return 1.0f

        for (i in 0 until length) {
            samples[i] = (samples[i] * smoothedGain)
                .roundToInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                .toShort()
        }
        return smoothedGain.toFloat()
    }

    fun reset() {
        smoothedGain = 1.0
    }
}
