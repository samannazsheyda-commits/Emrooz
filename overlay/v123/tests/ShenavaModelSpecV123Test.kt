package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class ShenavaModelSpecV123Test {
    @Test fun uses_accuracy_first_koochik_streaming_model() {
        assertEquals("models/shenava_koochik_stream/model.onnx", ShenavaModelSpec.modelPath)
        assertEquals("models/shenava_koochik_stream/tokens.txt", ShenavaModelSpec.tokensPath)
    }

    @Test fun keeps_koochik_full_context_finalizer() {
        assertEquals("models/shenava_v10_ctc_offline/model.onnx", ShenavaOfflineModelSpec.modelPath)
        assertEquals("models/shenava_v10_ctc_offline/tokens.txt", ShenavaOfflineModelSpec.tokensPath)
    }
}
