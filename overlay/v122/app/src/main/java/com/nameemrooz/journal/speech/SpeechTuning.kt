package com.nameemrooz.journal.speech

object SpeechTuning {
    const val SAMPLE_RATE = 16_000
    const val READ_SAMPLES = 4_096 // 256 ms; fewer JNI/decode calls than v1.2.1
    const val LIVE_QUEUE_CAPACITY = 8 // bounded: no unlimited latency growth
    const val FINAL_SILENCE_SAMPLES = 1_600
    const val MIN_FINALIZER_SAMPLES = 8_000
    const val MAX_FINALIZER_SECONDS = 15 * 60
    const val MAX_FINALIZER_SAMPLES = SAMPLE_RATE * MAX_FINALIZER_SECONDS
}
