package com.nameemrooz.journal.speech

enum class ShenavaModelKind {
    STREAMING_NEMO_CTC
}

object ShenavaModelSpec {
    val kind: ShenavaModelKind = ShenavaModelKind.STREAMING_NEMO_CTC
    const val modelPath: String = "models/shenava_v10_ctc/model.onnx"
    const val tokensPath: String = "models/shenava_v10_ctc/tokens.txt"
}
