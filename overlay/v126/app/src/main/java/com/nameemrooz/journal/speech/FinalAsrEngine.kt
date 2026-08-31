package com.nameemrooz.journal.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig

/** Accuracy-first recognizer reused for all finalized segments in one session. */
class FinalAsrEngine(context: Context) : AutoCloseable {
    private val recognizer = OfflineRecognizer(
        assetManager = context.assets,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(
                sampleRate = SpeechTuning.SAMPLE_RATE,
                featureDim = 80,
            ),
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = ShenavaOfflineModelSpec.modelPath),
                tokens = ShenavaOfflineModelSpec.tokensPath,
                numThreads = minOf(4, maxOf(2, Runtime.getRuntime().availableProcessors() - 1)),
                debug = false,
                provider = "cpu",
            ),
            decodingMethod = "greedy_search",
        ),
    )

    /** Decodes one bounded segment and wipes both temporary float PCM and input PCM. */
    fun recognize(segment: ShortArray): String {
        if (segment.isEmpty()) return ""
        val samples = FloatArray(segment.size)
        for (i in segment.indices) samples[i] = segment[i] / 32768.0f
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SpeechTuning.SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
            samples.fill(0f)
            segment.fill(0)
        }
    }

    override fun close() {
        recognizer.release()
    }
}
