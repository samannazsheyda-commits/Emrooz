package com.nameemrooz.journal.speech

/** Owned decoder boundary; microphone capture controls its lifecycle. */
interface SpeechDecoder : AutoCloseable {
    fun transcribe(samples: FloatArray): String
}
