package com.nameemrooz.journal.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig

/** Shenava Koochik v1.5 RNNT INT8. Fully offline; reads model assets directly from the APK. */
class ShenavaRnntRecognizer(context: Context) : AutoCloseable {
    companion object {
        const val MODEL_DIR = "models/shenava_v15_rnnt_int8"
        const val ENCODER = "$MODEL_DIR/encoder.int8.onnx"
        const val DECODER = "$MODEL_DIR/decoder.int8.onnx"
        const val JOINER = "$MODEL_DIR/joiner.int8.onnx"
        const val TOKENS = "$MODEL_DIR/tokens.txt"
        const val SAMPLE_RATE = 16_000
    }

    private val recognizer = OfflineRecognizer(
        assetManager = context.assets,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SAMPLE_RATE, featureDim = 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = ENCODER,
                    decoder = DECODER,
                    joiner = JOINER,
                ),
                tokens = TOKENS,
                numThreads = minOf(4, maxOf(2, Runtime.getRuntime().availableProcessors() - 1)),
                debug = false,
                provider = "cpu",
                modelType = RnntModelSpec.MODEL_TYPE,
            ),
            decodingMethod = "greedy_search",
        )
    )

    @Synchronized
    fun transcribe(samples: FloatArray): String {
        if (samples.isEmpty()) return ""
        val stream = recognizer.createStream()
        return try {
            stream.acceptWaveform(samples, SAMPLE_RATE)
            recognizer.decode(stream)
            recognizer.getResult(stream).text.trim()
        } finally {
            stream.release()
        }
    }

    @Synchronized
    override fun close() = recognizer.release()
}
