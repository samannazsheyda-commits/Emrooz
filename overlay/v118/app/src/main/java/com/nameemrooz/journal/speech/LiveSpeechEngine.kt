package com.nameemrooz.journal.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.nameemrooz.journal.util.PersianNameCorrector
import com.nameemrooz.journal.util.PersianText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Two-pass on-device Persian speech recognition.
 *
 * Pass 1: streaming CTC gives live draft text.
 * Pass 2: non-streaming full-context CTC re-decodes the same in-RAM samples after stop.
 *
 * Microphone capture runs independently from decoding so a slow decoder never blocks AudioRecord.read().
 * Privacy invariant: microphone samples are never written to disk and are wiped after use.
 */
class LiveSpeechEngine(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onFinalizing: (Boolean) -> Unit = {},
    private val onReady: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val preparing = AtomicBoolean(false)
    private val preparingNames = AtomicBoolean(false)
    private var recognizer: ShenavaRecognizer? = null
    @Volatile private var nameCorrector: PersianNameCorrector? = null
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var decodeJob: Job? = null
    private val sampleRate = SpeechTuning.SAMPLE_RATE

    fun prepare() {
        prepareNameLexicon()
        if (recognizer != null) {
            emitReady(true)
            return
        }
        if (!preparing.compareAndSet(false, true)) return
        emitReady(false)
        scope.launch {
            try {
                val loaded = ShenavaRecognizer(context.applicationContext)
                if (recognizer == null) recognizer = loaded else loaded.close()
                emitReady(true)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to prepare Persian streaming model", t)
                emitReady(false)
                emitError("مدل فارسی آماده نشد")
            } finally {
                preparing.set(false)
            }
        }
    }

    private fun prepareNameLexicon() {
        if (nameCorrector != null || !preparingNames.compareAndSet(false, true)) return
        scope.launch {
            try {
                val loaded = PersianNameCorrector(context.applicationContext)
                loaded.warmUp()
                nameCorrector = loaded
            } catch (t: Throwable) {
                Log.w(TAG, "Offline name lexicon warm-up failed", t)
            } finally {
                preparingNames.set(false)
            }
        }
    }

    fun start() {
        if (running.get()) return
        val activeRecognizer = recognizer
        if (activeRecognizer == null) {
            prepare()
            emitError("چند لحظه صبر کن تا نوشتن آماده شود")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            emitError("برای نوشتن با صدا اجازه‌ی میکروفون لازم است")
            return
        }

        try {
            activeRecognizer.resetSession()
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to reset streaming recognizer", t)
            emitError("نوشتن با صدا آماده نشد؛ دوباره امتحان کن")
            return
        }

        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) {
            emitError("میکروفون آماده نشد")
            return
        }

        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuffer * 4, 32768)
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            emitError("میکروفون آماده نشد")
            return
        }

        try {
            record.startRecording()
        } catch (t: Throwable) {
            Log.e(TAG, "Unable to start AudioRecord", t)
            record.release()
            emitError("میکروفون شروع نشد")
            return
        }

        val pcmQueue = Channel<ShortArray>(Channel.UNLIMITED)
        audioRecord = record
        running.set(true)
        emitListening(true)
        emitFinalizing(false)

        decodeJob = scope.launch(Dispatchers.Default) {
            val audioChunks = ArrayList<FloatArray>()
            var totalSamples = 0
            var finalizerOverflow = false
            var lastEmitted = ""
            var failed = false

            try {
                for (pcm in pcmQueue) {
                    val samples = FloatArray(pcm.size)
                    for (i in pcm.indices) samples[i] = pcm[i] / 32768.0f
                    java.util.Arrays.fill(pcm, 0.toShort())

                    if (totalSamples < SpeechTuning.MAX_FINALIZER_SAMPLES) {
                        val keep = minOf(samples.size, SpeechTuning.MAX_FINALIZER_SAMPLES - totalSamples)
                        if (keep > 0) {
                            audioChunks += if (keep == samples.size) samples.copyOf() else samples.copyOf(keep)
                            totalSamples += keep
                        }
                        if (keep < samples.size) finalizerOverflow = true
                    } else {
                        finalizerOverflow = true
                    }

                    val partial = PersianText.clean(activeRecognizer.accept(samples))
                    java.util.Arrays.fill(samples, 0f)
                    if (partial.isNotBlank() && partial != lastEmitted) {
                        lastEmitted = partial
                        emitText(partial)
                    }
                }

                val streamingFinal = PersianText.clean(activeRecognizer.finish(), final = true)
                val quickFinal = nameCorrector?.correct(streamingFinal) ?: streamingFinal
                if (quickFinal.isNotBlank() && quickFinal != lastEmitted) {
                    lastEmitted = quickFinal
                    emitText(quickFinal)
                }

                // Free the streaming model before loading the accurate full-context model.
                try { activeRecognizer.close() } catch (_: Throwable) {}
                if (recognizer === activeRecognizer) recognizer = null
                emitReady(false)

                val session = FloatArray(totalSamples)
                var offset = 0
                audioChunks.forEach { chunk ->
                    chunk.copyInto(session, offset)
                    offset += chunk.size
                    java.util.Arrays.fill(chunk, 0f)
                }
                audioChunks.clear()

                val fullContext = if (!finalizerOverflow && session.size >= SpeechTuning.MIN_FINALIZER_SAMPLES) {
                    try {
                        val offline = ShenavaOfflineRecognizer(context.applicationContext)
                        try { offline.recognize(session) } finally { offline.close() }
                    } catch (t: Throwable) {
                        Log.e(TAG, "Full-context Persian finalization failed; using streaming fallback", t)
                        ""
                    }
                } else ""
                java.util.Arrays.fill(session, 0f)

                val selected = FinalTranscriptSelector.choose(streamingFinal, fullContext)
                val cleaned = PersianText.clean(selected, final = true)
                val finalText = nameCorrector?.correct(cleaned) ?: cleaned
                if (finalText.isNotBlank() && finalText != lastEmitted) emitText(finalText)
            } catch (t: Throwable) {
                failed = true
                Log.e(TAG, "Two-pass speech recognition failed", t)
                emitError("تبدیل صدا به متن متوقف شد؛ دوباره امتحان کن")
            } finally {
                java.util.Arrays.fill(emptyArray<FloatArray>(), null)
                audioChunks.forEach { java.util.Arrays.fill(it, 0f) }
                audioChunks.clear()
                running.set(false)
                emitFinalizing(false)
                prepare()
                if (failed && recognizer == null) emitReady(false)
            }
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val readBuffer = ShortArray(SpeechTuning.READ_SAMPLES)
            try {
                while (running.get()) {
                    val n = record.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                    if (n < 0) {
                        if (!running.get()) break
                        throw IllegalStateException("AudioRecord.read failed with code $n")
                    }
                    if (n == 0) continue
                    pcmQueue.send(readBuffer.copyOf(n))
                    java.util.Arrays.fill(readBuffer, 0, n, 0.toShort())
                }
            } catch (t: Throwable) {
                if (running.get()) {
                    Log.e(TAG, "Continuous microphone capture failed", t)
                    emitError("تبدیل صدا به متن متوقف شد؛ دوباره امتحان کن")
                }
            } finally {
                running.set(false)
                java.util.Arrays.fill(readBuffer, 0.toShort())
                try { record.stop() } catch (_: Throwable) {}
                try { record.release() } catch (_: Throwable) {}
                if (audioRecord === record) audioRecord = null
                emitListening(false)
                emitFinalizing(true)
                pcmQueue.close()
            }
        }
    }

    fun stop() {
        running.set(false)
        try { audioRecord?.stop() } catch (_: Throwable) {}
    }

    private fun emitText(value: String) = mainHandler.post { onText(value) }
    private fun emitListening(value: Boolean) = mainHandler.post { onListening(value) }
    private fun emitFinalizing(value: Boolean) = mainHandler.post { onFinalizing(value) }
    private fun emitReady(value: Boolean) = mainHandler.post { onReady(value) }
    private fun emitError(value: String) = mainHandler.post { onError(value) }

    override fun close() {
        running.set(false)
        captureJob?.cancel()
        decodeJob?.cancel()
        try { audioRecord?.stop() } catch (_: Throwable) {}
        try { audioRecord?.release() } catch (_: Throwable) {}
        audioRecord = null
        try { recognizer?.close() } catch (_: Throwable) {}
        recognizer = null
        nameCorrector = null
        scope.cancel()
    }

    private companion object {
        const val TAG = "EmroozLiveSpeech"
    }
}
