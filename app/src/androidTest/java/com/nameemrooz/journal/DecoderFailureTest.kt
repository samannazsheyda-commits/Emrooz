package com.nameemrooz.journal

import android.Manifest
import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.nameemrooz.journal.speech.LiveSpeechEngine
import com.nameemrooz.journal.speech.SpeechDecoder
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DecoderFailureTest {
    @get:Rule val microphone = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    @Test fun bundled_offline_model_decodes_silence_without_inventing_words() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        com.nameemrooz.journal.speech.ShenavaRnntRecognizer(context).use { recognizer ->
            assertEquals("", recognizer.transcribe(FloatArray(16_000)))
        }
    }

    @Test fun failing_decoder_releases_microphone_before_reporting_stopped() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ready = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        val started = AtomicBoolean(false)
        val decoderCalled = AtomicBoolean(false)
        val engine = LiveSpeechEngine(context,
            onText = {},
            onListening = { if (it) started.set(true) else if (started.get()) stopped.countDown() },
            onReady = { if(it) ready.countDown() },
            onError = {},
            recognizerFactory = {
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
            assertTrue(stopped.await(15, TimeUnit.SECONDS))
            assertTrue(decoderCalled.get())
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            assertTrue("Microphone still active after stopped callback", manager.activeRecordingConfigurations.isEmpty())
        } finally { instrumentation.runOnMainSync { engine.close() } }
    }
}
