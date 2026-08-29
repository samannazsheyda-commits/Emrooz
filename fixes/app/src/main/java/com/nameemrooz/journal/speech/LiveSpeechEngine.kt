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
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * Privacy invariant: microphone samples exist only in RAM.
 * There is intentionally no code path that writes microphone data to disk.
 */
class LiveSpeechEngine(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onReady: (Boolean) -> Unit,
    private val onError: (String) -> Unit
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val preparing = AtomicBoolean(false)
    private var recognizer: ShenavaRecognizer? = null
    private var audioRecord: AudioRecord? = null
    private var job: Job? = null
    private val nameCorrector by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        PersianNameCorrector(context.applicationContext)
    }

    private val sampleRate = 16000

    /** Warm the bundled streaming model and offline name lexicon before the user starts speaking. */
    fun prepare() {
        if (recognizer != null) {
            emitReady(true)
            return
        }
        if (!preparing.compareAndSet(false, true)) return
        emitReady(false)
        scope.launch {
            try {
                val loaded = ShenavaRecognizer(context)
                if (recognizer == null) recognizer = loaded else loaded.close()
                nameCorrector.warmUp()
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

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
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

        audioRecord = record
        running.set(true)
        emitListening(true)

        job = scope.launch {
            val readBuffer = ShortArray(4096)
            var lastEmitted = ""
            var failed = false

            try {
                while (running.get()) {
                    val n = record.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                    if (n < 0) {
                        throw IllegalStateException("AudioRecord.read failed with code $n")
                    }
                    if (n == 0) continue

                    val samples = FloatArray(n)
                    for (i in 0 until n) {
                        samples[i] = readBuffer[i] / 32768.0f
                        readBuffer[i] = 0
                    }

                    // Keep live text stable and fast. Stronger editing happens only after stop.
                    val partial = PersianText.clean(activeRecognizer.accept(samples))
                    java.util.Arrays.fill(samples, 0f)
                    if (partial.isNotBlank() && partial != lastEmitted) {
                        lastEmitted = partial
                        emitText(partial)
                    }
                }

                val cleaned = PersianText.clean(activeRecognizer.finish(), final = true)
                val finalText = nameCorrector.correct(cleaned)
                if (finalText.isNotBlank() && finalText != lastEmitted) {
                    emitText(finalText)
                }
            } catch (t: Throwable) {
                failed = true
                Log.e(TAG, "Streaming speech recognition failed", t)
                emitError("تبدیل صدا به متن متوقف شد؛ دوباره امتحان کن")
            } finally {
                java.util.Arrays.fill(readBuffer, 0.toShort())
                try { record.stop() } catch (_: Throwable) {}
                record.release()
                audioRecord = null
                running.set(false)
                emitListening(false)
                if (failed) emitReady(recognizer != null)
            }
        }
    }

    fun stop() {
        running.set(false)
    }

    private fun emitText(value: String) = mainHandler.post { onText(value) }
    private fun emitListening(value: Boolean) = mainHandler.post { onListening(value) }
    private fun emitReady(value: Boolean) = mainHandler.post { onReady(value) }
    private fun emitError(value: String) = mainHandler.post { onError(value) }

    override fun close() {
        running.set(false)
        job?.cancel()
        try { audioRecord?.stop() } catch (_: Throwable) {}
        try { audioRecord?.release() } catch (_: Throwable) {}
        audioRecord = null
        try { recognizer?.close() } catch (_: Throwable) {}
        recognizer = null
        scope.cancel()
    }

    private companion object {
        const val TAG = "EmroozLiveSpeech"
    }
}
