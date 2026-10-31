package com.nameemrooz.journal.speech

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Verifies the exact bundled Shenava v1.5 RNNT INT8 assets without copying them to storage. */
class ModelAssets(private val context: Context) {
    suspend fun verifyBundled(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val encoderOk = context.assets.openFd(ShenavaRnntRecognizer.ENCODER).use { it.length == 131_342_912L }
            val decoderOk = context.assets.openFd(ShenavaRnntRecognizer.DECODER).use { it.length == 3_956_194L }
            val joinerOk = context.assets.openFd(ShenavaRnntRecognizer.JOINER).use { it.length == 1_408_514L }
            val tokensOk = context.assets.open(ShenavaRnntRecognizer.TOKENS).use { it.available() > 8_000 }
            encoderOk && decoderOk && joinerOk && tokensOk
        }.getOrDefault(false)
    }
}
