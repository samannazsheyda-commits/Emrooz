package com.nameemrooz.journal.speech

import org.junit.Assert.*
import org.junit.Test

class WhisperAudioSegmenterTest {
    @Test fun silenceAndVeryShortNoiseNeverBecomeUtterances() {
        val result = mutableListOf<ShortArray>()
        val segmenter = WhisperAudioSegmenter()
        segmenter.append(ShortArray(16000), emit = result::add)
        segmenter.append(ShortArray(640) { 4000 }, emit = result::add)
        segmenter.append(ShortArray(16000), emit = result::add)
        segmenter.finish(result::add)
        assertTrue(result.isEmpty())
    }

    @Test fun twoHundredMillisecondWordIsRetainedOnPause() {
        val result = mutableListOf<ShortArray>()
        val segmenter = WhisperAudioSegmenter()
        val shortWord = ShortArray(3200) { 5000 }
        segmenter.append(shortWord, emit = result::add)
        segmenter.append(ShortArray(16000), emit = result::add)
        assertEquals(1, result.size)
        assertEquals(shortWord.size, result.single().count { it == 5000.toShort() })
        segmenter.finish(result::add)
        assertEquals(1, result.size)
    }

    @Test fun twoHundredMillisecondWordIsRetainedOnExplicitStop() {
        val result = mutableListOf<ShortArray>()
        val segmenter = WhisperAudioSegmenter()
        val shortWord = ShortArray(3200) { 5000 }
        segmenter.append(shortWord, emit = result::add)
        segmenter.finish(result::add)
        assertEquals(1, result.size)
        assertArrayEquals(shortWord, result.single())
    }

    @Test fun quietTwoHundredMillisecondWordIsRetainedOnPauseAndStop() {
        for (pauseBeforeStop in listOf(false, true)) {
            val segmenter = WhisperAudioSegmenter()
            val result = mutableListOf<ShortArray>()
            // RMS 0.00061: quiet speech must not be treated as digital silence.
            val quietWord = ShortArray(3200) { 20 }
            segmenter.append(quietWord, emit = result::add)
            if (pauseBeforeStop) segmenter.append(ShortArray(16000), emit = result::add)
            segmenter.finish(result::add)
            assertEquals(1, result.size)
            assertEquals(quietWord.size, result.single().count { it == 20.toShort() })
        }
    }

    @Test fun longSpeechKeepsEverySampleIncludingShortFinalTailWithoutOverlap() {
        val input = ShortArray(8 * 16000 + 960) { (4000 + it % 200).toShort() }
        val result = mutableListOf<ShortArray>()
        val segmenter = WhisperAudioSegmenter()
        for (frame in input.asList().chunked(320)) {
            segmenter.append(frame.toShortArray(), emit = result::add)
        }
        segmenter.finish(result::add)
        assertEquals(listOf(128000, 960), result.map { it.size })
        assertArrayEquals(input, result.flatMap { it.asList() }.toShortArray())
        assertTrue(result.all { it.size <= 8 * 16000 })
    }

    @Test fun pauseSeparatesUtterancesAndResetErasesPriorPreroll() {
        val segmenter = WhisperAudioSegmenter()
        val result = mutableListOf<ShortArray>()
        segmenter.append(ShortArray(6400) { 5000 }, emit = result::add)
        segmenter.append(ShortArray(16000), emit = result::add)
        segmenter.append(ShortArray(6400) { 6000 }, emit = result::add)
        segmenter.finish(result::add)
        assertEquals(2, result.size)
        assertEquals(6400, result[0].count { it == 5000.toShort() })
        assertEquals(6400, result[1].count { it == 6000.toShort() })
        assertFalse(result[1].any { it == 5000.toShort() })
        result.clear()
        segmenter.append(ShortArray(6400) { 7000 }, emit = result::add)
        segmenter.reset()
        segmenter.append(ShortArray(6400) { 8000 }, emit = result::add)
        segmenter.finish(result::add)
        assertEquals(1, result.size)
        assertTrue(result[0].all { it == 8000.toShort() })
    }

    @Test fun chunkIdsPreventRetriesWhilePreservingIntentionalRepeatedWords() {
        val transcript = WhisperTranscript()
        assertEquals("سلام", transcript.append(0, "  سلام  "))
        assertEquals("سلام", transcript.append(0, "سلام"))
        assertEquals("سلام سلام", transcript.append(1, "سلام"))
        assertEquals("سلام سلام", transcript.append(0, "قدیمی"))
        transcript.reset()
        assertEquals("شروع", transcript.append(0, "شروع"))
    }
}
