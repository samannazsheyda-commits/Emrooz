package com.nameemrooz.journal.speech;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class StableTranscriptJUnitTest {
    @Test public void overlappingWindowsDoNotDuplicateBoundaryWords() {
        StableTranscriptMerger merger = new StableTranscriptMerger();
        assertEquals("امروز رفتم بازار", merger.commit("امروز رفتم بازار"));
        assertEquals("امروز رفتم بازار و خرید کردم", merger.commit("بازار و خرید کردم"));
    }

    @Test public void interruptedPartialIsCommittedBeforeTheNextProviderCycle() {
        StableTranscriptMerger merger = new StableTranscriptMerger();
        merger.updatePartial("بخش نخست پیش از توقف");
        assertEquals("بخش نخست پیش از توقف", merger.commit(""));
        assertEquals("بخش نخست پیش از توقف بخش دوم پس از ادامه", merger.updatePartial("بخش دوم پس از ادامه"));
        assertEquals("بخش نخست پیش از توقف بخش دوم پس از ادامه", merger.commit(""));
    }

    @Test public void resetStartsFreshSession() {
        StableTranscriptMerger merger = new StableTranscriptMerger();
        merger.commit("نامه قبلی");
        merger.reset();
        assertEquals("نامه جدید", merger.commit("نامه جدید"));
    }
}
