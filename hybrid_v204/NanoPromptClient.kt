package com.nameemrooz.journal.writing

enum class NanoStatus { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

interface NanoPromptClient : AutoCloseable {
    suspend fun status(): NanoStatus
    suspend fun requestDownload(): NanoStatus
    suspend fun generate(systemInstruction: String, prompt: String): String
}
