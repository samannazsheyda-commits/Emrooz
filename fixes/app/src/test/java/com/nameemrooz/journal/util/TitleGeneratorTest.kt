package com.nameemrooz.journal.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TitleGeneratorTest {
    @Test
    fun meetingWithNamedPersonUsesPersonInTitle() {
        assertEquals(
            "دیدار امروز با سهیل",
            TitleGenerator.generate("امروز با سهیل دیدار کردم و خیلی با هم صحبت کردیم")
        )
    }

    @Test
    fun familyDayGetsFamilyTitle() {
        assertEquals(
            "امروز کنار خانواده",
            TitleGenerator.generate("امروز بیشتر وقتم را با مامان و بابا در خانه گذراندم")
        )
    }

    @Test
    fun fallbackStillComesFromTranscript() {
        val title = TitleGenerator.generate("صبح باران آمد و بعد چای خوردم")
        assertNotEquals("قصه امروز من", title)
        assertEquals("صبح باران و چای", title)
    }

    @Test
    fun userEditedTitleAlwaysWins() {
        assertEquals(
            "قرار مهم من",
            TitleGenerator.resolve("  قرار مهم من  ", "امروز با سهیل دیدار کردم")
        )
    }

    @Test
    fun blankUserTitleFallsBackToGeneratedTitle() {
        assertEquals(
            "دیدار امروز با سهیل",
            TitleGenerator.resolve("   ", "امروز با سهیل دیدار کردم")
        )
    }
}