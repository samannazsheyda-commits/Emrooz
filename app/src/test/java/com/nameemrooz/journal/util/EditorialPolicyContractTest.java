package com.nameemrooz.journal.util;

public final class EditorialPolicyContractTest {
    private static void expect(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + "\nexpected: " + expected + "\nactual:   " + actual);
        }
    }

    public static void main(String[] args) {
        expect("من می‌خوام برم.", PersianEditorial.clean("من میخوام برم", true), "half-space and final stop");
        expect("سلام، خوبی؟", PersianEditorial.clean("سلام , خوبی ?", true), "Persian punctuation");
        expect("امروز رفتم خونه.", PersianEditorial.clean("امروز اممم رفتم رفتم خونه", true), "filler and accidental repeat");
        expect("نه نه نه.", PersianEditorial.clean("نه نه نه", true), "meaningful short repeat");
        expect("با ChatGPT کار کردم.", PersianEditorial.clean("با ChatGPT کار کردم", true), "mixed script");
        expect("Node.js خوب بود؟", PersianEditorial.clean("Node.js خوب بود?", true), "technical token and question mark");
        expect("example.com/path را باز کردم.", PersianEditorial.clean("example.com/path را باز کردم", true), "URL token");
        expect("https://example.com?q=a&x=1 را باز کردم.", PersianEditorial.clean("https://example.com?q=a&x=1 را باز کردم", true), "URL query punctuation");
        expect("قیومی امروز آمد.", PersianEditorial.applyReplacements("غیومی امروز آمد.", java.util.Map.of("غیومی", "قیومی")), "private correction rule");
        System.out.println("Editorial policy contract: PASS");
    }
}
