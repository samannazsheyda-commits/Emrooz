package com.nameemrooz.journal.speech

/**
 * Prefer the full-context final recognizer when its output looks like a plausible
 * Persian transcript. Fall back to the streaming text instead of replacing a
 * usable live transcript with empty or obviously broken output.
 */
object FinalTranscriptPolicy {
    fun choose(live: String, fullContext: String): String {
        val a = live.trim()
        val b = fullContext.trim()
        if (b.isBlank()) return a
        if (a.isBlank()) return if (looksPersian(b)) b else a
        if (!looksPersian(b)) return a

        val liveWords = wordCount(a)
        val finalWords = wordCount(b)
        val minWords = maxOf(1, (liveWords * 0.45f).toInt())
        val maxWords = liveWords * 2 + 5
        if (finalWords !in minWords..maxWords) return a

        return b
    }

    private fun wordCount(value: String): Int =
        value.split(Regex("\\s+")).count { it.isNotBlank() }

    private fun looksPersian(value: String): Boolean {
        var letters = 0
        var persian = 0
        value.forEach { ch ->
            if (ch.isLetter()) {
                letters++
                if (ch in '\u0600'..'\u06FF' || ch in '\u0750'..'\u077F') persian++
            }
        }
        return letters > 0 && persian.toFloat() / letters >= 0.70f
    }
}
