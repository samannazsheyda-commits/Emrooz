package com.nameemrooz.journal.writing

import kotlinx.coroutines.CancellationException

class WritingEnhancementCoordinator(
    private val local: LocalWritingEnhancer,
    private val nano: WritingEnhancer?,
    private val nanoEnabled: Boolean,
) {
    suspend fun stable(text: String, languageTag: String): String =
        finish(text, languageTag, final = false)

    suspend fun final(text: String, languageTag: String): String =
        finish(text, languageTag, final = true)

    private suspend fun finish(text: String, languageTag: String, final: Boolean): String {
        val baseline = local.live(text, languageTag)
        if (baseline.isBlank()) return baseline

        // Completed pages remain untouched; inference cost is bounded for long recordings.
        val start = editingTailStart(baseline)
        if (start == baseline.length) return baseline
        val prefix = baseline.substring(0, start)
        val recent = baseline.substring(start)
        val candidate = if (!nanoEnabled || nano == null) {
            recent
        } else {
            when (val result = try { nano.enhance(recent, languageTag) } catch (e: CancellationException) { throw e } catch (_: Exception) { EnhancementResult.Failed("provider_error") }) {
                is EnhancementResult.Applied -> result.text.takeIf { it.isNotBlank() && it.length <= MAX_EDIT_CHARS && words.findAll(it).take(MAX_EDIT_WORDS + 1).count() <= MAX_EDIT_WORDS } ?: recent
                is EnhancementResult.Unavailable -> recent
                is EnhancementResult.Failed -> recent
            }
        }

        val edited = if (final) local.final(candidate, languageTag) else local.stable(candidate, languageTag)
        if (edited.isNotBlank()) return prefix + edited

        val fallback = if (final) local.final(recent, languageTag) else local.stable(recent, languageTag)
        return prefix + fallback.ifBlank { recent }
    }
    private fun editingTailStart(text: String): Int {
        val matches = words.findAll(text).toList()
        var start = text.length
        for (word in matches.asReversed().take(MAX_EDIT_WORDS)) {
            if (text.length - word.range.first > MAX_EDIT_CHARS) break
            start = word.range.first
        }
        return start
    }

    private companion object {
        const val MAX_EDIT_CHARS = 1200
        const val MAX_EDIT_WORDS = 80
        val words = Regex("\\S+")
    }
}
