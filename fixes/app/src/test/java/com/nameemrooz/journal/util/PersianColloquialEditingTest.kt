package com.nameemrooz.journal.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianColloquialEditingTest {
    @Test
    fun normalizesCommonJoinedColloquialVerbs() {
        assertEquals(
            "من می‌رفتم خونه و می‌گفتم الان میام.",
            PersianText.clean("من میرفتم خونه و میگفتم الان میام", final = true)
        )
    }

    @Test
    fun normalizesNegativePastColloquialVerb() {
        assertEquals(
            "من نمی‌دونستم باید چی بگم.",
            PersianText.clean("من نمیدونستم باید چی بگم", final = true)
        )
    }

    @Test
    fun fixesFrequentAsrVariantOfWanted() {
        assertEquals(
            "من می‌خواستم برم بیرون.",
            PersianText.clean("من میخاستم برم بیرون", final = true)
        )
    }

    @Test
    fun normalizesComingAndUnderstandingForms() {
        assertEquals(
            "داشتم می‌اومدم ولی نمی‌فهمیدم چی شده.",
            PersianText.clean("داشتم میومدم ولی نمیفهمیدم چی شده", final = true)
        )
    }

    @Test
    fun doesNotRewriteOrdinaryWordsSemantically() {
        assertEquals(
            "این کار ممنوعی ندارد.",
            PersianText.clean("این کار ممنوعی ندارد", final = true)
        )
    }

    @Test
    fun normalizesCommonEverydayVerbsWithoutChangingMeaning() {
        assertEquals(
            "می‌بینم چی می‌شه بعد می‌ذارم کنار و می‌خوابم.",
            PersianText.clean("میبینم چی میشه بعد میذارم کنار و میخوابم", final = true)
        )
    }

    @Test
    fun normalizesPastAbilityAndActionForms() {
        assertEquals(
            "نمی‌تونستم کاری بکنم و نمی‌کردم.",
            PersianText.clean("نمیتونستم کاری بکنم و نمیکردم", final = true)
        )
    }
}
