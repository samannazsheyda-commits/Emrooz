package com.nameemrooz.journal.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fully offline Persian text-to-speech. Audio exists only in AudioTrack/RAM;
 * no WAV/MP3/M4A file is ever written.
 */
class OfflinePersianTts(private val context: Context) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var engine: OfflineTts? = null
    @Volatile private var track: AudioTrack? = null
    private val root = File(context.filesDir, "tts/fa_IR-amir-medium")

    init { scope.launch { initialize() } }

    private fun initialize() {
        root.mkdirs()
        copyAssets("tts/vits-piper-fa_IR-amir-medium", root)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = "tts/vits-piper-fa_IR-amir-medium/fa_IR-amir-medium.onnx",
                    tokens = "tts/vits-piper-fa_IR-amir-medium/tokens.txt",
                    dataDir = File(root, "espeak-ng-data").absolutePath,
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu",
            ),
            maxNumSentences = 1,
        )
        engine = OfflineTts(context.assets, config)
    }

    fun speak(text: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        scope.launch {
            val tts = engine ?: return@launch
            stop()
            stopRequested.set(false)
            val audio = runCatching { tts.generate(value, sid = 0, speed = 1.0f) }.getOrNull() ?: return@launch
            if (stopRequested.get() || audio.samples.isEmpty()) {
                audio.samples.fill(0f)
                return@launch
            }
            val min = AudioTrack.getMinBufferSize(
                audio.sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
            ).coerceAtLeast(2048)
            val attrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(audio.sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()
            val local = AudioTrack(
                attrs,
                format,
                min,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE,
            )
            track = local
            local.play()
            local.write(audio.samples, 0, audio.samples.size, AudioTrack.WRITE_BLOCKING)
            if (local.state == AudioTrack.STATE_INITIALIZED) {
                runCatching { local.stop() }
                runCatching { local.release() }
            }
            track = null
            audio.samples.fill(0f)
        }
    }

    fun stop() {
        stopRequested.set(true)
        track?.let {
            runCatching { it.pause() }
            runCatching { it.flush() }
            runCatching { it.release() }
        }
        track = null
    }

    private fun copyAssets(path: String, target: File) {
        val names = context.assets.list(path) ?: emptyArray()
        if (names.isEmpty()) return
        for (name in names) {
            val source = path + "/" + name
            val out = File(target, name)
            if ((context.assets.list(source) ?: emptyArray()).isNotEmpty()) {
                out.mkdirs()
                copyAssets(source, out)
            } else if (!out.exists() || out.length() == 0L) {
                context.assets.open(source).use { input ->
                    out.outputStream().buffered().use { output -> input.copyTo(output, 1024 * 1024) }
                }
            }
        }
    }

    override fun close() {
        stop()
        engine?.release()
        engine = null
        scope.cancel()
    }
}
