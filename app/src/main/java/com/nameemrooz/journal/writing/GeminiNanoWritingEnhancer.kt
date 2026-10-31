package com.nameemrooz.journal.writing

import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CancellationException

class GeminiNanoWritingEnhancer(
    private val clientFactory: () -> NanoPromptClient,
    private val timeoutMs: Long = 2500L,
) : WritingEnhancer, AutoCloseable {
    constructor(client: NanoPromptClient, timeoutMs: Long = 2500L) : this({ client }, timeoutMs)
    private var client: NanoPromptClient? = null
    private var closed = false
    private var failures = 0

    override suspend fun enhance(text: String, languageTag: String): EnhancementResult {
        if (text.isBlank()) return EnhancementResult.Failed("blank_input")
        if (closed || failures >= 3) return EnhancementResult.Unavailable("session_disabled")

        val result = withTimeoutOrNull(timeoutMs) {
            runCatching {
                val client = client ?: clientFactory().also { client = it }
                when (client.status()) {
                    NanoStatus.UNAVAILABLE -> EnhancementResult.Unavailable("unsupported")
                    NanoStatus.DOWNLOADING -> EnhancementResult.Unavailable("downloading")
                    NanoStatus.DOWNLOADABLE -> {
                        client.requestDownload()
                        EnhancementResult.Unavailable("download_requested")
                    }
                    NanoStatus.AVAILABLE -> {
                        val raw = client.generate(systemInstruction(languageTag), text)
                        val candidate = unwrap(raw)
                        if (candidate.isBlank()) EnhancementResult.Failed("empty_candidate")
                        else if (WritingEditSafety.accept(text, candidate)) EnhancementResult.Applied(candidate)
                        else EnhancementResult.Failed("unsafe_candidate")
                    }
                }
            }.getOrElse {
                if (it is CancellationException) throw it
                EnhancementResult.Failed("generation_error")
            }
        } ?: EnhancementResult.Failed("timeout")
        if (result is EnhancementResult.Failed) failures++
        else if (result is EnhancementResult.Applied) failures = 0
        return result
    }

    private fun systemInstruction(languageTag: String): String = if (languageTag.startsWith("fa", ignoreCase = true)) {
        """
        فقط متن اصلاح‌شده را برگردان؛ هیچ توضیحی ننویس. معنی، ترتیب و محتوای جمله را حفظ کن. نام اشخاص، عددها، تاریخ، ساعت، مبلغ، واژه‌های لاتین/انگلیسی، زبان متن و تکرارهای تأکیدی مثل «خیلی خیلی» را دقیقاً حفظ کن. فقط غلط املایی واضح، شکل حروف فارسی/عربی، نیم‌فاصله، فاصله‌گذاری و علائم نگارشی را اصلاح کن. جمله را بازنویسی، خلاصه، ترجمه یا زیباتر نکن و هیچ واژه یا اطلاعات تازه‌ای اضافه یا حذف نکن.
        """.trimIndent()
    } else {
        """
        Return corrected text only, with no explanation. Preserve meaning, sentence content, word order unless a spelling correction requires it, names, numbers, dates, times, amounts, Latin tokens, language, and repeated emphasis. Only correct obvious spelling, whitespace, and punctuation. Do not rewrite style, summarize, translate, add information, or remove words.
        """.trimIndent()
    }

    private fun unwrap(value: String): String {
        var text = value.trim()
        if (text.startsWith("```") && text.endsWith("```") && text.length >= 6) {
            text = text.removePrefix("```").removeSuffix("```").trim()
            val firstNewline = text.indexOf('\n')
            if (firstNewline in 1..12 && text.substring(0, firstNewline).all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                text = text.substring(firstNewline + 1).trim()
            }
        }
        val quotePairs = listOf('"' to '"', '«' to '»', '“' to '”')
        for ((open, close) in quotePairs) {
            if (text.length >= 2 && text.first() == open && text.last() == close) {
                text = text.substring(1, text.length - 1).trim()
                break
            }
        }
        return text
    }

    override fun close() {
        closed = true
        client?.close()
        client = null
    }
}
