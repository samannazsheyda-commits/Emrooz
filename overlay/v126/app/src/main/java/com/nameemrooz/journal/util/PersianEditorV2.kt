package com.nameemrooz.journal.util

/**
 * Deterministic Persian editor for speech transcripts.
 * It fixes orthography and punctuation only; it never paraphrases, converts
 * spoken numbers to digits, or collapses intentional repetition.
 */
object PersianEditorV2 {
    private val phraseFixes = linkedMapOf(
        "نمی خواستم" to "نمی‌خواستم", "نمیخواستم" to "نمی‌خواستم",
        "می خواستم" to "می‌خواستم", "میخواستم" to "می‌خواستم",
        "نمی دونم" to "نمی‌دونم", "نمیدونم" to "نمی‌دونم",
        "نمی دونستم" to "نمی‌دونستم", "نمیدونستم" to "نمی‌دونستم",
        "می دونم" to "می‌دونم", "میدونم" to "می‌دونم",
        "نمی تونم" to "نمی‌تونم", "نمیتونم" to "نمی‌تونم",
        "می تونم" to "می‌تونم", "میتونم" to "می‌تونم",
        "می خوام" to "می‌خوام", "میخوام" to "می‌خوام",
        "نمی خوام" to "نمی‌خوام", "نمیخوام" to "نمی‌خوام",
        "می خوای" to "می‌خوای", "میخوای" to "می‌خوای",
        "می خواد" to "می‌خواد", "میخواد" to "می‌خواد",
        "می شه" to "می‌شه", "میشه" to "می‌شه",
        "نمی شه" to "نمی‌شه", "نمیشه" to "نمی‌شه",
        "می کنم" to "می‌کنم", "میکنم" to "می‌کنم",
        "نمی کنم" to "نمی‌کنم", "نمیکنم" to "نمی‌کنم",
        "می کنی" to "می‌کنی", "میکنی" to "می‌کنی",
        "می کنه" to "می‌کنه", "میکنه" to "می‌کنه",
        "نمی کنه" to "نمی‌کنه", "نمیکنه" to "نمی‌کنه",
        "می کنیم" to "می‌کنیم", "میکنیم" to "می‌کنیم",
        "می کنند" to "می‌کنند", "میکنند" to "می‌کنند",
        "می رم" to "می‌رم", "میرم" to "می‌رم",
        "می ری" to "می‌ری", "میری" to "می‌ری",
        "می ره" to "می‌ره", "میره" to "می‌ره",
        "می ریم" to "می‌ریم", "میریم" to "می‌ریم",
        "می رن" to "می‌رن", "میرن" to "می‌رن",
        "می گم" to "می‌گم", "میگم" to "می‌گم",
        "می گه" to "می‌گه", "میگه" to "می‌گه",
        "می گی" to "می‌گی", "میگی" to "می‌گی",
        "می اومدم" to "می‌اومدم", "میومدم" to "می‌اومدم",
        "نمی فهمیدم" to "نمی‌فهمیدم", "نمیفهمیدم" to "نمی‌فهمیدم",
        "می ذارم" to "می‌ذارم", "میذارم" to "می‌ذارم",
        "می خوابم" to "می‌خوابم", "میخوابم" to "می‌خوابم",
        "می خونم" to "می‌خونم", "میخونم" to "می‌خونم",
        "نمی خونم" to "نمی‌خونم", "نمیخونم" to "نمی‌خونم",
        "می بینم" to "می‌بینم", "میبینم" to "می‌بینم",
        "نمی بینم" to "نمی‌بینم", "نمیبینم" to "نمی‌بینم",
        "خونه ی" to "خونه‌ی",
        "سهیل ممنوعی" to "سهیل ممدوحی",
        "سهیل ممدوهی" to "سهیل ممدوحی",
        "سهیل ممدو حی" to "سهیل ممدوحی",
        "روح الله" to "روح‌الله",
        "اهوار" to "اهواز",
    )

    private val fillerTokens = setOf("اِ", "اِم", "اِمم", "امم", "اممم", "هوم", "هومم", "همم", "ممم", "آآ", "آآآ")
    private val clearQuestionStarts = listOf("چرا", "آیا", "چطور", "چجوری", "کجا", "کی", "مگه")

    fun cleanLive(input: String): String = normalizeWhitespace(normalizeUnicode(input))

    fun editFinal(input: String, sentenceBoundary: Boolean = true): String {
        if (input.isBlank()) return ""
        var text = normalizeUnicode(input)
        text = normalizeWhitespace(text)
        text = removeStandaloneFillers(text)
        text = applyPhraseFixes(text)
        text = normalizeMorphology(text)
        text = normalizePunctuationSpacing(text)
        if (sentenceBoundary) text = restoreConservativeEnding(text)
        return normalizePunctuationSpacing(text).trim()
    }

    private fun normalizeUnicode(input: String): String = input
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace('ة', 'ه')
        .replace('ۀ', 'ه')
        .replace('\u200e', ' ')
        .replace('\u200f', ' ')
        .replace('\u00A0', ' ')
        .replace(',', '،')
        .replace('?', '؟')
        .replace(';', '؛')

    private fun normalizeWhitespace(input: String): String = input
        .replace(Regex("[\\t\\n\\r]+"), " ")
        .replace(Regex(" +"), " ")
        .trim()

    private fun removeStandaloneFillers(input: String): String = input
        .split(' ')
        .filter { token -> token.trim('،', '.', '!', '؟', '؛', ':') !in fillerTokens }
        .joinToString(" ")

    private fun applyPhraseFixes(input: String): String {
        var text = input
        phraseFixes.forEach { (from, to) -> text = text.replace(from, to) }
        return text
    }

    private fun normalizeMorphology(input: String): String {
        var text = input
        // Generic verbal prefixes only when ASR emitted them as separate words.
        text = text.replace(
            Regex("(^|\\s)(ن?می)\\s+([\\u0600-\\u06FF]+)"),
            "$1$2‌$3"
        )
        // Persian plural suffixes.
        text = text.replace(
            Regex("(?<=[\\u0600-\\u06FF])\\s+های(?=\\s|$|[،.!؟؛:])"),
            "‌های"
        )
        text = text.replace(
            Regex("(?<=[\\u0600-\\u06FF])\\s+ها(?=\\s|$|[،.!؟؛:])"),
            "‌ها"
        )
        // Comparative suffixes.
        text = text.replace(
            Regex("(?<=[\\u0600-\\u06FF])\\s+(تر|ترین)(?=\\s|$|[،.!؟؛:])"),
            "‌$1"
        )
        // Ezafe after heh: «خانه ی» -> «خانه‌ی».
        text = text.replace(
            Regex("ه\\s+ی(?=\\s|$|[،.!؟؛:])"),
            "ه‌ی"
        )
        return text
    }

    private fun normalizePunctuationSpacing(input: String): String = input
        .replace(Regex("\\s+([،؛:؟!.])"), "$1")
        .replace(Regex("([،؛:؟!.])(?=[^\\s،؛:؟!.])"), "$1 ")
        .replace(Regex(" +"), " ")
        .trim()

    private fun restoreConservativeEnding(input: String): String {
        val text = input.trim()
        if (text.isBlank() || text.last() in charArrayOf('.', '!', '؟')) return text
        val first = text.substringBefore(' ').trim('،', ':')
        return text + if (first in clearQuestionStarts) "؟" else "."
    }
}
