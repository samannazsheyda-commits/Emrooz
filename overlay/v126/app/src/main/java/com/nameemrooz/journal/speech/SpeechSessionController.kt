package com.nameemrooz.journal.speech

import android.content.Context
import android.media.AudioRecord
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.nameemrooz.journal.util.PersianEditorV2
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Segmented v1.2.6 speech pipeline.
 *
 * Capture retains only the active segment. Completed segments are finalized in
 * order while recording continues, so pressing Stop never replays historical
 * session audio.
 */
class SpeechSessionController(
    private val context: Context,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onFinalizing: (Boolean) -> Unit,
    private val onReady: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
) : AutoCloseable {
    private data class SegmentWork(
        val id: Long,
        val pcm: ShortArray,
        val stableLive: String,
    )

    private data class PendingPreview(
        val id: Long,
        val text: String,
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val preparing = AtomicBoolean(false)
    private val sessionActive = AtomicBoolean(false)
    private val captureRunning = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private val stateLock = Any()
    private val merger = SegmentTranscriptMerger()

    @Volatile private var liveEngine: LiveAsrEngine? = null
    @Volatile private var captureSource: AudioCaptureSource? = null
    private var captureJob: Job? = null
    private var processingJob: Job? = null
    private var finalJob: Job? = null

    private var nextSegmentId = 1L
    private var finalizedText = ""
    private val pendingPreviews = ArrayList<PendingPreview>()
    private var currentStableLive = ""

    fun prepare() {
        if (liveEngine != null) {
            emitReady(true)
            return
        }
        if (!preparing.compareAndSet(false, true)) return
        emitReady(false)
        scope.launch(Dispatchers.Default) {
            try {
                val loaded = LiveAsrEngine(appContext)
                if (liveEngine == null) liveEngine = loaded else loaded.close()
                emitReady(true)
            } catch (t: Throwable) {
                Log.e(TAG, "Unable to prepare live Persian ASR", t)
                emitReady(false)
                emitError("مدل فارسی آماده نشد")
            } finally {
                preparing.set(false)
            }
        }
    }

    fun start() {
        if (!sessionActive.compareAndSet(false, true)) return
        val live = liveEngine
        if (live == null) {
            sessionActive.set(false)
            prepare()
            emitError("چند لحظه صبر کن تا نوشتن آماده شود")
            return
        }

        resetSessionTextState()
        stopRequested.set(false)
        captureRunning.set(false)
        emitFinalizing(false)

        val source = AudioCaptureSource(appContext)
        val record: AudioRecord = try {
            source.openAndStart()
        } catch (t: Throwable) {
            source.close()
            sessionActive.set(false)
            Log.e(TAG, "Unable to start clean microphone capture", t)
            emitError("میکروفون آماده نشد")
            return
        }

        try {
            live.resetSegment()
        } catch (t: Throwable) {
            source.close()
            sessionActive.set(false)
            emitError("نوشتن با صدا آماده نشد؛ دوباره امتحان کن")
            return
        }

        val audioQueue = Channel<ShortArray>(capacity = AUDIO_QUEUE_CAPACITY)
        val finalQueue = Channel<SegmentWork>(capacity = FINAL_QUEUE_CAPACITY)
        val segmentBuffer = SegmentPcmBuffer(
            sampleRate = SpeechTuning.SAMPLE_RATE,
            hardLimitSeconds = HARD_SEGMENT_SECONDS,
            overlapMs = FORCED_SPLIT_OVERLAP_MS,
        )
        val stabilizer = LiveTranscriptStabilizer()
        captureSource = source
        captureRunning.set(true)
        emitListening(true)

        finalJob = scope.launch(Dispatchers.Default) {
            var finalEngine: FinalAsrEngine? = null
            try {
                for (work in finalQueue) {
                    val recognizer = finalEngine ?: FinalAsrEngine(appContext).also { finalEngine = it }
                    val rawFinal = try {
                        recognizer.recognize(work.pcm)
                    } catch (t: Throwable) {
                        work.pcm.fill(0)
                        Log.w(TAG, "Final Persian segment decode failed", t)
                        ""
                    }
                    val chosen = merger.chooseSegment(work.stableLive, rawFinal)
                    val edited = PersianEditorV2.editFinal(chosen, sentenceBoundary = false)
                    val visible = synchronized(stateLock) {
                        finalizedText = merger.merge(finalizedText, edited)
                        val index = pendingPreviews.indexOfFirst { it.id == work.id }
                        if (index >= 0) pendingPreviews.removeAt(index)
                        renderLocked()
                    }
                    if (visible.isNotBlank()) emitText(visible)
                }

                val completed = synchronized(stateLock) {
                    currentStableLive = ""
                    pendingPreviews.clear()
                    finalizedText = PersianEditorV2.editFinal(finalizedText, sentenceBoundary = true)
                    finalizedText
                }
                if (completed.isNotBlank()) emitText(completed)
            } catch (t: Throwable) {
                Log.e(TAG, "Segment finalization pipeline failed", t)
                emitError("بخشی از تبدیل صدا کامل نشد؛ متن شنیده‌شده حفظ شد")
                val fallback = synchronized(stateLock) {
                    var value = finalizedText
                    pendingPreviews.forEach { value = merger.merge(value, it.text) }
                    value = merger.merge(value, currentStableLive)
                    PersianEditorV2.editFinal(value, sentenceBoundary = true)
                }
                if (fallback.isNotBlank()) emitText(fallback)
            } finally {
                try { finalEngine?.close() } catch (_: Throwable) {}
                emitFinalizing(false)
                sessionActive.set(false)
            }
        }

        processingJob = scope.launch(Dispatchers.Default) {
            var latestPartial = ""
            try {
                for (pcm in audioQueue) {
                    try {
                        segmentBuffer.append(pcm, pcm.size)
                        val update = live.accept(pcm)
                        latestPartial = update.partial

                        val stable = stabilizer.offer(update.partial, SystemClock.elapsedRealtime())
                        if (!stable.isNullOrBlank()) {
                            val visible = synchronized(stateLock) {
                                currentStableLive = stable
                                renderLocked()
                            }
                            if (visible.isNotBlank()) emitText(visible)
                        }

                        val forced = segmentBuffer.shouldForceClose()
                        if (update.endpoint || forced) {
                            val liveAtBoundary = if (update.endpoint) {
                                val flushed = try { live.finishSegment() } catch (_: Throwable) { update.partial }
                                stabilizer.bestStableOr(if (flushed.isNotBlank()) flushed else update.partial)
                            } else {
                                stabilizer.bestStableOr(update.partial)
                            }
                            val segment = if (forced) segmentBuffer.forceClose() else segmentBuffer.closeWithoutOverlap()
                            enqueueSegment(finalQueue, segment, liveAtBoundary)
                            live.resetSegment()
                            stabilizer.reset()
                            latestPartial = ""
                        }
                    } finally {
                        pcm.fill(0)
                    }
                }

                val flushed = try {
                    if (segmentBuffer.sampleCount() > 0) live.finishSegment() else latestPartial
                } catch (_: Throwable) {
                    latestPartial
                }
                if (segmentBuffer.hasSpeechSizedTail(MIN_TAIL_SAMPLES)) {
                    val tail = segmentBuffer.closeWithoutOverlap()
                    enqueueSegment(finalQueue, tail, stabilizer.bestStableOr(flushed))
                } else {
                    segmentBuffer.clear()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Live segmented Persian processing failed", t)
                emitError("تبدیل صدا به متن متوقف شد؛ دوباره امتحان کن")
                segmentBuffer.clear()
            } finally {
                try { live.resetSegment() } catch (_: Throwable) {}
                finalQueue.close()
            }
        }

        captureJob = scope.launch(Dispatchers.IO) {
            val readBuffer = ShortArray(SpeechTuning.READ_SAMPLES)
            try {
                while (captureRunning.get()) {
                    val n = record.read(readBuffer, 0, readBuffer.size, AudioRecord.READ_BLOCKING)
                    if (n < 0) {
                        if (!captureRunning.get()) break
                        throw IllegalStateException("AudioRecord.read failed with code $n")
                    }
                    if (n == 0) continue
                    audioQueue.send(readBuffer.copyOf(n))
                    java.util.Arrays.fill(readBuffer, 0, n, 0.toShort())
                }
            } catch (t: Throwable) {
                if (captureRunning.get()) {
                    Log.e(TAG, "Continuous microphone capture failed", t)
                    emitError("میکروفون متوقف شد؛ دوباره امتحان کن")
                }
            } finally {
                captureRunning.set(false)
                readBuffer.fill(0)
                source.close()
                if (captureSource === source) captureSource = null
                emitListening(false)
                if (stopRequested.get()) emitFinalizing(true)
                audioQueue.close()
            }
        }
    }

    private suspend fun enqueueSegment(
        queue: Channel<SegmentWork>,
        segment: ShortArray,
        stableLive: String,
    ) {
        if (segment.isEmpty()) return
        val preview = PersianEditorV2.cleanLive(stableLive)
        val id = synchronized(stateLock) {
            val value = nextSegmentId++
            pendingPreviews.add(PendingPreview(value, preview))
            currentStableLive = ""
            value
        }
        queue.send(SegmentWork(id = id, pcm = segment, stableLive = preview))
    }

    private fun renderLocked(): String {
        var value = finalizedText
        pendingPreviews.forEach { pending -> value = merger.merge(value, pending.text) }
        value = merger.merge(value, currentStableLive)
        return value.trim()
    }

    private fun resetSessionTextState() {
        synchronized(stateLock) {
            nextSegmentId = 1L
            finalizedText = ""
            pendingPreviews.clear()
            currentStableLive = ""
        }
    }

    fun stop() {
        if (!sessionActive.get()) return
        stopRequested.set(true)
        captureRunning.set(false)
        try { captureSource?.stop() } catch (_: Throwable) {}
    }

    private fun emitText(value: String) = mainHandler.post { onText(value) }
    private fun emitListening(value: Boolean) = mainHandler.post { onListening(value) }
    private fun emitFinalizing(value: Boolean) = mainHandler.post { onFinalizing(value) }
    private fun emitReady(value: Boolean) = mainHandler.post { onReady(value) }
    private fun emitError(value: String) = mainHandler.post { onError(value) }

    override fun close() {
        captureRunning.set(false)
        stopRequested.set(false)
        try { captureSource?.close() } catch (_: Throwable) {}
        captureSource = null
        captureJob?.cancel()
        processingJob?.cancel()
        finalJob?.cancel()
        try { liveEngine?.close() } catch (_: Throwable) {}
        liveEngine = null
        sessionActive.set(false)
        scope.cancel()
    }

    private companion object {
        const val TAG = "EmroozSpeechV126"
        const val AUDIO_QUEUE_CAPACITY = 4
        const val FINAL_QUEUE_CAPACITY = 2
        const val HARD_SEGMENT_SECONDS = 12
        const val FORCED_SPLIT_OVERLAP_MS = 250
        const val MIN_TAIL_SAMPLES = 1_600
    }
}
