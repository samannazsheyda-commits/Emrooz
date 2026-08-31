package com.nameemrooz.journal.speech

import com.nameemrooz.journal.util.PersianText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Characterization tests for the three v1.2.5 architecture failures reported on
 * a physical device. They stay valid after v1.2.6 splits microphone capture out
 * of LiveSpeechEngine into AudioCaptureSource.
 */
class V126ArchitectureRedTest {
    @Test
    fun productionCaptureMustNotStackThreeGainStages() {
        val splitCapture = File("src/main/java/com/nameemrooz/journal/speech/AudioCaptureSource.kt")
        val source = if (splitCapture.exists()) {
            splitCapture.readText()
        } else {
            File("src/main/java/com/nameemrooz/journal/speech/LiveSpeechEngine.kt").readText()
        }
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
