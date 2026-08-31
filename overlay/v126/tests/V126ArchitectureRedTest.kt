package com.nameemrooz.journal.speech

import com.nameemrooz.journal.util.PersianText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * RED characterization tests for the v1.2.5 failures reported on a physical device.
 * These intentionally run against the reconstructed v1.2.5 source before any v1.2.6
 * production overlay is applied.
 */
class V126ArchitectureRedTest {
    @Test
    fun productionCaptureMustNotStackThreeGainStages() {
        val source = File("src/main/java/com/nameemrooz/journal/speech/LiveSpeechEngine.kt").readText()
        assertTrue(source.contains("MediaRecorder.AudioSource.VOICE_RECOGNITION"))
        assertFalse("software AdaptiveSpeechGain must be removed", source.contains("AdaptiveSpeechGain()"))
        assertFalse("Android AGC must not be stacked", source.contains("AutomaticGainControl"))
        assertFalse("Android NoiseSuppressor must not be stacked", source.contains("NoiseSuppressor"))
    }

    @Test
    fun livePreviewMustNotHaveAn850msThreeSnapshotGate() {
        val source = File("src/main/java/com/nameemrooz/journal/speech/StableLiveTranscript.kt").readText()
        assertFalse("fixed 850 ms delay is too slow", source.contains("minStableMs: Long = 850"))
        assertFalse("three-snapshot gate is too slow", source.contains("snapshots.size < 3"))
    }

    @Test
    fun stopMustNotReplayWholeSessionPcm() {
        val source = File("src/main/java/com/nameemrooz/journal/speech/LiveSpeechEngine.kt").readText()
        assertFalse("whole-session PCM buffer makes Stop latency grow with duration", source.contains("val finalPcm = PcmMemoryBuffer"))
        assertFalse("whole-session toFloatArray replay must disappear", source.contains("finalPcm.toFloatArray()"))
    }

    @Test
    fun finalEditorMustPreserveIntentionalEmphasis() {
        val edited = PersianText.clean("امروز خیلی خیلی خوشحال بودم", final = true)
        assertTrue("intentional خیلی خیلی was collapsed: $edited", edited.contains("خیلی خیلی"))
    }
}
