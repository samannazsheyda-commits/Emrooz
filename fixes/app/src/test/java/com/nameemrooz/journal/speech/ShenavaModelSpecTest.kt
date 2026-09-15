package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenavaModelSpecTest {
    @Test
    fun bundledModelIsDeclaredAsStreamingNemoCtc() {
        assertEquals(ShenavaModelKind.STREAMING_NEMO_CTC, ShenavaModelSpec.kind)
        assertTrue(ShenavaModelSpec.modelPath.endsWith("model.onnx"))
        assertTrue(ShenavaModelSpec.tokensPath.endsWith("tokens.txt"))
    }
}
