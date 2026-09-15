package com.nameemrooz.journal.writing

interface WritingEnhancer {
    suspend fun enhance(text: String, languageTag: String): EnhancementResult
}

sealed interface EnhancementResult {
    data class Applied(val text: String) : EnhancementResult
    data class Unavailable(val reason: String) : EnhancementResult
    data class Failed(val reason: String) : EnhancementResult
}
