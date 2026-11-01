package com.nameemrooz.journal.writing

import kotlin.math.floor

/**
 * Conservative validator for on-device generative writing edits.
 *
 * It permits punctuation/spacing/ZWNJ changes and a very small number of
 * same-position Persian spelling corrections, while rejecting insertions,
 * deletions, numeric changes, Latin-token changes, repeated-emphasis loss,
 * and widespread lexical rewrites.
 */
object WritingEditSafety {
    private val numberRun = Regex("[0-9۰-۹٠-٩]+(?:[./:٫٬,_-][0-9۰-۹٠-٩]+)*")
    private val lexicalToken = Regex("[\\p{L}\\p{N}]+")
    private val latinLetter = Regex("[A-Za-z]")

    fun accept(original: String, candidate: String): Boolean {
        val source = original.trim()
        val edited = candidate.trim()
        if (source.isBlank() || edited.isBlank()) return false

        if (numberRuns(source) != numberRuns(edited)) return false

        val sourceTokens = tokens(source)
        val candidateTokens = tokens(edited)
        if (sourceTokens.isEmpty() || sourceTokens.size != candidateTokens.size) return false

        val sourceLatin = sourceTokens.filter { latinLetter.containsMatchIn(it.raw) }.map { it.raw }
        val candidateLatin = candidateTokens.filter { latinLetter.containsMatchIn(it.raw) }.map { it.raw }
        if (sourceLatin != candidateLatin) return false

        // Preserve explicit repeated emphasis such as «خیلی خیلی».
        for (index in 1 until sourceTokens.size) {
            if (sourceTokens[index - 1].normalized == sourceTokens[index].normalized) {
                if (candidateTokens[index - 1].normalized != sourceTokens[index - 1].normalized ||
                    candidateTokens[index].normalized != sourceTokens[index].normalized
                ) return false
            }
        }

        var changed = 0
        sourceTokens.indices.forEach { index ->
            val before = sourceTokens[index]
            val after = candidateTokens[index]
            if (before.normalized == after.normalized) return@forEach

            if (!isPersianLettersOnly(before.normalized) || !isPersianLettersOnly(after.normalized)) return false
            val maxDistance = if (maxOf(before.normalized.length, after.normalized.length) <= 5) 1 else 2
            if (levenshtein(before.normalized, after.normalized) > maxDistance) return false
            changed += 1
        }

        val maxChanged = maxOf(1, floor(sourceTokens.size * 0.20).toInt())
        return changed <= maxChanged
    }

    private data class Token(val raw: String, val normalized: String)

    private fun numberRuns(value: String): List<String> =
        numberRun.findAll(value).map { it.value }.toList()

    private fun tokens(value: String): List<Token> {
        val zwnjExpanded = value.replace('\u200C', ' ')
        return lexicalToken.findAll(zwnjExpanded).map { match ->
            val raw = match.value
            Token(raw = raw, normalized = normalizePersianVariants(raw))
        }.toList()
    }

    private fun normalizePersianVariants(value: String): String = value
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')

    private fun isPersianLettersOnly(value: String): Boolean =
        value.isNotEmpty() && value.all { ch ->
            Character.isLetter(ch) && Character.UnicodeScript.of(ch.code) == Character.UnicodeScript.ARABIC
        }

    private fun levenshtein(left: String, right: String): Int {
        if (left == right) return 0
        if (left.isEmpty()) return right.length
        if (right.isEmpty()) return left.length

        var previous = IntArray(right.length + 1) { it }
        var current = IntArray(right.length + 1)
        for (i in left.indices) {
            current[0] = i + 1
            for (j in right.indices) {
                val substitution = previous[j] + if (left[i] == right[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    substitution,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[right.length]
    }
}
