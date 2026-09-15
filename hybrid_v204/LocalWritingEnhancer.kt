package com.nameemrooz.journal.writing

class LocalWritingEnhancer(
    private val editor: TextEditor,
) {
    fun live(text: String, languageTag: String): String =
        editor.live(text, languageTag)

    suspend fun stable(text: String, languageTag: String): String =
        editor.stable(text, languageTag)

    suspend fun final(text: String, languageTag: String): String =
        editor.final(text, languageTag)
}
