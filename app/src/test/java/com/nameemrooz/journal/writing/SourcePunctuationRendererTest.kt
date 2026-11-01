package com.nameemrooz.journal.writing

import org.junit.Assert.*
import org.junit.Test

class SourcePunctuationRendererTest {
    private fun plan(text: String) = SourcePunctuationRenderer.plan(text) { listOf(5, 9) }
    private fun punctuate(text: String): String {
        val insertions = plan(text).flatMap { chunk ->
            val labels = IntArray(chunk.sumOf { it.ids.size } + 2)
            var i = 0
            chunk.forEach { i += it.ids.size; labels[i] = 10 }
            SourcePunctuationRenderer.insertions(chunk, labels)
        }
        return SourcePunctuationRenderer.render(text, insertions)
    }

    @Test fun preservesNamesNumbersNegationAndWhitespace() {
        val source = "  میثم\tنمی‌آیم\n۱۲۳  "
        assertEquals("  میثم؟\tنمی‌آیم؟\n۱۲۳؟  ", punctuate(source))
        assertTrue(LexicalSafety.isPunctuationOnlyChange(source, punctuate(source)))
    }

    @Test fun preservesQuotesAndExistingPunctuation() {
        assertEquals("«میثم؟» گفت: نمی‌آیم؟", punctuate("«میثم» گفت: نمی‌آیم"))
        assertEquals("سلام؟\nخوبی،", punctuate("سلام؟\nخوبی،"))
    }

    @Test fun processesBeyond126TokensWithoutDroppingTail() {
        val source = (1..150).joinToString("\n") { "میثم" } + "  پایان"
        val result = punctuate(source)
        assertEquals(3, plan(source).size)
        assertEquals(source, result.replace("؟", ""))
        assertTrue(result.endsWith("پایان؟"))
    }

    @Test fun ignoresBosEosAndMarkerOnlyPredictions() {
        val chunk = plan("سلام").single()
        assertEquals(emptyList<SourcePunctuationRenderer.Insertion>(),
            SourcePunctuationRenderer.insertions(chunk, intArrayOf(10, 10, 0, 10)))
    }

    @Test fun keepsOversizedWordAndContinues() {
        val chunks = SourcePunctuationRenderer.plan("بسیارطولانی\tمیثم") {
            if (it == "بسیارطولانی") List(127) { 4 } else listOf(9)
        }
        val edits = SourcePunctuationRenderer.insertions(chunks.single(), intArrayOf(0, 1, 0))
        assertEquals("بسیارطولانی\tمیثم.", SourcePunctuationRenderer.render("بسیارطولانی\tمیثم", edits))
    }

    @Test fun preservesSourceWithCombiningMarksEmojiAndMixedScripts() {
        val source = "\n  مِیثَم\tنمی‌آید 😄  Emrooz\r\n۱۲۵٫۵۰  "
        assertEquals("\n  مِیثَم؟\tنمی‌آید؟ 😄  Emrooz؟\r\n۱۲۵٫۵۰؟  ", punctuate(source))
    }

    @Test fun rejectsUnknownAndIncompletePredictions() {
        val chunk = plan("میثم").single()
        assertTrue(SourcePunctuationRenderer.insertions(chunk, intArrayOf(0, 0, 99, 0)).isEmpty())
        assertTrue(SourcePunctuationRenderer.insertions(chunk, intArrayOf(0, 10)).isEmpty())
    }
}
