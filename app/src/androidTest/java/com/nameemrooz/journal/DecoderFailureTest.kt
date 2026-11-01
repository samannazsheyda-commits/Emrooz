package com.nameemrooz.journal

import android.Manifest
import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.nameemrooz.journal.speech.WhisperSpeechEngine
import com.nameemrooz.journal.speech.SpeechDecoder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

class DecoderFailureTest {
    @get:Rule val microphone = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test fun bundled_offline_model_decodes_silence_without_inventing_words() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WhisperSpeechEngine.createBundledDecoder(context, "fa-IR").use { recognizer ->
            assertEquals("", recognizer.transcribe(FloatArray(16_000)))
        }
    }

    @Test fun bundled_offline_model_runs_nonzero_pcm_through_native_decode() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WhisperSpeechEngine.createBundledDecoder(context, "fa-IR").use { recognizer ->
            val samples = FloatArray(16_000 * 3) { index ->
                (0.1 * sin(2 * PI * 440 * index / 16_000)).toFloat()
            }
            try {
                // Synthetic tone has no expected transcript; this exercises whisper_full.
                recognizer.transcribe(samples)
            } finally { samples.fill(0f) }
        }
    }

    @Test fun failing_decoder_releases_microphone_before_reporting_stopped() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ready = CountDownLatch(1)
        val listening = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val started = AtomicBoolean(false)
        val decoderCalled = AtomicBoolean(false)
        val engine = WhisperSpeechEngine(context,
            languageTag = "fa-IR",
            onText = {},
            onListening = { if (it) { started.set(true); listening.countDown() } else if (started.get()) stopped.countDown() },
            onReady = { if(it) ready.countDown() },
            onError = {},
            voiceThreshold = 0.0, // Make captured emulator silence exercise the decoder failure path.
            decoderFactory = {
                object : SpeechDecoder {
                    override fun transcribe(samples: FloatArray): String {
                        decoderCalled.set(true)
                        error("Injected decoder failure")
                    }
                    override fun close() { }
                }
            },
        )
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            instrumentation.runOnMainSync { engine.prepare() }
            assertTrue(ready.await(10, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { engine.start() }
            assertTrue("Microphone never started", listening.await(10, TimeUnit.SECONDS))
            // A full 8 s captured chunk reaches the forced failure without calling stop().
            assertTrue("Decoder failure did not stop capture", stopped.await(20, TimeUnit.SECONDS))
            assertTrue(decoderCalled.get())
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            assertTrue("Microphone still active after stopped callback", manager.activeRecordingConfigurations.isEmpty())
        } finally { instrumentation.runOnMainSync { engine.close() } }
    }
}
