package com.nameemrooz.journal.util

object TitleGenerator {
    private val meetingCues = setOf("دیدار", "دیدم", "دیدمش", "ملاقات", "صحبت", "گپ")
    private val familyCues = setOf("خانواده", "مامان", "مادر", "بابا", "پدر", "نوه", "دختر", "پسر")
    private val walkCues = setOf("پیاده", "پیاده‌روی", "قدم", "پارک")
    private val bookCues = setOf("کتاب", "مطالعه", "خواندم", "خوندم")
    private val homeCues = setOf("خانه", "خونه")
    private val workCues = setOf("کار", "اداره", "جلسه", "همکار")
    private val shoppingCues = setOf("خرید", "بازار", "فروشگاه")

    private val stopWords = setOf(
        "امروز", "من", "ما", "و", "که", "را", "رو", "به", "از", "در", "با", "برای", "این", "اون", "آن",
        "بعد", "بعدش", "خیلی", "یک", "یه", "هم", "بود", "بودم", "شد", "کردم", "کردیم", "گفتم", "گفتیم",
        "رفتم", "رفتیم", "اومدم", "آمدم", "آمد", "خوردم", "داشت", "داشتم", "بیشتر", "وقتم", "گذراندم"
    )

    fun generate(text: String): String {
        val normalized = PersianText.clean(text).trim().trimEnd('.', '!', '؟')
        if (normalized.isBlank()) return "نامه امروز"
        val words = normalized.split(Regex("\\s+")).map { cleanToken(it) }.filter { it.isNotBlank() }

        val person = personAfterWith(words)
        if (person != null && meetingCues.any { cue -> normalized.contains(cue) }) {
            return "دیدار امروز با $person"
        }

        if (familyCues.any { cue -> normalized.contains(cue) }) return "امروز کنار خانواده"
        if (walkCues.any { cue -> normalized.contains(cue) }) return "قدم‌های امروز"
        if (bookCues.any { cue -> normalized.contains(cue) }) return "وقت کتاب امروز"
        if (workCues.any { cue -> normalized.contains(cue) }) return "امروز و کار"
        if (shoppingCues.any { cue -> normalized.contains(cue) }) return "خرید امروز"
        if (homeCues.any { cue -> normalized.contains(cue) }) return "امروز در خانه"

        return fallback(words)
    }

    /** A manually edited non-blank title is authoritative; only blank titles are generated. */
    fun resolve(userTitle: String, text: String): String =
        userTitle.trim().takeIf { it.isNotBlank() } ?: generate(text)

    private fun personAfterWith(words: List<String>): String? {
        for (i in 0 until words.lastIndex) {
            if (words[i] != "با") continue
            val candidate = words[i + 1]
            if (candidate.length >= 2 && candidate !in stopWords) return candidate
        }
        return null
    }

    private fun fallback(words: List<String>): String {
        val meaningful = words.filter { word ->
            word.length >= 2 && word !in stopWords && !word.all { it.isDigit() }
        }.distinct()

        if (meaningful.isEmpty()) return "نامه امروز"
        if (meaningful.size == 1) return meaningful.first()
        if (meaningful.size == 2) return meaningful.joinToString(" و ")

        // Prefer a natural compact Persian title: first meaningful scene word + two salient words.
        return listOf(meaningful[0], meaningful[1], "و", meaningful[2]).joinToString(" ")
    }

    private fun cleanToken(token: String): String = token
        .trim('،', '؛', ':', '؟', '!', '.', '…', '«', '»', '"', '\'', '(', ')')
}