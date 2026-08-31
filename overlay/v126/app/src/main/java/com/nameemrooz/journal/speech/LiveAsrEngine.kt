package com.nameemrooz.journal.speech

import android.content.Context
import com.nameemrooz.journal.util.PersianEditorV2

/** Streaming-ASR boundary: hypotheses + endpoint only. */
data class LiveAsrUpdate(
    val partial: String,
    val endpoint: Boolean,
)

class LiveAsrEngine(context: Context) : AutoCloseable {
    private val recognizer = ShenavaRecognizer(context.applicationContext)

    fun accept(pcm: ShortArray, length: Int = pcm.size): LiveAsrUpdate {
        require(length in 0..pcm.size)
        if (length == 0) {
            return LiveAsrUpdate(
                partial = PersianEditorV2.cleanLive(recognizer.currentText()),
                endpoint = recognizer.isEndpoint(),
            )
        }
        val samples = FloatArray(length)
        return try {
            for (i in 0 until length) samples[i] = pcm[i] / 32768.0f
            val partial = PersianEditorV2.cleanLive(recognizer.accept(samples))
            LiveAsrUpdate(partial = partial, endpoint = recognizer.isEndpoint())
        } finally {
            samples.fill(0f)
        }
    }

    fun finishSegment(): String = PersianEditorV2.cleanLive(recognizer.finish())

    fun resetSegment() = recognizer.resetSegment()

    override fun close() = recognizer.close()
}
