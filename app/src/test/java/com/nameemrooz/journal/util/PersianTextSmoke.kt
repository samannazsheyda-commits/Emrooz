package com.nameemrooz.journal.util

fun main() {
    fun checkEq(actual: String, expected: String, label: String) {
        require(actual == expected) { "$label\nexpected: $expected\nactual:   $actual" }
    }
    checkEq(PersianText.clean("امروز اممم رفتم رفتم خونه"), "امروز رفتم خونه", "filler/repeat")
    checkEq(PersianText.clean("من میخوام برم"), "من می‌خوام برم", "half-space")
    checkEq(PersianText.clean("كیف يادم بود"), "کیف یادم بود", "arabic chars")
    checkEq(PersianText.clean("سلام   ،   خوبی ؟"), "سلام، خوبی؟", "punctuation spacing")
    checkEq(PersianText.clean("امروز خوب بود", final = true), "امروز خوب بود.", "final punctuation")
    checkEq(TitleGenerator.generate("امروز با دخترم و نوه‌ام بودم"), "کنار خانواده", "family title")
    checkEq(TitleGenerator.generate("سلام"), "قصه امروز من", "fallback title")
    println("OK")
}
