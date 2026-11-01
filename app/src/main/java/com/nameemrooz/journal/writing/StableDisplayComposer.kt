package com.nameemrooz.journal.writing

object StableDisplayComposer {
    fun compose(stableRaw: String, stableDisplay: String, currentRaw: String): String {
        val raw = normalize(currentRaw)
        val baseRaw = normalize(stableRaw)
        val baseDisplay = normalize(stableDisplay)
        if (baseRaw.isBlank() || baseDisplay.isBlank()) return raw

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
        .replace(Regex("^[،,.!؟?:؛;]+|[،,.!؟?:؛;]+$"), "")

    private fun normalize(value: String): String = value.replace(Regex("\\s+"), " ").trim()
}
