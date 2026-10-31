package com.nameemrooz.journal.speech;

import java.util.Map;

public final class StableTranscriptContractTest {
    private static void expect(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + "\nexpected: " + expected + "\nactual:   " + actual);
        }
    }

    public static void main(String[] args) {
        StableTranscriptMerger merger = new StableTranscriptMerger();
        expect("امروز", merger.updatePartial("امروز"), "first partial");
        expect("امروز صبح", merger.updatePartial("امروز صبح"), "partial extension");
        expect("امروز صبح خوب", merger.updatePartial("امروز صبح خوب"), "small unstable revision");
        expect("امروز صبح خوب بود.", merger.commit("امروز صبح خوب بود."), "commit current utterance");
        expect("امروز صبح خوب بود. پیاده‌روی کردم", merger.updatePartial("پیاده‌روی کردم"), "next utterance");
        expect("امروز صبح خوب بود. پیاده‌روی کردم.", merger.commit("پیاده‌روی کردم."), "second commit");

        StableTranscriptMerger overlap = new StableTranscriptMerger();
        overlap.commit("امروز رفتم بازار");
        expect("امروز رفتم بازار و خرید کردم", overlap.commit("بازار و خرید کردم"), "boundary overlap");

        PersonalVocabulary vocabulary = new PersonalVocabulary(Map.of("غیومی", "قیومی", "واتساپ", "واتس‌اپ"));
        expect("آقای قیومی در واتس‌اپ پیام داد.", vocabulary.apply("آقای غیومی در واتساپ پیام داد."), "private vocabulary");
        expect("قیومی", vocabulary.preferredFor("  غیومی  "), "trim lookup");
        System.out.println("Stable transcript contract: PASS");
    }
}
