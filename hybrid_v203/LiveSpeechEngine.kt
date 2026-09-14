package com.nameemrooz.journal.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.nameemrooz.journal.util.PersianText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Continuous microphone capture -> lightweight VAD/windowing -> Shenava RNNT decode.
 *
 * Privacy invariant: microphone PCM exists only in RAM. No microphone samples are
 * written to a file, database, preference, log, or network API.
 */
class LiveSpeechEngine(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onReady: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val onStableText: (String) -> Unit = {},
) : SpeechEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val preparing = AtomicBoolean(false)

    @Volatile private var recognizer: ShenavaRnntRecognizer? = null
    @Volatile private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var segmentJob: Job? = null
    private var decodeJob: Job? = null

    private val transcript = StableTranscriptMerger()
    private val vocabulary = PersonalVocabulary(
        mapOf(
            "واتساپ" to "واتس‌اپ",
            "واتس اپ" to "واتس‌اپ",
        )
    )

    private val sampleRate = ShenavaRnntRecognizer.SAMPLE_RATE
    private val windowSamples = (sampleRate * 1.60f).toInt()
    private val overlapSamples = (sampleRate * 0.50f).toInt()
    private val pauseSilenceSamples = (sampleRate * 0.62f).toInt()
    private val minPauseSegmentSamples = (sampleRate * 0.90f).toInt()
    private val minTailSamples = (sampleRate * 0.32f).toInt()
    private val silenceRmsThreshold = 0.0115

    private data class DecodeRequest(val samples: ShortArray, val finalUtterance: Boolean)

    override fun prepare() {
        if (recognizer != null) {
            emitReady(true)
            return
        }
        if (!preparing.compareAndSet(false, true)) return
        emitReady(false)
        scope.launch(Dispatchers.Default) {
            try {
                val loaded = ShenavaRnntRecognizer(context.applicationContext)
                synchronized(this@LiveSpeechEngine) {
                    if (recognizer == null) recognizer = loaded else loaded.close()
                }
                emitReady(true)
            } catch (_: Throwable) {
                emitReady(false)
                emitError("مدل فارسی آماده نشد")
            } finally {
                preparing.set(false)
            }
        }
    }

    override fun start() {
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

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            emitError("میکروفون آماده نشد")
            return
        }

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuffer * 4, 32768),
            )
        } catch (_: Throwable) {
            emitError("میکروفون آماده نشد")
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            emitError("میکروفون آماده نشد")
            return
        }

        val captureChannel = Channel<ShortArray>(capacity = 32)
        val decodeChannel = Channel<DecodeRequest>(Channel.UNLIMITED)
        val buffer = AudioWindowBuffer(windowSamples, overlapSamples)

        transcript.reset()
        stopRequested.set(false)
        running.set(true)
        audioRecord = record

        decodeJob = scope.launch(Dispatchers.Default) {
            try {
                for (request in decodeChannel) {
                    val floatSamples = FloatArray(request.samples.size)
                    for (i in request.samples.indices) floatSamples[i] = request.samples[i] / 32768.0f
                    Arrays.fill(request.samples, 0.toShort())
                    try {
                        val raw = activeRecognizer.transcribe(floatSamples)
                        val clean = PersianText.clean(vocabulary.apply(raw), final = request.finalUtterance)
                        if (clean.isNotBlank()) {
                            val merged = transcript.commit(clean)
                            emitText(merged)
                            if (request.finalUtterance) emitStableText(merged)
                        }
                    } finally {
                        Arrays.fill(floatSamples, 0f)
                    }
                }
                val finalText = PersianText.clean(transcript.current(), final = true)
                if (finalText.isNotBlank()) emitText(finalText)
            } catch (_: Throwable) {
                if (transcript.current().isBlank()) emitError("تبدیل صدا به متن انجام نشد")
            } finally {
                running.set(false)
            }
        }

        segmentJob = scope.launch(Dispatchers.Default) {
            var silenceSamples = 0
            var heardSpeech = false
            try {
                for (chunk in captureChannel) {
                    buffer.append(chunk)
                    var squareSum = 0.0
                    for (sample in chunk) {
                        val v = sample.toDouble() / 32768.0
                        squareSum += v * v
                    }
                    val rms = if (chunk.isNotEmpty()) sqrt(squareSum / chunk.size) else 0.0
                    if (rms < silenceRmsThreshold) {
                        if (heardSpeech) silenceSamples += chunk.size
                    } else {
                        heardSpeech = true
                        silenceSamples = 0
                    }
                    Arrays.fill(chunk, 0.toShort())

                    val pauseBoundary = heardSpeech &&
                        silenceSamples >= pauseSilenceSamples &&
                        buffer.availableSamples >= minPauseSegmentSamples

                    if (pauseBoundary) {
                        val phrase = buffer.takeAll()
                        if (phrase.size >= minTailSamples) decodeChannel.send(DecodeRequest(phrase, true))
                        else Arrays.fill(phrase, 0.toShort())
                        silenceSamples = 0
                        heardSpeech = false
                    } else {
                        while (true) {
                            val window = buffer.pollWindow() ?: break
                            decodeChannel.send(DecodeRequest(window, false))
                        }
                    }
                }
            } finally {
                val tail = buffer.takeAll()
                if (tail.size >= minTailSamples) decodeChannel.send(DecodeRequest(tail, true))
                else Arrays.fill(tail, 0.toShort())
                decodeChannel.close()
            }
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val readBuffer = ShortArray(2048)
            try {
                record.startRecording()
                emitListening(true)
                while (!stopRequested.get()) {
                    val n = record.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                    if (n <= 0) {
                        if (stopRequested.get()) break
                        continue
                    }
                    captureChannel.send(readBuffer.copyOf(n))
                    Arrays.fill(readBuffer, 0, n, 0.toShort())
                }
            } catch (_: Throwable) {
                if (!stopRequested.get()) emitError("دریافت صدا از میکروفون قطع شد")
            } finally {
                Arrays.fill(readBuffer, 0.toShort())
                runCatching { record.stop() }
                record.release()
                if (audioRecord === record) audioRecord = null
                captureChannel.close()
                emitListening(false)
            }
        }
    }

    override fun stop() {
        stopRequested.set(true)
        runCatching { audioRecord?.stop() }
    }

    private fun emitText(value: String) = mainHandler.post { onText(value) }
    private fun emitStableText(value: String) = mainHandler.post { onStableText(value) }
    private fun emitListening(value: Boolean) = mainHandler.post { onListening(value) }
    private fun emitReady(value: Boolean) = mainHandler.post { onReady(value) }
    private fun emitError(value: String) = mainHandler.post { onError(value) }

    override fun close() {
        stopRequested.set(true)
        runCatching { audioRecord?.stop() }
        captureJob?.cancel()
        segmentJob?.cancel()
        decodeJob?.cancel()
        runCatching { audioRecord?.release() }
        audioRecord = null
        runCatching { recognizer?.close() }
        recognizer = null
        scope.cancel()
    }
}
