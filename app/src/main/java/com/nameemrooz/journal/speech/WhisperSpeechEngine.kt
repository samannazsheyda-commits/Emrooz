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
import com.nameemrooz.whisper.WhisperContext
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Offline 16 kHz microphone -> bounded RAM utterances -> CPU Whisper. No audio files. */
class WhisperSpeechEngine(
    context: Context,
    languageTag: String,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onReady: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val onStableText: (String) -> Unit = {},
    private val decoderFactory: (() -> SpeechDecoder)? = null,
    private val voiceThreshold: Double = 0.0003,
) : SpeechEngine {
    init { require(voiceThreshold.isFinite() && voiceThreshold >= 0.0) }

    private val context = context.applicationContext
    private val language = if (languageTag.startsWith("en", ignoreCase = true)) "en" else "fa"
    private val main = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)
    private val running = AtomicBoolean(false)
    private val preparing = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val session = AtomicLong(0)
    private val decoderExecutor = Executors.newSingleThreadExecutor()
    private val captureExecutor = Executors.newSingleThreadExecutor()
    @Volatile private var decoder: SpeechDecoder? = null
    @Volatile private var recorder: AudioRecord? = null
    @Volatile private var pending: ArrayBlockingQueue<ShortArray>? = null

    @Synchronized override fun prepare() {
        if (closed.get()) return
        if (decoder != null) { post { onReady(true) }; return }
        if (!preparing.compareAndSet(false, true)) return
        post { onReady(false) }
        decoderExecutor.execute {
            try {
                if (!closed.get()) {
                    val loaded = decoderFactory?.invoke() ?: createBundledDecoder(context, language)
                    synchronized(this) {
                        if (closed.get()) loaded.close() else decoder = loaded
                    }
                    post { onReady(decoder != null) }
                }
            } catch (_: Exception) {
                post { onReady(false); onError("مدل فارسی آماده نشد") }
            } catch (_: LinkageError) {
                post { onReady(false); onError("مدل فارسی آماده نشد") }
            } finally { preparing.set(false) }
        }
    }

    @Synchronized override fun start() {
        if (closed.get() || running.get()) return
        val activeDecoder = decoder
        if (activeDecoder == null) { prepare(); post { onError("چند لحظه صبر کن تا نوشتن آماده شود") }; return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            post { onError("برای نوشتن با صدا اجازه‌ی میکروفون لازم است") }; return
        }
        val record = try {
            val minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0)
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum * 4, 32768)).also {
                if (it.state != AudioRecord.STATE_INITIALIZED) { it.release(); error("Microphone unavailable") }
            }
        } catch (_: Exception) { post { onError("میکروفون آماده نشد") }; return }
        val id = session.incrementAndGet()
        val queue = ArrayBlockingQueue<ShortArray>(4) // at most 32 s queued + current 8 s
        val captureFinished = CountDownLatch(1)
        val decodeFailed = AtomicBoolean(false)
        pending = queue
        recorder = record
        running.set(true)
        stopRequested.set(false)
        decoderExecutor.execute {
            val transcript = WhisperTranscript()
            var chunkId = 0L
            try {
                while (!closed.get()) {
                    val pcm = queue.take()
                    if (pcm.isEmpty()) break
                    val floats = FloatArray(pcm.size)
                    try {
                        for (i in pcm.indices) floats[i] = pcm[i] / 32768f
                        pcm.fill(0)
                        if (!closed.get()) {
                            val raw = activeDecoder.transcribe(floats)
                            val clean = if (language == "fa") PersianText.clean(raw, final = true) else raw.trim()
                            if (clean.isNotBlank()) {
                                val text = transcript.append(chunkId++, clean)
                                post(id) { onText(text); onStableText(text) }
                            }
                        }
                    } finally { pcm.fill(0); floats.fill(0f) }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                decodeFailed.set(true)
            } catch (_: LinkageError) {
                decodeFailed.set(true)
            } finally {
                stopRequested.set(true)
                runCatching { record.stop() }
                clear(queue)
                // A stopped callback always means capture has released the microphone.
                var interrupted = Thread.interrupted()
                while (captureFinished.count > 0) {
                    try { captureFinished.await() } catch (_: InterruptedException) { interrupted = true }
                }
                clear(queue)
                if (interrupted) Thread.currentThread().interrupt()
                if (decodeFailed.get()) post(id) { onError("تبدیل صدا به متن انجام نشد") }
                if (pending === queue) pending = null
                running.set(false)
                post(id) { onListening(false) }
            }
        }
        captureExecutor.execute {
            val buffer = ShortArray(320) // 20 ms VAD frames
            val segmenter = WhisperAudioSegmenter(threshold = voiceThreshold)
            fun submit(pcm: ShortArray) {
                if (closed.get() || decodeFailed.get()) { pcm.fill(0); return }
                if (!queue.offer(pcm)) {
                    // Stop immediately on backlog, then preserve every already captured sample.
                    stopRequested.set(true)
                    runCatching { record.stop() }
                    post(id) { onError("پردازش از ضبط عقب افتاد؛ ضبط متوقف شد و صدای دریافت‌شده در حال تبدیل است") }
                    try {
                        while (!queue.offer(pcm, 100, TimeUnit.MILLISECONDS)) {
                            if (closed.get() || decodeFailed.get()) { pcm.fill(0); return }
                        }
                    } catch (error: InterruptedException) { pcm.fill(0); throw error }
                }
            }
            try {
                if (!stopRequested.get() && !closed.get()) {
                    record.startRecording()
                    post(id) { onListening(true) }
                }
                while (!stopRequested.get() && !closed.get()) {
                    val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    if (count <= 0) {
                        if (stopRequested.get() || closed.get()) break
                        error("Microphone read failed")
                    }
                    segmenter.append(buffer, count, ::submit)
                    buffer.fill(0)
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            } catch (_: Exception) {
                if (!stopRequested.get()) post(id) { onError("دریافت صدا از میکروفون قطع شد") }
            } finally {
                buffer.fill(0)
                runCatching { record.stop() }
                runCatching { record.release() }
                if (recorder === record) recorder = null
                // Release before waiting on decoding/backpressure.
                captureFinished.countDown()
                try {
                    if (!closed.get() && !decodeFailed.get()) {
                        segmenter.finish(::submit)
                        while (!queue.offer(ShortArray(0), 100, TimeUnit.MILLISECONDS)) {
                            if (closed.get() || decodeFailed.get()) break
                        }
                    }
                } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
                finally { segmenter.reset() }
            }
        }
    }

    override fun stop() { stopRequested.set(true); runCatching { recorder?.stop() } }

    @Synchronized override fun close() {
        if (!closed.compareAndSet(false, true)) return
        session.incrementAndGet()
        stop()
        decoder?.cancel()
        pending?.let { clear(it); it.offer(ShortArray(0)) }
        captureExecutor.shutdown() // Run queued cleanup even if close raced capture startup.
        // Native close safely coexists with a running transcribe; PCM is cleared in its finally.
        decoder?.close()
        decoder = null
        decoderExecutor.shutdownNow()
    }

    private fun post(id: Long = session.get(), action: () -> Unit) {
        main.post { if (!closed.get() && session.get() == id) action() }
    }

    companion object {
        private const val SAMPLE_RATE = 16000
        private val MODEL_LOCK = Any()
        private data class BundledModel(val name: String, val bytes: Long, val sha256: String)
        private val PERSIAN_MODEL = BundledModel(
            "ggml-base-persian-q8_0.bin", 81_768_602L,
            "a8bcb0746be5c9741ba8d1c87c89cccb6fa1452e7878275071f5165a7c43f4a7",
        )
        private val ENGLISH_MODEL = BundledModel(
            "ggml-tiny.en-q5_1.bin", 32_166_155L,
            "c77c5766f1cef09b6b7d47f21b546cbddd4157886b3b5d6d4f709e91e66c7c2b",
        )

        internal fun createBundledDecoder(context: Context, languageTag: String): SpeechDecoder {
            val language = if (languageTag.startsWith("en", ignoreCase = true)) "en" else "fa"
            val native = WhisperContext.create(installModel(context, language).absolutePath)
            return object : SpeechDecoder {
                override fun transcribe(samples: FloatArray): String = native.transcribe(samples, language)
                override fun cancel() = native.cancel()
                override fun close() = native.close()
            }
        }

        private fun installModel(context: Context, language: String): File = synchronized(MODEL_LOCK) {
            val model = if (language == "en") ENGLISH_MODEL else PERSIAN_MODEL
            val asset = "models/whisper/${model.name}"
            val expected = context.assets.open("$asset.sha256").bufferedReader().use { it.readText().trim().substringBefore(' ') }
            check(expected.equals(model.sha256, ignoreCase = true)) { "Invalid model checksum" }
            val directory = File(context.noBackupFilesDir, "whisper").apply { check(isDirectory || mkdirs()) }
            val output = File(directory, "${model.name.removeSuffix(".bin")}-${expected.lowercase()}.bin")
            if (output.isFile && output.length() == model.bytes && sha256(output).equals(expected, ignoreCase = true)) return@synchronized output
            val partial = File(directory, output.name + ".part")
            try {
                context.assets.open(asset).use { input -> partial.outputStream().use { input.copyTo(it) } }
                check(partial.length() == model.bytes && sha256(partial).equals(expected, ignoreCase = true)) { "Model checksum mismatch" }
                check(partial.renameTo(output)) { "Unable to install model" }
                output
            } finally { partial.delete() }
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

    }

    private fun clear(queue: ArrayBlockingQueue<ShortArray>) {
        while (true) (queue.poll() ?: break).fill(0)
    }

}
