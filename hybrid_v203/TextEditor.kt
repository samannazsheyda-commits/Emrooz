package com.nameemrooz.journal.writing

import com.nameemrooz.journal.util.PersianEditorial

class TextEditor(
    private val punctuationRestorer: PunctuationRestorer = NoOpPunctuationRestorer,
) {
    fun live(text: String, languageTag: String): String =
        if (isPersian(languageTag)) PersianEditorial.clean(text, false)
        else normalizeEnglish(text)

    suspend fun stable(text: String, languageTag: String): String {
        val normalized = live(text, languageTag)
        if (normalized.isBlank()) return normalized
        if (!isPersian(languageTag)) return normalizeEnglish(normalized)
        val restored = runCatching { punctuationRestorer.restore(normalized) }.getOrDefault(normalized)
        val accepted = if (LexicalSafety.isPunctuationOnlyChange(normalized, restored)) restored else normalized
        return PersianEditorial.clean(accepted, false)
    }

    suspend fun final(text: String, languageTag: String): String {
        val stable = stable(text, languageTag)
        return if (isPersian(languageTag)) PersianEditorial.clean(stable, true)
        else normalizeEnglishFinal(stable)
    }

    private fun isPersian(languageTag: String): Boolean = languageTag.startsWith("fa", ignoreCase = true)

    private fun normalizeEnglish(value: String): String = value
        .replace(Regex("\\s+([,;:.!?])"), "$1")
        .replace(Regex("([,;:.!?])(?=\\S)"), "$1 ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizeEnglishFinal(value: String): String {
        val clean = normalizeEnglish(value)
        return if (clean.isBlank() || clean.last() in listOf('.', '!', '?')) clean else "$clean."
    }
}
