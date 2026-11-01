package com.nameemrooz.journal.writing

/** Inserts model labels into the source; token decoding must never rewrite a diary entry. */
internal object SourcePunctuationRenderer {
    const val MAX_CONTENT_TOKENS = 126
    val postLabels = listOf(
        "", ".", ",", "?", "？", "，", "。", "、", "・", "।", "؟", "،", ";", "።", "፣", "፧"
    )
    data class Word(val ids: List<Int>, val insertionOffset: Int, val hasPunctuation: Boolean)
    data class Insertion(val offset: Int, val punctuation: String)
    private val words = Regex("\\S+")
    private val punctuationAndSymbols = Regex("[\\p{P}\\p{S}]")
    private val lexicalCharacter = Regex("[\\p{L}\\p{N}\\p{M}\\u200c\\u200d]")
    private const val TERMINATORS = ".,!?;:؟،؛。？！、，።፣፧।…"

    fun plan(text: String, encode: (String) -> List<Int>): List<List<Word>> {
        val chunks = mutableListOf<List<Word>>()
        var chunk = mutableListOf<Word>()
        var count = 0
        for (match in words.findAll(text)) {
            val source = match.value
            val last = lexicalCharacter.findAll(source).lastOrNull() ?: continue
            val end = last.range.last + 1
            val clean = source.replace(punctuationAndSymbols, "")
            val ids = encode(clean)
            if (ids.isEmpty()) continue
            if (count + ids.size > MAX_CONTENT_TOKENS) {
                if (chunk.isNotEmpty()) chunks.add(chunk)
                chunk = mutableListOf()
                count = 0
            }
            // An enormous single word cannot fit the model. Keep it verbatim and continue.
            if (ids.size > MAX_CONTENT_TOKENS) continue
            chunk.add(Word(ids, match.range.first + end, source.substring(end).any { it in TERMINATORS }))
            count += ids.size
        }
        if (chunk.isNotEmpty()) chunks.add(chunk)
        return chunks
    }

    fun insertions(words: List<Word>, postPredictions: IntArray): List<Insertion> {
        if (postPredictions.size < words.sumOf { it.ids.size } + 2) return emptyList()
        var index = 0
        return words.mapNotNull { word ->
            index += word.ids.size // +1 BOS means the last subword is at this index.
            val punctuation = postLabels.getOrNull(postPredictions[index]).orEmpty()
            if (word.hasPunctuation || punctuation.isEmpty()) null
            else Insertion(word.insertionOffset, punctuation)
        }
    }

    fun render(text: String, insertions: List<Insertion>): String {
        val out = StringBuilder(text)
        insertions.sortedByDescending { it.offset }.forEach { insertion ->
            if (insertion.offset in 0..text.length && insertion.punctuation in postLabels &&
                insertion.punctuation.isNotEmpty()) out.insert(insertion.offset, insertion.punctuation)
        }
        return out.toString()
    }
}
