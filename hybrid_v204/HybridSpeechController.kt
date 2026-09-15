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
    private val stateLock = Any()
    private val punctuationModel = if (languageTag.startsWith("fa", ignoreCase = true)) {
        LocalPunctuationModel(appContext)
    } else null
    private val editor = TextEditor(punctuationModel ?: NoOpPunctuationRestorer)
    private val localEnhancer = LocalWritingEnhancer(editor)
    private val nanoEnhancer: GeminiNanoWritingEnhancer? = if (enhancedEditingEnabled) {
        GeminiNanoWritingEnhancer(MlKitNanoPromptClient())
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
    private var stableRaw = ""
    private var stableDisplay = ""
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
        synchronized(stateLock) {
            stableRaw = ""
            stableDisplay = ""
        }
        if (activeEngine == null) activeEngine = createEngine(activeMode)
        activeEngine?.start()
    }

    override fun stop() {
        sessionRequested = false
        pendingFallbackStart = false
        activeEngine?.stop()
    }

    private fun createEngine(mode: SpeechMode): SpeechEngine {
        val textCallback: (String) -> Unit = { candidate ->
            val combinedRaw = TranscriptContinuity.combine(carryPrefix, candidate)
            rawCurrent = combinedRaw
            val normalized = editor.live(combinedRaw, languageTag)
            normalizedCurrent = normalized
            val display = synchronized(stateLock) {
                StableDisplayComposer.compose(stableRaw, stableDisplay, normalized)
            }
            onText(display)
        }
        val stableCallback: (String) -> Unit = { candidate ->
            val combinedRaw = TranscriptContinuity.combine(carryPrefix, candidate)
            rawCurrent = combinedRaw
            val normalizedSnapshot = editor.live(combinedRaw, languageTag)
            normalizedCurrent = normalizedSnapshot
            scope.launch {
                val edited = coordinator.stable(normalizedSnapshot, languageTag)
                val latest = normalizedCurrent
                val display = synchronized(stateLock) {
                    stableRaw = normalizedSnapshot
                    stableDisplay = edited
                    StableDisplayComposer.compose(stableRaw, stableDisplay, latest)
                }
                mainHandler.post {
                    if (!closed.get()) onText(display)
                }
            }
        }
        val readyCallback: (Boolean) -> Unit = { ready ->
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
                onListening = onListening,
                onReady = readyCallback,
                onError = onError,
                onStableText = stableCallback,
            )
            SpeechMode.SYSTEM_HIGH_ACCURACY -> SystemSpeechEngine(
                context = appContext,
                languageTag = languageTag,
                onText = textCallback,
                onListening = onListening,
                onReady = readyCallback,
                onError = onError,
                onFallbackRequested = { reason -> fallbackToOffline(reason) },
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
        activeEngine?.close()
        activeEngine = null
        runCatching { nanoEnhancer?.close() }
        punctuationModel?.close()
        scope.cancel()
    }
}
