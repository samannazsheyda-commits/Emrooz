package com.nameemrooz.journal.writing

fun interface PunctuationRestorer {
    suspend fun restore(text: String): String
}

object NoOpPunctuationRestorer : PunctuationRestorer {
    override suspend fun restore(text: String): String = text
}
