package com.nameemrooz.journal.speech

enum class PreferredAudioSource {
    UNPROCESSED,
    VOICE_RECOGNITION
}

object AudioSourceSelector {
    fun choose(@Suppress("UNUSED_PARAMETER") unprocessedSupported: Boolean): PreferredAudioSource =
        PreferredAudioSource.VOICE_RECOGNITION
}
