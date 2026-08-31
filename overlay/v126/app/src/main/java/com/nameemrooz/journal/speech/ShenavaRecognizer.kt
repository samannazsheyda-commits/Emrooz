package com.nameemrooz.journal.speech

import android.content.Context
import com.k2fsa.sherpa.onnx.EndpointConfig
import com.k2fsa.sherpa.onnx.EndpointRule
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OnlineModelConfig
import com.k2fsa.sherpa.onnx.OnlineNeMoCtcModelConfig
import com.k2fsa.sherpa.onnx.OnlineRecognizer
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig
import com.k2fsa.sherpa.onnx.OnlineStream

/** Persian streaming CTC recognizer with endpoint detection enabled. */
class ShenavaRecognizer(context: Context) : AutoCloseable {
    private val recognizer: OnlineRecognizer
    private var stream: OnlineStream

    init {
        val modelConfig = OnlineModelConfig(
            neMoCtc = OnlineNeMoCtcModelConfig(model = ShenavaModelSpec.modelPath),
            tokens = ShenavaModelSpec.tokensPath,
            numThreads = minOf(4, maxOf(2, Runtime.getRuntime().availableProcessors() - 1)),
            debug = false,
            provider = "cpu",
        )
        recognizer = OnlineRecognizer(
            assetManager = context.assets,
            config = OnlineRecognizerConfig(
                featConfig = FeatureConfig(
                    sampleRate = SpeechTuning.SAMPLE_RATE,
                    featureDim = 80,
                ),
                modelConfig = modelConfig,
                endpointConfig = EndpointConfig(
                    rule1 = EndpointRule(false, 1.20f, 0.0f),
                    rule2 = EndpointRule(true, 0.52f, 0.0f),
                    rule3 = EndpointRule(false, 0.0f, 12.0f),
                ),
                enableEndpoint = true,
                decodingMethod = "greedy_search",
            ),
        )
        stream = recognizer.createStream()
    }

    fun resetSession() = resetSegment()

    fun resetSegment() {
        stream.release()
        stream = recognizer.createStream()
    }

    /** Feed microphone samples and return the cumulative hypothesis for this segment. */
    fun accept(samples: FloatArray): String {
        if (samples.isNotEmpty()) {
            stream.acceptWaveform(samples, SpeechTuning.SAMPLE_RATE)
            while (recognizer.isReady(stream)) recognizer.decode(stream)
        }
        return recognizer.getResult(stream).text.trim()
    }

    fun currentText(): String = recognizer.getResult(stream).text.trim()

    fun isEndpoint(): Boolean = recognizer.isEndpoint(stream)

    /** Flush only the current short segment. */
    fun finish(): String {
        stream.acceptWaveform(FloatArray(1_600), SpeechTuning.SAMPLE_RATE)
        stream.inputFinished()
        while (recognizer.isReady(stream)) recognizer.decode(stream)
        return recognizer.getResult(stream).text.trim()
    }

    override fun close() {
        stream.release()
        recognizer.release()
    }
}
