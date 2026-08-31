package com.nameemrooz.journal.speech

/**
 * Compatibility implementation retained only for older regression tests/source.
 * v1.2.6 production uses LiveTranscriptStabilizer directly with a faster
 * two-hypothesis confirmation path.
 */
class StableLiveTranscript(
    private val minStableMs: Long = 280,
    private val windowMs: Long = 600,
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

        if (snapshots.size < MIN_CONFIRMING_SNAPSHOTS) return null
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

    private fun normalize(value: String): String = value.trim().replace(Regex("\\s+"), " ")

    private companion object {
        const val MIN_CONFIRMING_SNAPSHOTS = 3
    }
}
