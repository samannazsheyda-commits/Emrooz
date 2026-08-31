package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioSourceSelectorV121Test {
    @Test
    fun `uses voice recognition processing even when raw input is available`() {
        assertEquals(
            PreferredAudioSource.VOICE_RECOGNITION,
            AudioSourceSelector.choose(unprocessedSupported = true)
        )
    }

    @Test
    fun `uses voice recognition processing when raw input is unavailable`() {
        assertEquals(
            PreferredAudioSource.VOICE_RECOGNITION,
            AudioSourceSelector.choose(unprocessedSupported = false)
        )
    }
}
