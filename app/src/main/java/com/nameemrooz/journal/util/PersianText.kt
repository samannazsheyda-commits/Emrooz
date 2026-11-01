package com.nameemrooz.journal.util

object PersianText {
    private val fillers = setOf(
        "اِم", "اِمم", "ام", "امم", "اممم", "اا", "آآ", "آآآ", "اه", "هوم", "هومم", "اوه"
    )

    private val phraseFixes = listOf(
        "نمی خواستم" to "نمی‌خواستم", "نمیخواستم" to "نمی‌خواستم",
        "می خواستم" to "می‌خواستم", "میخواستم" to "می‌خواستم",
        "نمی دونم" to "نمی‌دونم", "نمیدونم" to "نمی‌دونم",
        "می دونم" to "می‌دونم", "میدونم" to "می‌دونم",
        "نمی تونم" to "نمی‌تونم", "نمیتونم" to "نمی‌تونم",
        "می تونم" to "می‌تونم", "میتونم" to "می‌تونم",
        "نمی شه" to "نمی‌شه", "نمیشه" to "نمی‌شه",
        "می شه" to "می‌شه", "میشه" to "می‌شه",
        "می خوام" to "می‌خوام", "میخوام" to "می‌خوام",
        "نمی کنم" to "نمی‌کنم", "نمیکنم" to "نمی‌کنم",
        "می کنم" to "می‌کنم", "میکنم" to "می‌کنم",
        "نمی کنه" to "نمی‌کنه", "نمیکنه" to "نمی‌کنه",
        "می کنه" to "می‌کنه", "میکنه" to "می‌کنه",
        "می رم" to "می‌رم", "میرم" to "می‌رم"
    )

    /** Conservative cleanup: orthography + obvious disfluencies only. Never paraphrases. */
    fun clean(input: String, final: Boolean = false): String {
        return PersianEditorial.clean(input, final)
    }
}
