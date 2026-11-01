package com.nameemrooz.journal.util

object TitleGenerator {
    private val topics = listOf(
        setOf("دختر", "پسر", "خانواده", "مادر", "مامان", "پدر", "بابا", "نوه") to "کنار خانواده",
        setOf("دوست", "رفیق", "دیدار", "دیدمش") to "یک دیدار خوب",
        setOf("پیاده", "پیاده‌روی", "قدم", "پارک") to "قدم‌های امروز",
        setOf("کتاب", "مطالعه", "خواندم", "خوندم") to "وقت کتاب",
        setOf("مهمانی", "مهمونی", "جشن", "تولد") to "یک روز پرماجرا",
        setOf("آرام", "استراحت", "خونه", "خانه") to "یک روز آرام"
    )

    fun generate(text: String): String {
        val normalized = PersianText.clean(text)
        topics.forEach { (keys, title) ->
            if (keys.any { normalized.contains(it) }) return title
        }
        return "قصه امروز من"
    }
}
