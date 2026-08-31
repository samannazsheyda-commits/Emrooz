package com.nameemrooz.journal.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig

/** Full-context Persian CTC pass used only after recording stops. */
class ShenavaOfflineRecognizer(context: Context) : AutoCloseable {
    private val recognizer = OfflineRecognizer(
        assetManager = context.assets,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SpeechTuning.SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = ShenavaOfflineModelSpec.modelPath),
                tokens = ShenavaOfflineModelSpec.tokensPath,
                numThreads = minOf(4, maxOf(2, Runtime.getRuntime().availableProcessors() - 1)),
                debug = false,
                provider = "cpu"
            ),
            decodingMethod = "greedy_search"
        )
    )

    fun recognize(samples: FloatArray): String {
        if (samples.isEmpty()) return ""
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SpeechTuning.SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    override fun close() {
        recognizer.release()
    }
}
