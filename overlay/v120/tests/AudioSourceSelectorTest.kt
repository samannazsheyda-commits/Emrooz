package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSourceSelectorTest {
    @Test
    fun usesUnprocessedOnlyWhenDeviceExplicitlySupportsIt() {
        assertEquals(
            PreferredAudioSource.UNPROCESSED,
            AudioSourceSelector.choose(unprocessedSupported = true)
        )
    }

    @Test
    fun fallsBackToVoiceRecognitionOtherwise() {
        assertEquals(
            PreferredAudioSource.VOICE_RECOGNITION,
            AudioSourceSelector.choose(unprocessedSupported = false)
        )
    }
}
