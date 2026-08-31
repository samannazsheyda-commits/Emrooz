package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class WhisperFinalRecognizerContractTest {
    @Test
    fun usesPinnedWhisperSmallModelAsset() {
        assertEquals(
            "models/whisper/ggml-small-q5_1.bin",
            WhisperFinalRecognizer.MODEL_PATH
        )
    }
}
