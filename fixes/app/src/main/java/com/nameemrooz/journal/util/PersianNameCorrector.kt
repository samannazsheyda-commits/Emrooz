package com.nameemrooz.journal.util

import android.content.Context
import kotlin.math.abs

/**
 * Conservative proper-name correction for Persian ASR output.
 *
 * It only attempts surname correction immediately after a recognized first name,
 * never rewrites ordinary words globally, and uses a small edit-distance ceiling.
 */
class PersianNameCorrector internal constructor(
    firstNames: Set<String>,
    surnames: List<Pair<String, Int>>
) {
    private data class Surname(val value: String, val frequency: Int)

    private val firstNames = firstNames.map(::normalize).filter { it.isNotBlank() }.toHashSet()
    private val surnamesByName = surnames
        .asSequence()
        .map { normalize(it.first) to it.second }
        .filter { it.first.length >= 3 }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.maxOrNull() ?: 1 }

    private val surnameIndex: Map<Char, List<Surname>> = surnamesByName.entries
        .groupBy(
            keySelector = { it.key.first() },
            valueTransform = { Surname(it.key, it.value) }
        )

    constructor(context: Context) : this(
        firstNames = loadFirstNames(context),
        surnames = loadSurnames(context)
    )

    fun warmUp(): Int = firstNames.size + surnamesByName.size

    fun correct(input: String): String {
        if (input.isBlank() || firstNames.isEmpty() || surnamesByName.isEmpty()) return input

        val tokens = input.split(Regex("\\s+")).toMutableList()
        if (tokens.size < 2) return input

        for (i in 0 until tokens.lastIndex) {
            val first = normalize(stripPunctuation(tokens[i]))
            if (first !in firstNames) continue

            val originalSurnameToken = tokens[i + 1]
            val surname = normalize(stripPunctuation(originalSurnameToken))
            if (surname.length < 4 || surname in surnamesByName) continue

            val replacement = bestSurname(surname) ?: continue
            tokens[i + 1] = replaceCore(originalSurnameToken, stripPunctuation(originalSurnameToken), replacement)
        }

        return tokens.joinToString(" ")
    }

    private fun bestSurname(word: String): String? {
        val bucket = surnameIndex[word.first()].orEmpty()
        if (bucket.isEmpty()) return null

        val scored = bucket.asSequence()
            .filter { abs(it.value.length - word.length) <= 1 }
            .mapNotNull { candidate ->
                val distance = editDistanceAtMostTwo(word, candidate.value)
                if (distance > 2) null else candidate to distance
            }
            .toList()

        if (scored.isEmpty()) return null
        val bestDistance = scored.minOf { it.second }
        if (bestDistance == 0) return null

        val best = scored
            .filter { it.second == bestDistance }
            .sortedByDescending { it.first.frequency }

        if (bestDistance == 1) {
            // A one-edit miss is safe enough in a known-name context. Prefer frequency on ties.
            return best.first().first.value
        }

        // Two edits are only accepted for a very strong shape match and a unique best spelling.
        // This catches errors such as ممنوعی -> ممدوحی without becoming a general spell checker.
        if (word.length >= 6) {
            val strong = best.filter {
                val value = it.first.value
                value.length == word.length &&
                    value.take(2) == word.take(2) &&
                    value.last() == word.last()
            }
            if (strong.size == 1) return strong.first().first.value
        }

        return null
    }

    private fun editDistanceAtMostTwo(a: String, b: String): Int {
        if (abs(a.length - b.length) > 2) return 3
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost
                )
                rowMin = minOf(rowMin, current[j])
            }
            if (rowMin > 2) return 3
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private fun replaceCore(token: String, core: String, replacement: String): String {
        if (core.isBlank()) return token
        val at = token.indexOf(core)
        return if (at >= 0) token.replaceRange(at, at + core.length, replacement) else replacement
    }

    private companion object {
        private val trimChars = charArrayOf('،', ',', '.', '!', '؟', '?', ':', '؛', '…', '"', '\'', '«', '»', '(', ')')

        fun stripPunctuation(token: String): String = token.trim().trim(*trimChars)

        fun normalize(value: String): String = value
            .trim()
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')
            .replace('ة', 'ه')
            .replace(Regex("\\s+"), " ")

        fun loadFirstNames(context: Context): Set<String> = runCatching {
            context.assets.open("lexicon/persian_first_names.txt").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.map(::normalize).filter { it.isNotBlank() }.toSet()
            }
        }.getOrDefault(emptySet())

        fun loadSurnames(context: Context): List<Pair<String, Int>> = runCatching {
            context.assets.open("lexicon/persian_surnames.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.mapNotNull { line ->
                    val tab = line.lastIndexOf('\t')
                    if (tab <= 0) return@mapNotNull null
                    val name = normalize(line.substring(0, tab))
                    val frequency = line.substring(tab + 1).trim().toIntOrNull() ?: 1
                    if (name.isBlank()) null else name to frequency
                }.toList()
            }
        }.getOrDefault(emptyList())
    }
}
