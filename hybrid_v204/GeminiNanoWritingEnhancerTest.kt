package com.nameemrooz.journal.writing

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiNanoWritingEnhancerTest {
    private class FakeNanoPromptClient(
        var currentStatus: NanoStatus,
        var generatedText: String = "",
        var generationError: Throwable? = null,
        var generationDelayMs: Long = 0L,
    ) : NanoPromptClient {
        var generateCalls = 0
        var downloadCalls = 0

        override suspend fun status(): NanoStatus = currentStatus

        override suspend fun requestDownload(): NanoStatus {
            downloadCalls += 1
            currentStatus = NanoStatus.DOWNLOADING
            return currentStatus
        }

        override suspend fun generate(systemInstruction: String, prompt: String): String {
            generateCalls += 1
            if (generationDelayMs > 0) delay(generationDelayMs)
            generationError?.let { throw it }
            return generatedText
        }

        override fun close() = Unit
    }

    @Test fun unavailable_never_generates() = runBlocking {
        val client = FakeNanoPromptClient(NanoStatus.UNAVAILABLE)
        val enhancer = GeminiNanoWritingEnhancer(client)

        val result = enhancer.enhance("سلام دنیا", "fa-IR")

        assertTrue(result is EnhancementResult.Unavailable)
        assertEquals(0, client.generateCalls)
        assertEquals(0, client.downloadCalls)
    }

    @Test fun downloadable_requests_download_and_returns_unavailable_for_current_call() = runBlocking {
        val client = FakeNanoPromptClient(NanoStatus.DOWNLOADABLE)
        val enhancer = GeminiNanoWritingEnhancer(client)

        val result = enhancer.enhance("سلام دنیا", "fa-IR")

        assertTrue(result is EnhancementResult.Unavailable)
        assertEquals(1, client.downloadCalls)
        assertEquals(0, client.generateCalls)
    }

    @Test fun available_safe_candidate_is_applied() = runBlocking {
        val client = FakeNanoPromptClient(
            currentStatus = NanoStatus.AVAILABLE,
            generatedText = "امروز هوا عالی بود.",
        )
        val enhancer = GeminiNanoWritingEnhancer(client)

        val result = enhancer.enhance("امروز هوا عاللی بود", "fa-IR")

        assertEquals(EnhancementResult.Applied("امروز هوا عالی بود."), result)
        assertEquals(1, client.generateCalls)
    }

    @Test fun unsafe_candidate_is_failed() = runBlocking {
        val client = FakeNanoPromptClient(
            currentStatus = NanoStatus.AVAILABLE,
            generatedText = "ساعت ۹ رسیدم.",
        )
        val enhancer = GeminiNanoWritingEnhancer(client)

        val result = enhancer.enhance("ساعت ۸ رسیدم", "fa-IR")

        assertEquals(EnhancementResult.Failed("unsafe_candidate"), result)
    }

    @Test fun generation_exception_is_failed() = runBlocking {
        val client = FakeNanoPromptClient(
            currentStatus = NanoStatus.AVAILABLE,
            generationError = IllegalStateException("boom"),
        )
        val enhancer = GeminiNanoWritingEnhancer(client)

        val result = enhancer.enhance("سلام دنیا", "fa-IR")

        assertTrue(result is EnhancementResult.Failed)
    }

    @Test fun timeout_is_failed() = runBlocking {
        val client = FakeNanoPromptClient(
            currentStatus = NanoStatus.AVAILABLE,
            generatedText = "سلام دنیا.",
            generationDelayMs = 80L,
        )
        val enhancer = GeminiNanoWritingEnhancer(client, timeoutMs = 10L)

        val result = enhancer.enhance("سلام دنیا", "fa-IR")

        assertEquals(EnhancementResult.Failed("timeout"), result)
    }
}
