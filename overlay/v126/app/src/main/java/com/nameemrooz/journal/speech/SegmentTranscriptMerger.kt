package com.nameemrooz.journal.speech

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Per-segment fallback and overlap-aware transcript assembly. */
class SegmentTranscriptMerger {
    fun chooseSegment(stableLive: String, finalText: String): String {
        val live = normalize(stableLive)
        val final = normalize(finalText)
        if (final.isBlank()) return live
        if (persianRatio(final) < 0.55) return live
        if (live.isBlank()) return if (dominantRepetition(words(final))) "" else final

        val liveWords = words(live)
        val finalWords = words(final)
        if (finalWords.isEmpty()) return live
        if (dominantRepetition(finalWords)) return live

        if (liveWords.size >= 4) {
            val minWords = max(2, ceil(liveWords.size * 0.45).toInt())
            val maxWords = liveWords.size * 2 + 5
            if (finalWords.size !in minWords..maxWords) return live
        }

        if (liveWords.size >= 4 && finalWords.size >= 4) {
            val common = liveWords.toSet().intersect(finalWords.toSet()).size
            val base = min(liveWords.toSet().size, finalWords.toSet().size).coerceAtLeast(1)
            if (common.toDouble() / base < 0.30) return live
        }
        return final
    }

    fun merge(base: String, next: String): String {
        val left = normalize(base)
        val right = normalize(next)
        if (left.isBlank()) return right
        if (right.isBlank()) return left

        val a = left.split(' ').filter(String::isNotBlank)
        val b = right.split(' ').filter(String::isNotBlank)
        val limit = minOf(8, a.size, b.size)
        var overlap = 0
        for (n in limit downTo 1) {
            if (a.takeLast(n).map(::tokenKey) == b.take(n).map(::tokenKey)) {
                overlap = n
                break
            }
        }
        return (a + b.drop(overlap)).joinToString(" ").trim()
    }

    private fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    private fun words(value: String): List<String> = value
        .replace(Regex("[،,.!؟؛:…]+"), " ")
        .split(Regex("\\s+"))
        .map(String::trim)
        .filter(String::isNotBlank)

    private fun tokenKey(value: String): String = value
        .trim('،', ',', '.', '!', '؟', '؛', ':', '…')
        .replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')

    private fun dominantRepetition(tokens: List<String>): Boolean {
        if (tokens.size < 4) return false
        val maxCount = tokens.groupingBy(::tokenKey).eachCount().values.maxOrNull() ?: return false
        return maxCount >= 3 && maxCount.toDouble() / tokens.size >= 0.60
    }

    private fun persianRatio(value: String): Double {
        val letters = value.filter(Char::isLetter)
        if (letters.isEmpty()) return 0.0
        val persian = letters.count {
            it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' || it in '\u08A0'..'\u08FF'
        }
        return persian.toDouble() / letters.length
    }
}
