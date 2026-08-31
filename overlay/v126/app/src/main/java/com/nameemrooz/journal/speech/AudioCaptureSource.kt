package com.nameemrooz.journal.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlin.math.max

/**
 * One clean microphone path for Persian ASR.
 * VOICE_RECOGNITION already asks the Android audio stack for recognition-oriented
 * capture, so v1.2.6 deliberately does not stack AGC/NS/software gain on top.
 */
class AudioCaptureSource(
    private val context: Context,
    private val sampleRate: Int = SpeechTuning.SAMPLE_RATE,
) : AutoCloseable {
    private var record: AudioRecord? = null

    fun openAndStart(): AudioRecord {
        check(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        ) { "RECORD_AUDIO permission missing" }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minBuffer > 0) { "AudioRecord min buffer unavailable: $minBuffer" }

        val created = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            max(minBuffer * 4, 32_768),
        )
        check(created.state == AudioRecord.STATE_INITIALIZED) {
            created.release()
            "AudioRecord did not initialize"
        }
        created.startRecording()
        check(created.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
            created.release()
            "AudioRecord did not enter recording state"
        }
        record = created
        return created
    }

    fun stop() {
        val active = record ?: return
        try { active.stop() } catch (_: Throwable) {}
    }

    override fun close() {
        val active = record
        record = null
        if (active != null) {
            try { active.stop() } catch (_: Throwable) {}
            try { active.release() } catch (_: Throwable) {}
        }
    }
}
