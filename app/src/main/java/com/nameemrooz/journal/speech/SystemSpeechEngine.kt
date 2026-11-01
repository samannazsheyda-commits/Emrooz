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
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.nameemrooz.journal.util.PersianText
import java.util.concurrent.atomic.AtomicBoolean

/** Device speech only: the offline factory never silently selects a network service. */
class SystemSpeechEngine(
    context: Context,
    private val languageTag: String,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onReady: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val onFallbackRequested: (String) -> Unit,
    private val onStableText: (String) -> Unit = {},
    private val onDeviceOnly: Boolean = false,
) : SpeechEngine {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val policy = SystemSpeechErrorPolicy(maxRecoverableRetries = 1)
    private val transcript = StableTranscriptMerger()
    private val closed = AtomicBoolean(false)
    private var recognizer: SpeechRecognizer? = null
    private var prepared = false
    private var preparing = false
    private var active = false
    private var stopping = false
    private var awaitingResult = false
    private var generation = 0L
    private var preparation = 0L
    private var watchdog: Runnable? = null

    override fun prepare() { main.post {
        if (closed.get() || preparing || prepared) return@post
        preparing = true
        val ticket = ++preparation
        try {
            if (onDeviceOnly) {
                if (Build.VERSION.SDK_INT < 31 || !SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)) {
                    unavailable("گفتار آفلاین روی این گوشی در دسترس نیست؛ سرویس گفتار گوشی را انتخاب کنید")
                    return@post
                }
                recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else {
                if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
                    unavailable("سرویس گفتار گوشی در دسترس نیست")
                    return@post
                }
                recognizer = SpeechRecognizer.createSpeechRecognizer(appContext)
            }
            recognizer?.setRecognitionListener(listener(generation))
            if (onDeviceOnly && Build.VERSION.SDK_INT >= 33) {
                recognizer?.checkRecognitionSupport(recognizerIntent(), ContextCompat.getMainExecutor(appContext), object : RecognitionSupportCallback {
                    override fun onSupportResult(support: RecognitionSupport) {
                        if (closed.get() || ticket != preparation || !preparing) return
                        when (DeviceSpeechSupport.classify(languageTag, support.installedOnDeviceLanguages, support.supportedOnDeviceLanguages, support.pendingOnDeviceLanguages)) {
                            DeviceSpeechReadiness.READY, DeviceSpeechReadiness.UNKNOWN -> ready(ticket)
                            DeviceSpeechReadiness.DOWNLOAD_REQUIRED -> unavailable("مدل این زبان برای گفتار آفلاین نصب نیست؛ سرویس گفتار گوشی را انتخاب کنید")
                            DeviceSpeechReadiness.UNSUPPORTED -> unavailable("این زبان در گفتار آفلاین گوشی پشتیبانی نمی‌شود؛ سرویس گفتار گوشی را انتخاب کنید")
                        }
                    }
                    override fun onError(error: Int) {
                        if (closed.get() || ticket != preparation || !preparing) return
                        // Some providers cannot enumerate languages. The explicitly on-device
                        // recognizer may still be tried; it cannot become the online factory.
                        if (error == SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT) ready(ticket)
                        else unavailable("گفتار آفلاین برای این زبان آماده نیست؛ سرویس گفتار گوشی را انتخاب کنید")
                    }
                })
                main.postDelayed({ if (!closed.get() && ticket == preparation && preparing) {
                    unavailable("بررسی گفتار آفلاین پاسخ نداد؛ سرویس گفتار گوشی را انتخاب کنید")
                } }, 5_000L)
            } else ready(ticket)
        } catch (_: Exception) { unavailable(if (onDeviceOnly) "گفتار آفلاین روی این گوشی در دسترس نیست؛ سرویس گفتار گوشی را انتخاب کنید" else "سرویس گفتار گوشی آماده نشد") }
        catch (_: LinkageError) { unavailable("سرویس گفتار گوشی آماده نشد") }
    } }

    private fun ready(ticket: Long) {
        if (closed.get() || ticket != preparation) return
        preparing = false
        prepared = true
        onReady(true)
    }

    private fun unavailable(message: String) {
        if (closed.get()) return
        preparing = false
        prepared = false
        preparation++
        finish(false)
        runCatching { recognizer?.destroy() }
        recognizer = null
        onReady(false)
        onFallbackRequested(message)
    }

    override fun start() { main.post {
        if (closed.get() || active) return@post
        if (!prepared || recognizer == null) {
            onError("سرویس گفتار گوشی آماده نشد")
            return@post
        }
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onError("برای نوشتن با صدا اجازه‌ی میکروفون لازم است")
            return@post
        }
        // A fresh binder/listener for each user recording prevents late callbacks
        // from a canceled recording being delivered into the next one.
        try {
            recognizer?.destroy()
            recognizer = if (onDeviceOnly) {
                if (Build.VERSION.SDK_INT < 31) throw UnsupportedOperationException("On-device recognition requires Android 12")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else SpeechRecognizer.createSpeechRecognizer(appContext)
        } catch (_: Exception) { unavailable("سرویس گفتار گوشی آماده نشد"); return@post }
        val ticket = ++generation
        transcript.reset()
        policy.reset()
        stopping = false
        active = true
        recognizer?.setRecognitionListener(listener(ticket))
        onListening(true)
        startCycle(ticket)
    } }

    override fun stop() { main.post {
        if (closed.get() || !active || stopping) return@post
        stopping = true
        val ticket = generation
        runCatching { recognizer?.stopListening() }
        // A misbehaving service must never leave the UI waiting indefinitely.
        main.postDelayed({ if (valid(ticket) && stopping) finish(true) }, 1_600L)
    } }

    private fun valid(ticket: Long) = !closed.get() && active && ticket == generation

    private fun startCycle(ticket: Long) {
        if (!valid(ticket) || stopping) return
        awaitingResult = false
        try {
            recognizer?.startListening(recognizerIntent()) ?: error("Recognizer unavailable")
            armWatchdog(ticket, 12_000L)
        } catch (_: Exception) { fail("سرویس گفتار گوشی آماده نشد") }
    }

    private fun cancelWatchdog() { watchdog?.let(main::removeCallbacks); watchdog = null }
    private fun armWatchdog(ticket: Long, delay: Long) {
        cancelWatchdog()
        watchdog = Runnable { if (valid(ticket)) fail("سرویس گفتار گوشی پاسخ نداد؛ دوباره تلاش کنید") }.also { main.postDelayed(it, delay) }
    }
    private fun restart(ticket: Long, delay: Long = 250L) {
        cancelWatchdog()
        main.postDelayed({ if (valid(ticket) && !stopping) startCycle(ticket) }, delay)
    }

    private fun finish(deliverPartial: Boolean) {
        cancelWatchdog()
        val wasActive = active
        active = false
        stopping = false
        generation++
        runCatching { recognizer?.cancel() }
        if (wasActive) {
            if (deliverPartial) transcript.current().takeIf { it.isNotBlank() }?.let(onStableText)
            onListening(false)
        }
    }
    private fun fail(message: String) { finish(true); onError(message) }

    private fun recognizerIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        // EXTRA_PREFER_OFFLINE is only a hint; privacy rests on the explicit factory.
        if (onDeviceOnly) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        if (Build.VERSION.SDK_INT >= 33) putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
    }
    private fun text(results: Bundle?) = SystemSpeechResultPolicy.select(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
    private fun clean(raw: String) = if (languageTag.startsWith("fa", true)) PersianText.clean(raw, false) else raw.replace(Regex("\\s+"), " ").trim()

    private fun listener(ticket: Long) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { if (valid(ticket)) armWatchdog(ticket, 45_000L) }
        override fun onBeginningOfSpeech() { if (valid(ticket)) armWatchdog(ticket, 45_000L) }
        override fun onRmsChanged(rmsdB: Float) {
            if (valid(ticket) && !stopping && !awaitingResult) armWatchdog(ticket, 45_000L)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() { if (valid(ticket)) { awaitingResult = true; armWatchdog(ticket, 10_000L) } }
        override fun onError(error: Int) {
            if (!valid(ticket)) return
            cancelWatchdog()
            if (stopping) { finish(true); return }
            if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                if (onDeviceOnly) unavailable("این زبان در گفتار آفلاین گوشی آماده نیست؛ سرویس گفتار گوشی را انتخاب کنید")
                else fail("این زبان در سرویس گفتار گوشی آماده نیست")
                return
            }
            when (policy.onFailure(mapFailure(error))) {
                SystemSpeechAction.RETRY -> restart(ticket, 350L)
                SystemSpeechAction.STOP -> fail("اجازه‌ی میکروفون برای تبدیل صدا لازم است")
                SystemSpeechAction.FALLBACK -> fail(if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) "گفتاری دریافت نشد؛ دوباره تلاش کنید" else "سرویس گفتار گوشی پاسخ نداد؛ دوباره تلاش کنید")
            }
        }
        override fun onResults(results: Bundle?) {
            if (!valid(ticket)) return
            cancelWatchdog()
            val value = clean(text(results))
            if (value.isNotBlank()) {
                val accepted = transcript.commit(value)
                onText(accepted)
                onStableText(accepted)
                policy.onSuccess()
            }
            if (stopping) finish(true) else restart(ticket)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            if (!valid(ticket)) return
            if (!stopping) armWatchdog(ticket, 45_000L)
            val value = clean(text(partialResults))
            if (value.isNotBlank()) onText(transcript.updatePartial(value))
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun mapFailure(error: Int) = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> SystemSpeechFailure.NO_MATCH
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SystemSpeechFailure.SPEECH_TIMEOUT
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SystemSpeechFailure.BUSY
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SystemSpeechFailure.PERMISSION
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SystemSpeechFailure.NETWORK
        else -> SystemSpeechFailure.OTHER
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        main.post {
            preparation++
            preparing = false
            prepared = false
            finish(false)
            runCatching { recognizer?.destroy() }
            recognizer = null
        }
    }

    companion object {
        fun isAvailable(context: Context): Boolean = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
        fun isOnDeviceAvailable(context: Context): Boolean = Build.VERSION.SDK_INT >= 31 &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
    }
}
