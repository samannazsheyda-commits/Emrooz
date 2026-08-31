package com.nameemrooz.journal.speech

/**
 * Exposes only a monotonic word prefix that is confirmed by consecutive ASR
 * hypotheses. This avoids showing one-off garbage without imposing the old
 * fixed 850 ms / three-snapshot delay.
 */
class LiveTranscriptStabilizer(
    private val minAgreementMs: Long = 280,
) {
    private var previous = ""
    private var previousAtMs = 0L
    private var committed = ""

    fun offer(value: String, nowMs: Long): String? {
        val text = normalize(value)
        if (text.isBlank()) return null

        if (previous.isBlank()) {
            previous = text
            previousAtMs = nowMs
            return null
        }

        val elapsed = nowMs - previousAtMs
        val prefix = commonWordPrefix(previous, text)
        previous = text
        previousAtMs = nowMs

        if (elapsed < minAgreementMs) return null
        if (prefix.length < 4) return null
        if (prefix.length <= committed.length) return null
        if (committed.isNotEmpty() && !prefix.startsWith(committed)) return null

        committed = prefix
        return committed
    }

    fun bestStableOr(value: String): String {
        return if (committed.isNotBlank()) committed else normalize(value)
    }

    fun reset() {
        previous = ""
        previousAtMs = 0L
        committed = ""
    }

    private fun commonWordPrefix(a: String, b: String): String {
        val aw = a.split(' ').filter(String::isNotBlank)
        val bw = b.split(' ').filter(String::isNotBlank)
        val n = minOf(aw.size, bw.size)
        var count = 0
        while (count < n && aw[count] == bw[count]) count++
        return aw.take(count).joinToString(" ")
    }

    private fun normalize(value: String): String = value
        .trim()
        .replace(Regex("\\s+"), " ")
}
