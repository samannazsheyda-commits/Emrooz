package com.nameemrooz.journal.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.nameemrooz.journal.writing.GeminiNanoWritingEnhancer
import com.nameemrooz.journal.writing.LocalPunctuationModel
import com.nameemrooz.journal.writing.LocalWritingEnhancer
import com.nameemrooz.journal.writing.MlKitNanoPromptClient
import com.nameemrooz.journal.writing.NoOpPunctuationRestorer
import com.nameemrooz.journal.writing.StableDisplayComposer
import com.nameemrooz.journal.writing.TextEditor
import com.nameemrooz.journal.writing.WritingEnhancementCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.nameemrooz.journal.writing.TranscriptEdits
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Presents one stable speech session to the UI while allowing the optional device
 * recognizer to fall back to Shenava without losing already accepted text.
 *
 * Live deterministic cleanup happens on every update. Neural editing/punctuation runs only
 * for stable pause/final segments and never blocks microphone capture.
 */
class HybridSpeechController(
    private val context: Context,
    private val configuredMode: SpeechMode,
    private val languageTag: String,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val onReady: (Boolean) -> Unit,
    private val onError: (String) -> Unit,
    private val enhancedEditingEnabled: Boolean = false,
) : SpeechEngine {
    private val appContext = context.applicationContext
    private val closed = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val edits = TranscriptEdits()
    private val editingMutex = Mutex()
    private var editingJob: Job? = null
    private var engineEpoch = 0L
    private val punctuationModel = if (languageTag.startsWith("fa", ignoreCase = true)) {
        LocalPunctuationModel(appContext)
    } else null
    private val editor = TextEditor(punctuationModel ?: NoOpPunctuationRestorer)
    private val localEnhancer = LocalWritingEnhancer(editor)
    private val nanoEnhancer: GeminiNanoWritingEnhancer? = if (enhancedEditingEnabled) {
        GeminiNanoWritingEnhancer(clientFactory = { MlKitNanoPromptClient() })
    } else null
    private val coordinator = WritingEnhancementCoordinator(
        local = localEnhancer,
        nano = nanoEnhancer,
        nanoEnabled = enhancedEditingEnabled,
    )

    private var activeMode: SpeechMode = configuredMode
    private var activeEngine: SpeechEngine? = null
    private var carryPrefix = ""
    @Volatile private var rawCurrent = ""
    @Volatile private var normalizedCurrent = ""
    private var sessionRequested = false
    private var pendingFallbackStart = false
    private var fallbackUsed = false

    override fun prepare() {
        if (closed.get()) return
        if (activeEngine == null) activeEngine = createEngine(activeMode)
        activeEngine?.prepare()
    }

    override fun start() {
        if (closed.get()) return
        sessionRequested = true
        pendingFallbackStart = false
        fallbackUsed = false
        carryPrefix = ""
        rawCurrent = ""
        normalizedCurrent = ""
        editingJob?.cancel()
        edits.reset()
        if (activeEngine == null) activeEngine = createEngine(activeMode)
        activeEngine?.start()
    }

    override fun stop() {
        sessionRequested = false
        pendingFallbackStart = false
        activeEngine?.stop()
    }

    /** Unlike stop(), discard never flushes a final transcript into the next draft. */
    fun discard() {
        if (closed.get()) return
        engineEpoch++
        sessionRequested = false
        pendingFallbackStart = false
        fallbackUsed = false
        editingJob?.cancel()
        edits.reset()
        activeEngine?.close()
        activeEngine = null
        carryPrefix = ""
        rawCurrent = ""
        normalizedCurrent = ""
        activeMode = configuredMode
        onListening(false)
        onReady(false)
        prepare()
    }

    private fun createEngine(mode: SpeechMode): SpeechEngine {
        val epoch = ++engineEpoch
        val textCallback: (String) -> Unit = text@{ candidate ->
            if (closed.get() || epoch != engineEpoch) return@text
            val combinedRaw = TranscriptContinuity.combine(carryPrefix, candidate)
            rawCurrent = combinedRaw
            val normalized = editor.live(combinedRaw, languageTag)
            normalizedCurrent = normalized
            onText(edits.partial(normalized))
        }
        val stableCallback: (String) -> Unit = stable@{ candidate ->
            if (closed.get() || epoch != engineEpoch) return@stable
            val combinedRaw = TranscriptContinuity.combine(carryPrefix, candidate)
            rawCurrent = combinedRaw
            val normalizedSnapshot = editor.live(combinedRaw, languageTag)
            normalizedCurrent = normalizedSnapshot
            val ticket = edits.ticket(normalizedSnapshot)
            editingJob?.cancel()
            editingJob = scope.launch {
                val edited = editingMutex.withLock { coordinator.stable(normalizedSnapshot, languageTag) }
                mainHandler.post {
                    if (!closed.get() && epoch == engineEpoch) {
                        edits.apply(ticket, edited)?.let(onText)
                    }
                }
            }
        }
        val listeningCallback: (Boolean) -> Unit = listening@{ listening ->
            if (closed.get() || epoch != engineEpoch) return@listening
            if (listening) onListening(true)
            else scope.launch {
                editingJob?.join()
                mainHandler.post { if (!closed.get() && epoch == engineEpoch) onListening(false) }
            }
        }
        val readyCallback: (Boolean) -> Unit = ready@{ ready ->
            if (closed.get() || epoch != engineEpoch) return@ready
            onReady(ready)
            if (ready && pendingFallbackStart && sessionRequested && !closed.get()) {
                pendingFallbackStart = false
                activeEngine?.start()
            }
        }

        return when (mode) {
            SpeechMode.PRIVATE_OFFLINE -> LiveSpeechEngine(
                context = appContext,
                onText = textCallback,
                onListening = listeningCallback,
                onReady = readyCallback,
                onError = { if (!closed.get() && epoch == engineEpoch) onError(it) },
                onStableText = stableCallback,
            )
            SpeechMode.SYSTEM_HIGH_ACCURACY -> SystemSpeechEngine(
                context = appContext,
                languageTag = languageTag,
                onText = textCallback,
                onListening = listeningCallback,
                onReady = readyCallback,
                onError = { if (!closed.get() && epoch == engineEpoch) onError(it) },
                onFallbackRequested = { reason -> if (!closed.get() && epoch == engineEpoch) fallbackToOffline(reason) },
                onStableText = stableCallback,
            )
        }
    }

    private fun fallbackToOffline(reason: String) {
        if (closed.get() || activeMode == SpeechMode.PRIVATE_OFFLINE || fallbackUsed) return
        fallbackUsed = true
        val shouldResume = sessionRequested
        carryPrefix = rawCurrent
        activeEngine?.close()
        activeMode = SpeechMode.PRIVATE_OFFLINE
        activeEngine = createEngine(activeMode)
        pendingFallbackStart = shouldResume
        onError(reason)
        activeEngine?.prepare()
    }

    fun actualMode(): SpeechMode = activeMode

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        sessionRequested = false
        pendingFallbackStart = false
        engineEpoch++
        activeEngine?.close()
        activeEngine = null
        edits.reset()
        editingJob?.cancel()
        scope.launch {
            editingMutex.withLock {
                runCatching { nanoEnhancer?.close() }
                punctuationModel?.close()
            }
            scope.cancel()
        }
    }
}
