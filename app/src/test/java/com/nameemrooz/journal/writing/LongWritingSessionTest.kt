package com.nameemrooz.journal.writing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LongWritingSessionTest {
    @Test fun optional_models_only_receive_a_bounded_recent_tail_of_a_long_diary() = runBlocking {
        val requests = mutableListOf<String>()
        val punctuation = PunctuationRestorer { text -> requests += text; "$text." }
        val nano = object : WritingEnhancer {
            override suspend fun enhance(text: String, languageTag: String): EnhancementResult {
                requests += text
                return EnhancementResult.Unavailable("test_device")
            }
        }
        val source = "شروع، با میثم و ساعت ۱۲:۳۰. " + "این بخش از نوشته قبلا ثبت شده است ".repeat(100) + "امروز دوباره پیاده رفتم"
        val result = WritingEnhancementCoordinator(LocalWritingEnhancer(TextEditor(punctuation)), nano, true).stable(source, "fa-IR")
        assertTrue("No source words may disappear", LexicalSafety.isPunctuationOnlyChange(source, result))
        assertTrue("Approved earlier punctuation must remain intact", result.startsWith("شروع، با میثم و ساعت ۱۲:۳۰."))
        assertTrue("The newest sentence is still punctuated", result.endsWith("امروز دوباره پیاده رفتم."))
        assertTrue(requests.isNotEmpty())
        requests.forEach { request ->
            assertTrue("Model received the full diary (${request.length} characters)", request.length <= 1200)
            assertTrue("Model received too many words", request.split(' ').size <= 80)
            assertFalse("Earlier pages must not be repeatedly sent for editing", request.contains("شروع"))
        }
    }

    @Test fun a_single_oversize_token_is_kept_without_sending_it_to_optional_models() = runBlocking {
        var calls = 0
        val punctuation = PunctuationRestorer { calls++; it }
        val nano = object : WritingEnhancer {
            override suspend fun enhance(text: String, languageTag: String): EnhancementResult { calls++; return EnhancementResult.Failed("must_not_run") }
        }
        val text = "ک".repeat(5000)
        val result = WritingEnhancementCoordinator(LocalWritingEnhancer(TextEditor(punctuation)), nano, true).stable(text,"fa-IR")
        assertEquals(text,result)
        assertEquals("Oversize input must bypass optional inference",0,calls)
    }
}
