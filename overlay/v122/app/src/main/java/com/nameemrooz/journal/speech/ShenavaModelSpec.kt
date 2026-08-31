package com.nameemrooz.journal.speech

enum class ShenavaModelKind {
    STREAMING_NEMO_CTC
}

/** Lightweight streaming model: used only for responsive live preview. */
object ShenavaModelSpec {
    val kind: ShenavaModelKind = ShenavaModelKind.STREAMING_NEMO_CTC
    const val modelPath: String = "models/shenava_rizeh_stream/model.onnx"
    const val tokensPath: String = "models/shenava_rizeh_stream/tokens.txt"
}

/** Full-context Koochik model: used once after Stop for the saved final text. */
object ShenavaOfflineModelSpec {
    const val modelPath: String = "models/shenava_v10_ctc_offline/model.onnx"
    const val tokensPath: String = "models/shenava_v10_ctc_offline/tokens.txt"
}
