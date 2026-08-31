package com.nameemrooz.journal.speech

/**
 * Chooses the safest final transcript without allowing a broken second pass
 * to overwrite a coherent live transcript.
 */
object FinalTranscriptPolicy {
    fun choose(streaming: String, fullContext: String): String {
        val live = streaming.trim()
        val final = fullContext.trim()
        if (final.isBlank()) return live
        if (live.isBlank()) return if (persianRatio(final) >= 0.55) final else live
        if (persianRatio(final) < 0.55) return live

        val liveWords = words(live)
        val finalWords = words(final)
        if (finalWords.isEmpty()) return live

        // A full-context pass must not collapse into a tiny fragment.
        if (liveWords.size >= 4) {
            val minWords = kotlin.math.max(2, kotlin.math.ceil(liveWords.size * 0.45).toInt())
            val maxWords = liveWords.size * 2 + 5
            if (finalWords.size !in minWords..maxWords) return live
        }

        // Reject classic CTC degeneration such as «رولا رولا رولا رولا».
        if (dominantRepetition(finalWords)) return live

        // When both hypotheses are meaningful, the final pass must still be
        // recognisably about the same utterance. This protects the user's text
        // while still allowing spelling and phrasing improvements.
        if (liveWords.size >= 4 && finalWords.size >= 4) {
            val liveSet = liveWords.toSet()
            val finalSet = finalWords.toSet()
            val common = liveSet.intersect(finalSet).size
            val base = kotlin.math.min(liveSet.size, finalSet.size).coerceAtLeast(1)
            if (common.toDouble() / base.toDouble() < 0.35) return live
        }

        return final
    }

    private fun words(value: String): List<String> = value
        .replace(Regex("[،,.!؟؛:…]+"), " ")
        .split(Regex("\\s+"))
        .map { it.trim() }
        .filter { it.isNotBlank() }

    private fun dominantRepetition(tokens: List<String>): Boolean {
        if (tokens.size < 4) return false
        val counts = tokens.groupingBy { it }.eachCount()
        val maxCount = counts.values.maxOrNull() ?: return false
        return maxCount >= 3 && maxCount.toDouble() / tokens.size.toDouble() >= 0.60
    }

    private fun persianRatio(value: String): Double {
        val letters = value.filter { it.isLetter() }
        if (letters.isEmpty()) return 0.0
        val persian = letters.count {
            it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\u08A0'..'\u08FF'
        }
        return persian.toDouble() / letters.length.toDouble()
    }
}
