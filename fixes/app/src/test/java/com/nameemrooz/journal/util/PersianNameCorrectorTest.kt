package com.nameemrooz.journal.util

import org.junit.Assert.assertEquals
import org.junit.Test

class PersianNameCorrectorTest {
    private val corrector = PersianNameCorrector(
        firstNames = setOf("سهیل", "مهدی", "سارا"),
        surnames = listOf(
            "ممدوحی" to 900,
            "محمدی" to 5000,
            "محمودی" to 2500,
            "کریمی" to 4000
        )
    )

    @Test
    fun fixesSurnameOnlyWhenPrecededByKnownFirstName() {
        assertEquals("مهدی کریمی امروز آمد.", corrector.correct("مهدی کرمی امروز آمد."))
    }

    @Test
    fun canUseUniqueTwoEditSurnameMatchWithStrongShape() {
        assertEquals("سهیل ممدوحی آمد.", corrector.correct("سهیل ممنوعی آمد."))
    }

    @Test
    fun doesNotTouchOrdinaryWordsOutsideNameContext() {
        assertEquals("این کار ممنوعی ندارد.", corrector.correct("این کار ممنوعی ندارد."))
    }

    @Test
    fun doesNotReplaceAnAlreadyKnownSurname() {
        assertEquals("سارا محمدی آمد.", corrector.correct("سارا محمدی آمد."))
    }
}