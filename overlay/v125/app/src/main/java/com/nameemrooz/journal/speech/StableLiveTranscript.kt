package com.nameemrooz.journal.speech

/**
 * Holds back fast-changing streaming ASR guesses and exposes only text that has
 * remained a common prefix for roughly one second. Final transcription is not
 * filtered by this class.
 */
class StableLiveTranscript(
    private val minStableMs: Long = 850,
    private val windowMs: Long = 1100,
) {
    private data class Snapshot(val atMs: Long, val text: String)

    private val snapshots = ArrayDeque<Snapshot>()
    private var lastEmitted = ""

    fun offer(value: String, nowMs: Long): String? {
        val text = normalize(value)
        if (text.isBlank()) return null

        snapshots.addLast(Snapshot(nowMs, text))
        while (snapshots.size > 1 && snapshots.first().atMs < nowMs - windowMs) {
            snapshots.removeFirst()
        }

        if (snapshots.size < 3) return null
        if (nowMs - snapshots.first().atMs < minStableMs) return null

        val candidate = commonWordPrefix(snapshots.map { it.text })
        if (candidate.length < 4) return null
        if (candidate == lastEmitted) return null
        if (lastEmitted.isNotEmpty() && !candidate.startsWith(lastEmitted)) return null
        if (candidate.length <= lastEmitted.length) return null

        lastEmitted = candidate
        return candidate
    }

    fun reset() {
        snapshots.clear()
        lastEmitted = ""
    }

    private fun commonWordPrefix(values: List<String>): String {
        if (values.isEmpty()) return ""
        val words = values.map { it.split(' ').filter(String::isNotBlank) }
        val shortest = words.minOf { it.size }
        var count = 0
        while (count < shortest) {
            val expected = words[0][count]
            if (words.any { it[count] != expected }) break
            count++
        }
        return words[0].take(count).joinToString(" ")
    }

    private fun normalize(value: String): String = value
        .trim()
        .replace(Regex("\\s+"), " ")
}
