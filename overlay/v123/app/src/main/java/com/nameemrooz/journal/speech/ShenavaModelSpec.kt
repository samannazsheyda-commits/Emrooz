package com.nameemrooz.journal.speech

enum class ShenavaModelKind {
    STREAMING_NEMO_CTC
}

/** v1.2.3: accuracy-first Koochik streaming model for live Persian text. */
object ShenavaModelSpec {
    val kind: ShenavaModelKind = ShenavaModelKind.STREAMING_NEMO_CTC
    const val modelPath: String = "models/shenava_koochik_stream/model.onnx"
    const val tokensPath: String = "models/shenava_koochik_stream/tokens.txt"
}

/** Keep the proven full-context Koochik INT8 pass for final transcription. */
object ShenavaOfflineModelSpec {
    const val modelPath: String = "models/shenava_v10_ctc_offline/model.onnx"
    const val tokensPath: String = "models/shenava_v10_ctc_offline/tokens.txt"
}
