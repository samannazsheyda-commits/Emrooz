package com.nameemrooz.journal.speech

import android.content.Context

/**
 * UI-compatible facade. The implementation lives in SpeechSessionController so
 * capture, streaming, segmentation, final ASR and editing have separate roles.
 */
class LiveSpeechEngine(
    context: Context,
    onText: (String) -> Unit,
    onListening: (Boolean) -> Unit,
    onFinalizing: (Boolean) -> Unit = {},
    onReady: (Boolean) -> Unit,
    onError: (String) -> Unit,
) : AutoCloseable {
    private val controller = SpeechSessionController(
        context = context.applicationContext,
        onText = onText,
        onListening = onListening,
        onFinalizing = onFinalizing,
        onReady = onReady,
        onError = onError,
    )

    fun prepare() = controller.prepare()
    fun start() = controller.start()
    fun stop() = controller.stop()
    override fun close() = controller.close()
}
