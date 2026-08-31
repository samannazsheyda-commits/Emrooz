package com.nameemrooz.journal.speech

enum class PreferredAudioSource {
    UNPROCESSED,
    VOICE_RECOGNITION
}

object AudioSourceSelector {
    fun choose(unprocessedSupported: Boolean): PreferredAudioSource =
        if (unprocessedSupported) PreferredAudioSource.UNPROCESSED
        else PreferredAudioSource.VOICE_RECOGNITION
}
