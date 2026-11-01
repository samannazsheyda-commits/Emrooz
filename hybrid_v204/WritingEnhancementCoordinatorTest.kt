package com.nameemrooz.journal.writing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WritingEnhancementCoordinatorTest {
    private class FakeNano(var result: EnhancementResult) : WritingEnhancer {
        var calls = 0
        override suspend fun enhance(text: String, languageTag: String): EnhancementResult {
            calls++
            return result
        }
    }

    private fun local(restorer: PunctuationRestorer = NoOpPunctuationRestorer) =
        LocalWritingEnhancer(TextEditor(restorer))

    @Test fun disabled_never_calls_nano() = runBlocking {
        val nano = FakeNano(EnhancementResult.Applied("نباید استفاده شود"))
        val coordinator = WritingEnhancementCoordinator(local(), nano, nanoEnabled = false)

        assertEquals("امروز خوب بود", coordinator.stable("امروز خوب بود", "fa-IR"))
        assertEquals(0, nano.calls)
    }

    @Test fun unavailable_uses_local_result() = runBlocking {
        val nano = FakeNano(EnhancementResult.Unavailable("no_aicore"))
        val punctuation = PunctuationRestorer { text -> "$text." }
        val coordinator = WritingEnhancementCoordinator(local(punctuation), nano, nanoEnabled = true)

        assertEquals("امروز خوب بود.", coordinator.stable("امروز خوب بود", "fa-IR"))
        assertEquals(1, nano.calls)
    }

    @Test fun failed_uses_local_result() = runBlocking {
        val nano = FakeNano(EnhancementResult.Failed("timeout"))
        val coordinator = WritingEnhancementCoordinator(local(), nano, nanoEnabled = true)

        assertEquals("امروز خوب بود", coordinator.stable("امروز خوب بود", "fa-IR"))
        assertEquals(1, nano.calls)
    }

    @Test fun safe_nano_output_is_then_locally_punctuated() = runBlocking {
        val nano = FakeNano(EnhancementResult.Applied("امروز هوا عالی بود"))
        val punctuation = PunctuationRestorer { text -> "$text!" }
        val coordinator = WritingEnhancementCoordinator(local(punctuation), nano, nanoEnabled = true)

        assertEquals("امروز هوا عالی بود!", coordinator.stable("امروز هوا عاللی بود", "fa-IR"))
        assertEquals(1, nano.calls)
    }

    @Test fun nonblank_input_never_becomes_blank_on_fallback() = runBlocking {
        val nano = FakeNano(EnhancementResult.Applied("   "))
        val coordinator = WritingEnhancementCoordinator(local(), nano, nanoEnabled = true)

        val result = coordinator.stable("امروز خوب بود", "fa-IR")
        assertTrue(result.isNotBlank())
        assertEquals("امروز خوب بود", result)
    }

    @Test fun final_path_keeps_local_text_when_nano_fails() = runBlocking {
        val nano = FakeNano(EnhancementResult.Failed("quota"))
        val coordinator = WritingEnhancementCoordinator(local(), nano, nanoEnabled = true)

        val result = coordinator.final("امروز خوب بود", "fa-IR")
        assertFalse(result.isBlank())
        assertTrue(result.endsWith("."))
    }
}
