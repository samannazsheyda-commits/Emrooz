package com.nameemrooz.whisper

import java.util.concurrent.atomic.AtomicLong

/** CPU-only offline decoder. No audio is written to disk or sent over a network. */
class WhisperContext private constructor(handle: Long) : AutoCloseable {
    private val handle = AtomicLong(handle)

    /** Serialized in native code. Input must be mono float PCM at 16 kHz, at most 30 s. */
    fun transcribe(samples: FloatArray, language: String): String {
        val current = handle.get()
        check(current != 0L) { "Whisper is closed" }
        require(samples.size <= 30 * 16000) { "PCM exceeds 30 seconds" }
        require(language == "fa" || language == "en") { "Unsupported language" }
        return nativeTranscribe(current, samples, language)
    }

    /** Permanently abort this context. May be called while transcribe is running. */
    fun cancel() { handle.get().takeIf { it != 0L }?.let(::nativeCancel) }

    /** Safe during decode: native ownership defers destruction until its last caller exits. */
    override fun close() {
        val current = handle.getAndSet(0L)
        if (current != 0L) nativeFree(current)
    }

    private external fun nativeTranscribe(handle: Long, samples: FloatArray, language: String): String
    private external fun nativeCancel(handle: Long)
    private external fun nativeFree(handle: Long)

    companion object {
        init { System.loadLibrary("emrooz_whisper") }
        fun create(modelPath: String): WhisperContext {
            val handle = nativeCreate(modelPath)
            check(handle != 0L) { "Offline Whisper initialization failed" }
            return WhisperContext(handle)
        }
        @JvmStatic private external fun nativeCreate(modelPath: String): Long
    }
}
