package com.nameemrooz.journal.writing

object StableDisplayComposer {
    private val whitespace = Regex("\\s+")
    private val whitespaceNoise = Regex("[\\t\\n\\r\\x0B\\f]| {2,}")
    private val edgePunctuation = Regex("^[،,.!؟?:؛;]+|[،,.!؟?:؛;]+$")
    fun compose(stableRaw: String, stableDisplay: String, currentRaw: String): String {
        val raw = normalize(currentRaw)
        val baseRaw = normalize(stableRaw)
        val baseDisplay = normalize(stableDisplay)
        if (baseRaw.isBlank() || baseDisplay.isBlank()) return raw

        if (raw == baseRaw) return baseDisplay
        if (raw.startsWith(baseRaw) && raw.getOrNull(baseRaw.length) == ' ') {
            return "$baseDisplay ${raw.substring(baseRaw.length + 1)}"
        }
        val baseWords = baseRaw.split(' ')
        val currentWords = raw.split(' ')
        if (currentWords.size < baseWords.size) return raw
        for (index in baseWords.indices) {
            if (comparable(baseWords[index]) != comparable(currentWords[index])) return raw
        }
        val suffix = currentWords.drop(baseWords.size).joinToString(" ")
        return if (suffix.isBlank()) baseDisplay else "$baseDisplay $suffix"
    }

    private fun comparable(word: String): String = word
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace(edgePunctuation, "")

    private fun normalize(value: String): String {
        val trimmed = value.trim()
        return if (whitespaceNoise.containsMatchIn(trimmed)) trimmed.replace(whitespace, " ") else trimmed
    }
}
