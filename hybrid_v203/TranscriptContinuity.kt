package com.nameemrooz.journal.speech

object TranscriptContinuity {
    fun combine(prefix: String, incoming: String): String {
        val left = normalize(prefix)
        val right = normalize(incoming)
        if (left.isEmpty()) return right
        if (right.isEmpty()) return left

        val leftWords = left.split(' ')
        val rightWords = right.split(' ')
        val maxOverlap = minOf(leftWords.size, rightWords.size, 12)
        var overlap = 0
        for (size in maxOverlap downTo 1) {
            var equal = true
            for (offset in 0 until size) {
                val a = comparable(leftWords[leftWords.size - size + offset])
                val b = comparable(rightWords[offset])
                if (a != b) {
                    equal = false
                    break
                }
            }
            if (equal) {
                overlap = size
                break
            }
        }
        val suffix = rightWords.drop(overlap).joinToString(" ")
        return if (suffix.isBlank()) left else "$left $suffix"
    }

    private fun comparable(word: String): String =
        word.replace(Regex("[،,.!؟?:؛;]+$"), "")

    private fun normalize(value: String): String =
        value.replace(Regex("\\s+"), " ").trim()
}
