package com.nameemrooz.journal.writing

import java.util.Locale

/** Accepts only punctuation/casing changes; lexical token insertion/deletion/substitution is rejected. */
object LexicalSafety {
    private val punctuation = Regex("[\\p{P}\\p{S}]+")

    fun isPunctuationOnlyChange(input: String, output: String): Boolean =
        lexicalTokens(input) == lexicalTokens(output)

    fun lexicalTokens(text: String): List<String> = text
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .lowercase(Locale.ROOT)
        .replace(punctuation, " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeIf { it.isNotEmpty() }
        ?.split(' ')
        ?: emptyList()
}
