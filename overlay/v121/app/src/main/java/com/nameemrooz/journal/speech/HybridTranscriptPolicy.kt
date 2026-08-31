package com.nameemrooz.journal.speech

/**
 * Defensive policy kept for compatibility with older tests/callers.
 * A usable live transcript is never replaced by a speculative second pass.
 */
object HybridTranscriptPolicy {
    fun choose(streamingFinal: String, secondPass: String): String {
        val live = streamingFinal.trim()
        return if (live.isNotBlank()) live else secondPass.trim()
    }
}
