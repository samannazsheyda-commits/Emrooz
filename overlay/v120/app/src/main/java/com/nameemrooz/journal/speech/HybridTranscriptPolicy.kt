package com.nameemrooz.journal.speech

object HybridTranscriptPolicy {
    private val specialMarkers = Regex("<\\|[^>]+\\|>")
    private val whitespace = Regex("\\s+")
    private val persian = Regex("[\\u0600-\\u06FF]")

    fun choose(streamingFinal: String, whisperFinal: String): String {
        val live = clean(streamingFinal)
        val whisper = clean(whisperFinal)

        if (whisper.isBlank()) return live
        if (live.isNotBlank() && persian.containsMatchIn(live) && !persian.containsMatchIn(whisper)) {
            return live
        }
        if (live.isNotBlank() && isDegenerate(whisper)) return live
        return whisper
    }

    private fun clean(value: String): String = value
        .replace(specialMarkers, " ")
        .replace(whitespace, " ")
        .trim()

    private fun isDegenerate(value: String): Boolean {
        val tokens = value.split(' ').filter { it.isNotBlank() }
        if (tokens.size < 6) return false
        val counts = tokens.groupingBy { it }.eachCount()
        val max = counts.values.maxOrNull() ?: return false
        return max.toDouble() / tokens.size.toDouble() >= 0.70
    }
}
