package com.nameemrooz.journal.speech

import android.content.Context
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.runBlocking

class WhisperFinalRecognizer(context: Context) : AutoCloseable {
    private var whisper: WhisperContext? = WhisperContext.createContextFromAsset(
        context.assets,
        MODEL_PATH
    )

    suspend fun recognize(samples: FloatArray): String {
        if (samples.isEmpty()) return ""
        val active = whisper ?: return ""
        return active.transcribeData(samples, printTimestamp = false).trim()
    }

    override fun close() {
        val active = whisper ?: return
        whisper = null
        runBlocking { active.release() }
    }

    companion object {
        const val MODEL_PATH = "models/whisper/ggml-small-q5_1.bin"
    }
}
