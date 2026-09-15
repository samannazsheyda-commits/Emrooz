package com.nameemrooz.journal.writing

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/** Google ML Kit Prompt API adapter. Inference is performed by the device AICore service. */
class MlKitNanoPromptClient : NanoPromptClient {
    private val model = Generation.getClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadRequested = AtomicBoolean(false)

    override suspend fun status(): NanoStatus = when (model.checkStatus()) {
        FeatureStatus.AVAILABLE -> NanoStatus.AVAILABLE
        FeatureStatus.DOWNLOADABLE -> NanoStatus.DOWNLOADABLE
        FeatureStatus.DOWNLOADING -> NanoStatus.DOWNLOADING
        else -> NanoStatus.UNAVAILABLE
    }

    override suspend fun requestDownload(): NanoStatus {
        if (downloadRequested.compareAndSet(false, true)) {
            scope.launch {
                try {
                    model.download().collect { /* AICore owns progress/provisioning. */ }
                } finally {
                    downloadRequested.set(false)
                }
            }
        }
        return NanoStatus.DOWNLOADING
    }

    override suspend fun generate(systemInstruction: String, prompt: String): String {
        // Keep the approved narrow instruction in the same text part instead of
        // relying on a separate system-instruction channel.
        val combinedPrompt = buildString {
            append(systemInstruction.trim())
            append("\n\n---\nمتن برای اصلاح:\n")
            append(prompt.trim())
            append("\n---\nفقط متن اصلاح‌شده را برگردان.")
        }
        val request = generateContentRequest(TextPart(combinedPrompt)) {
            temperature = 0.0f
            topK = 1
            candidateCount = 1
            maxOutputTokens = 512
        }
        val response = model.generateContent(request)
        return response.candidates.firstOrNull()?.text.orEmpty()
    }

    override fun close() {
        scope.cancel()
        runCatching { model.close() }
    }
}
