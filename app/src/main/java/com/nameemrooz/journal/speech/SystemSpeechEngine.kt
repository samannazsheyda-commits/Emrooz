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
    private val policy = SystemSpeechErrorPolicy(maxRecoverableRetries = 2)
    private val transcript = StableTranscriptMerger()
    private val closed = AtomicBoolean(false)
    private var recognizer: SpeechRecognizer? = null
    private var prepared = false
    private var preparing = false
    private var active = false
    private var stopping = false
    private var awaitingResult = false
    private var generation = 0L
    private var cycle = 0L
    private var cycleRunning = false
    private var retryDelayMs = 350L
    private var lastPublished = ""
    private var lastStablePublished = ""
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
            recognizer?.setRecognitionListener(listener(generation, cycle))
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
        val ticket = ++generation
        transcript.reset()
        policy.reset()
        retryDelayMs = 350L
        lastPublished = ""
        lastStablePublished = ""
        stopping = false
        active = true
        onListening(true)
        startCycle(ticket)
    } }

    override fun stop() { main.post {
        if (closed.get() || !active || stopping) return@post
        stopping = true
        if (!cycleRunning) { finish(true); return@post }
        val ticket = generation
        runCatching { recognizer?.stopListening() }
        // A misbehaving service must never leave the UI waiting indefinitely.
        main.postDelayed({ if (valid(ticket) && stopping) finish(true) }, 1_600L)
    } }

    private fun valid(ticket: Long) = !closed.get() && active && ticket == generation

    private fun currentCycle(ticket: Long, run: Long) = valid(ticket) && run == cycle

    private fun startCycle(ticket: Long) {
        if (!valid(ticket) || stopping) return
        awaitingResult = false
        val run = ++cycle
        cycleRunning = true
        try {
            // Each native utterance gets its own callback binder. Retired binders cannot
            // accidentally deliver a late error through the next cycle's listener.
            recognizer?.destroy()
            recognizer = if (onDeviceOnly) {
                if (Build.VERSION.SDK_INT < 31) throw UnsupportedOperationException("On-device recognition requires Android12")
                SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
            } else SpeechRecognizer.createSpeechRecognizer(appContext)
            recognizer?.setRecognitionListener(listener(ticket, run))
            recognizer?.startListening(recognizerIntent()) ?: error("Recognizer unavailable")
            armWatchdog(ticket, run, 12_000L)
        } catch (_: Exception) {
            recover(ticket, SystemSpeechFailure.CLIENT, "سرویس گفتار گوشی آماده نشد")
        }
    }

    private fun cancelWatchdog() { watchdog?.let(main::removeCallbacks); watchdog = null }
    private fun armWatchdog(ticket: Long, run: Long, delay: Long) {
        cancelWatchdog()
        watchdog = Runnable {
            if (currentCycle(ticket, run)) recover(ticket, SystemSpeechFailure.NETWORK_TIMEOUT, "سرویس گفتار گوشی پاسخ نداد؛ دوباره تلاش کنید")
        }.also { main.postDelayed(it, delay) }
    }
    private fun restart(ticket: Long, delay: Long = 100L) {
        cancelWatchdog()
        cycle++
        cycleRunning = false
        main.postDelayed({ if (valid(ticket) && !stopping) startCycle(ticket) }, delay)
    }

    private fun publish(value: String, stable: Boolean) {
        if (value.isBlank()) return
        if (value != lastPublished) {
            lastPublished = value
            policy.onSuccess()
            retryDelayMs = 350L
            onText(value)
        }
        if (stable && value != lastStablePublished) {
            lastStablePublished = value
            onStableText(value)
        }
    }

    private fun recover(ticket: Long, failure: SystemSpeechFailure, message: String) {
        cancelWatchdog()
        // Preserve the words already shown even when the provider sends no final result.
        publish(transcript.commit(""), stable = true)
        if (stopping) { finish(false); return }
        when (policy.onFailure(failure)) {
            SystemSpeechAction.RETRY -> {
                val delay = retryDelayMs
                retryDelayMs = (retryDelayMs * 2).coerceAtMost(2000L)
                restart(ticket, delay)
            }
            SystemSpeechAction.STOP, SystemSpeechAction.FALLBACK -> fail(message)
        }
    }

    private fun finish(deliverPartial: Boolean) {
        cancelWatchdog()
        val wasActive = active
        active = false
        stopping = false
        cycleRunning = false
        generation++
        cycle++
        runCatching { recognizer?.cancel() }
        if (wasActive) {
            if (deliverPartial) publish(transcript.commit(""), stable = true)
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
        if (Build.VERSION.SDK_INT >= 33) putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY)
    }
    private fun text(results: Bundle?) = SystemSpeechResultPolicy.select(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
    private fun clean(raw: String) = if (languageTag.startsWith("fa", true)) PersianText.clean(raw, false) else raw.replace(Regex("\\s+"), " ").trim()

    private fun listener(ticket: Long, run: Long) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { if (currentCycle(ticket, run)) armWatchdog(ticket, run, 45_000L) }
        override fun onBeginningOfSpeech() { if (currentCycle(ticket, run)) armWatchdog(ticket, run, 45_000L) }
        override fun onRmsChanged(rmsdB: Float) {
            if (currentCycle(ticket, run) && !stopping && !awaitingResult) armWatchdog(ticket, run, 45_000L)
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() {
            if (currentCycle(ticket, run)) { awaitingResult = true; armWatchdog(ticket, run, 10_000L) }
        }
        override fun onError(error: Int) {
            if (!currentCycle(ticket, run)) return
            cancelWatchdog()
            if (stopping) { finish(true); return }
            if (error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) {
                if (onDeviceOnly) unavailable("این زبان در گفتار آفلاین گوشی آماده نیست؛ سرویس گفتار گوشی را انتخاب کنید")
                else fail("این زبان در سرویس گفتار گوشی آماده نیست (کد $error)")
                return
            }
            recover(ticket, mapFailure(error), errorMessage(error) + " (کد $error)")
        }
        override fun onResults(results: Bundle?) {
            if (!currentCycle(ticket, run)) return
            cancelWatchdog()
            val value = clean(text(results))
            publish(transcript.commit(value), stable = true)
            if (stopping) finish(false) else restart(ticket)
        }
        override fun onPartialResults(partialResults: Bundle?) {
            if (!currentCycle(ticket, run)) return
            if (!stopping) armWatchdog(ticket, run, if (awaitingResult) 10_000L else 45_000L)
            val value = clean(text(partialResults))
            if (value.isNotBlank()) publish(transcript.updatePartial(value), stable = false)
        }
        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun errorMessage(error: Int) = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "اجازه‌ی میکروفون برای تبدیل صدا لازم است"
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "گفتاری دریافت نشد؛ دوباره تلاش کنید"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ارتباط سرویس گفتار برقرار نشد؛ متن شما حفظ شده است"
        SpeechRecognizer.ERROR_AUDIO -> "دریافت صدا قطع شد؛ متن شما حفظ شده است"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "درخواست‌های گفتار بیش از حد شد؛ کمی صبر کنید"
        else -> "سرویس گفتار گوشی پاسخ نداد؛ دوباره تلاش کنید"
    }

    private fun mapFailure(error: Int) = when (error) {
        SpeechRecognizer.ERROR_NO_MATCH -> SystemSpeechFailure.NO_MATCH
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SystemSpeechFailure.SPEECH_TIMEOUT
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SystemSpeechFailure.BUSY
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SystemSpeechFailure.PERMISSION
        SpeechRecognizer.ERROR_NETWORK -> SystemSpeechFailure.NETWORK
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SystemSpeechFailure.NETWORK_TIMEOUT
        SpeechRecognizer.ERROR_SERVER, SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> SystemSpeechFailure.SERVER
        SpeechRecognizer.ERROR_CLIENT -> SystemSpeechFailure.CLIENT
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
