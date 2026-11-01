package com.nameemrooz.journal.writing

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

        val candidate = if (!nanoEnabled || nano == null) {
            baseline
        } else {
            when (val result = nano.enhance(baseline, languageTag)) {
                is EnhancementResult.Applied -> result.text.takeIf { it.isNotBlank() } ?: baseline
                is EnhancementResult.Unavailable -> baseline
                is EnhancementResult.Failed -> baseline
            }
        }

        val edited = if (final) local.final(candidate, languageTag) else local.stable(candidate, languageTag)
        if (edited.isNotBlank()) return edited

        val fallback = if (final) local.final(baseline, languageTag) else local.stable(baseline, languageTag)
        return fallback.ifBlank { baseline }
    }
}
