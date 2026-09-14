package com.nameemrooz.journal.speech

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.nameemrooz.journal.util.PersianText
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional high-accuracy speech path backed by the device's default RecognitionService.
 *
 * Emrooz never records this service's audio to a file. Depending on the selected device
 * service, recognition may be processed outside the app process and may use network.
 */
class SystemSpeechEngine(
    private val context: Context,
    private val languageTag: String,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onReady: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val onFallbackRequested: (String) -> Unit,
    private val onStableText: (String) -> Unit = {},
) : SpeechEngine {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val policy = SystemSpeechErrorPolicy(maxRecoverableRetries = 1)
    private val transcript = StableTranscriptMerger()
    private val sessionActive = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)

    @Volatile private var recognizer: SpeechRecognizer? = null

    override fun prepare() {
        if (closed.get()) return
        mainHandler.post {
            if (closed.get()) return@post
            if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                onReady(false)
                onFallbackRequested("سرویس گفتار گوشی در دسترس نیست")
                return@post
            }
            if (recognizer == null) {
                recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).also {
                    it.setRecognitionListener(listener)
                }
            }
            onReady(true)
        }
    }

    override fun start() {
        if (closed.get() || sessionActive.get()) return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("برای نوشتن با صدا اجازه‌ی میکروفون لازم است")
            return
        }
        sessionActive.set(true)
        policy.reset()
        transcript.reset()
        onListening(true)
        mainHandler.post {
            if (recognizer == null) prepare()
            mainHandler.postDelayed({ startListeningNow() }, 40L)
        }
    }

    override fun stop() {
        sessionActive.set(false)
        mainHandler.post {
            runCatching { recognizer?.stopListening() }
            runCatching { recognizer?.cancel() }
            onListening(false)
        }
    }

    private fun startListeningNow() {
        if (!sessionActive.get() || closed.get()) return
        val active = recognizer
        if (active == null) {
            fallback("سرویس گفتار گوشی آماده نشد")
            return
        }
        runCatching { active.startListening(recognizerIntent()) }
            .onFailure { fallback("سرویس گفتار گوشی آماده نشد") }
    }

    private fun recognizerIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    private fun bestText(results: Bundle?): String =
        results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()
            .orEmpty()

    private fun handlePartial(raw: String) {
        val clean = clean(raw, final = false)
        if (clean.isNotBlank()) onText(transcript.updatePartial(clean))
    }

    private fun handleFinal(raw: String) {
        val clean = clean(raw, final = false)
        if (clean.isNotBlank()) {
            val merged = transcript.commit(clean)
            onText(merged)
            onStableText(merged)
        }
    }

    private fun clean(raw: String, final: Boolean): String =
        if (languageTag.startsWith("fa", ignoreCase = true)) PersianText.clean(raw, final)
        else raw.replace(Regex("\\s+"), " ").trim()

    private fun restartSoon(delayMs: Long = 180L) {
        if (!sessionActive.get() || closed.get()) return
        mainHandler.postDelayed({ startListeningNow() }, delayMs)
    }

    private fun fallback(message: String) {
        if (!sessionActive.getAndSet(false)) return
        runCatching { recognizer?.cancel() }
        onListening(false)
        onFallbackRequested(message)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            if (sessionActive.get()) onListening(true)
        }

        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit

        override fun onError(error: Int) {
            if (!sessionActive.get()) return
            when (policy.onFailure(mapFailure(error))) {
                SystemSpeechAction.RETRY -> restartSoon(260L)
                SystemSpeechAction.FALLBACK -> fallback("دقت بیشتر در دسترس نبود؛ آفلاین ادامه می‌دهم")
                SystemSpeechAction.STOP -> {
                    sessionActive.set(false)
                    onListening(false)
                    onError("اجازه‌ی میکروفون برای تبدیل صدا لازم است")
                }
            }
        }

        override fun onResults(results: Bundle?) {
            if (!sessionActive.get()) return
            val text = bestText(results)
            if (text.isNotBlank()) {
                handleFinal(text)
                policy.onSuccess()
            }
            restartSoon()
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!sessionActive.get()) return
            val text = bestText(partialResults)
            if (text.isNotBlank()) handlePartial(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun mapFailure(error: Int): SystemSpeechFailure = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> SystemSpeechFailure.NO_MATCH
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SystemSpeechFailure.SPEECH_TIMEOUT
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SystemSpeechFailure.BUSY
        SpeechRecognizer.ERROR_NETWORK -> SystemSpeechFailure.NETWORK
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SystemSpeechFailure.NETWORK_TIMEOUT
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> SystemSpeechFailure.SERVER
        SpeechRecognizer.ERROR_CLIENT -> SystemSpeechFailure.CLIENT
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SystemSpeechFailure.PERMISSION
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> SystemSpeechFailure.LANGUAGE
        else -> SystemSpeechFailure.OTHER
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        sessionActive.set(false)
        mainHandler.post {
            runCatching { recognizer?.cancel() }
            runCatching { recognizer?.destroy() }
            recognizer = null
            onListening(false)
        }
    }

    companion object {
        fun isAvailable(context: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

        fun isOnDeviceAvailable(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    }
}
