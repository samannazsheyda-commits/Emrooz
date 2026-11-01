package com.nameemrooz.journal

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.nameemrooz.journal.speech.SystemSpeechEngine
import com.nameemrooz.journal.writing.LocalPunctuationModel
import com.nameemrooz.journal.writing.LexicalSafety
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class DeviceSpeechEngineTest {
    @Test fun missing_on_device_service_reports_unavailable_without_starting_capture() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val terminal = CountDownLatch(1)
        val ready = AtomicBoolean(false)
        val engine = SystemSpeechEngine(context, "fa-IR", {}, {}, {
            ready.set(it); terminal.countDown()
        }, {}, { terminal.countDown() }, onDeviceOnly = true)
        try {
            engine.prepare()
            assertTrue("Device speech preparation was unbounded", terminal.await(7, TimeUnit.SECONDS))
            if (Build.VERSION.SDK_INT < 31 || !SystemSpeechEngine.isOnDeviceAvailable(context)) assertFalse(ready.get())
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            assertTrue(audio.activeRecordingConfigurations.isEmpty())
        } finally { engine.close() }
    }

    @Test fun closed_engine_ignores_queued_preparation_callbacks() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val callback = AtomicBoolean(false)
        val engine = SystemSpeechEngine(context, "fa-IR", {}, {callback.set(true)}, {callback.set(true)}, {callback.set(true)}, {callback.set(true)}, onDeviceOnly = true)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync { engine.prepare(); engine.close() }
        instrumentation.waitForIdleSync()
        assertFalse(callback.get())
    }

    @Test fun bundled_punctuation_runtime_loads_and_preserves_original_words() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        LocalPunctuationModel(context).use { model ->
            assertTrue("Punctuation runtime did not load", model.prepare())
            val text = "امروز هوا خوب بود من با میثم قدم زدم و ساعت ۱۲ برگشتم"
            assertTrue(LexicalSafety.isPunctuationOnlyChange(text, model.restore(text)))
        }
    }
}
