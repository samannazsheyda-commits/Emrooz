package com.nameemrooz.journal.speech

import com.k2fsa.sherpa.onnx.OfflineModelType

/** v1.2.3: accuracy-first Koochik streaming model for live Persian text. */
object ShenavaModelSpec {
    val kind: OfflineModelType = OfflineModelType.STREAMING_NEMO_CTC
    const val modelPath = "models/shenava_koochik_stream/model.onnx"
    const val tokensPath = "models/shenava_koochik_stream/tokens.txt"
}

/** Keep the proven full-context Koochik INT8 pass for final transcription. */
object ShenavaOfflineModelSpec {
    const val modelPath = "models/shenava_v10_ctc_offline/model.onnx"
    const val tokensPath = "models/shenava_v10_ctc_offline/tokens.txt"
}
