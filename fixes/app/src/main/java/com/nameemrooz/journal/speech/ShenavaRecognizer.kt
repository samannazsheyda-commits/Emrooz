package com.nameemrooz.journal.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineNeMoCtcModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream

/** Persian streaming CTC recognizer loaded directly from APK assets. */
class ShenavaRecognizer(context: Context) : AutoCloseable {
    private val recognizer: OnlineRecognizer
    private var stream: OnlineStream

    init {
        val modelConfig = OnlineModelConfig(
            neMoCtc = OnlineNeMoCtcModelConfig(model = ShenavaModelSpec.modelPath),
            tokens = ShenavaModelSpec.tokensPath,
            numThreads = minOf(4, maxOf(2, Runtime.getRuntime().availableProcessors() - 1)),
            debug = false,
            provider = "cpu"
        )
        recognizer = OnlineRecognizer(
            assetManager = context.assets,
            config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(sampleRate = SpeechTuning.SAMPLE_RATE, featureDim = 80),
                modelConfig = modelConfig,
                enableEndpoint = false,
                decodingMethod = "greedy_search"
            )
        )
        stream = recognizer.createStream()
    }

    fun resetSession() {
        stream.release()
        stream = recognizer.createStream()
    }

    /** Feed a short microphone chunk and return the current cumulative partial transcript. */
    fun accept(samples: FloatArray): String {
        if (samples.isEmpty()) return recognizer.getResult(stream).text.trim()
        stream.acceptWaveform(samples, SpeechTuning.SAMPLE_RATE)
        while (recognizer.isReady(stream)) recognizer.decode(stream)
        return recognizer.getResult(stream).text.trim()
    }

    /** Flush the streaming decoder when the user stops speaking. */
    fun finish(): String {
        // 100 ms at 16 kHz is enough right-context to flush the final word without a long stop delay.
        stream.acceptWaveform(FloatArray(1600), SpeechTuning.SAMPLE_RATE)
        stream.inputFinished()
        while (recognizer.isReady(stream)) recognizer.decode(stream)
        return recognizer.getResult(stream).text.trim()
    }

    override fun close() {
        stream.release()
        recognizer.release()
    }
}
