package com.nameemrooz.journal.util

object PersianText {
    private val fillers = setOf(
        "اِ", "اِم", "اِمم", "ام", "امم", "اممم", "اوم", "هوم", "هومم", "همم", "ممم", "اا", "آآ", "آآآ", "اه", "اوه"
    )

    // Repetition can be intentional in Persian. Keep these common emphatic repetitions intact.
    private val intentionalRepeats = setOf("نه", "کم", "تک", "یواش", "آروم", "دونه", "قدم")

    // Phrase-level corrections are intentionally narrow so ordinary uses of a word are not rewritten.
    private val knownTranscriptionFixes = linkedMapOf(
        "سهیل ممنوعی" to "سهیل ممدوحی",
        "سهیل ممدوهی" to "سهیل ممدوحی",
        "سهیل ممدو حی" to "سهیل ممدوحی"
    )

    private val units = mapOf(
        "صفر" to 0L, "یک" to 1L, "دو" to 2L, "سه" to 3L, "چهار" to 4L,
        "پنج" to 5L, "شش" to 6L, "شیش" to 6L, "هفت" to 7L, "هشت" to 8L, "نه" to 9L
    )
    private val teens = mapOf(
        "ده" to 10L, "یازده" to 11L, "دوازده" to 12L, "سیزده" to 13L, "چهارده" to 14L,
        "پانزده" to 15L, "پونزده" to 15L, "شانزده" to 16L, "شونزده" to 16L,
        "هفده" to 17L, "هیفده" to 17L, "هجده" to 18L, "هیجده" to 18L, "نوزده" to 19L
    )
    private val tens = mapOf(
        "بیست" to 20L, "سی" to 30L, "چهل" to 40L, "پنجاه" to 50L, "شصت" to 60L,
        "هفتاد" to 70L, "هشتاد" to 80L, "نود" to 90L
    )
    private val hundreds = mapOf(
        "صد" to 100L, "یکصد" to 100L, "دویست" to 200L, "سیصد" to 300L, "چهارصد" to 400L,
        "پانصد" to 500L, "پونصد" to 500L, "ششصد" to 600L, "شیشصد" to 600L,
        "هفتصد" to 700L, "هشتصد" to 800L, "نهصد" to 900L
    )
    private val scales = mapOf("هزار" to 1_000L, "میلیون" to 1_000_000L, "میلیارد" to 1_000_000_000L)
    private val persianDigits = "۰۱۲۳۴۵۶۷۸۹"
    private val numberTrailingPunctuation = setOf('،', '؛', ':', '؟', '!', '.')

    private val phraseFixes = listOf(
        "دلت خوش آ" to "دلت خوشه آ",
        "نمی خواستم" to "نمی‌خواستم", "نمیخواستم" to "نمی‌خواستم",
        "می خواستم" to "می‌خواستم", "میخواستم" to "می‌خواستم",
        "نمی دونم" to "نمی‌دونم", "نمیدونم" to "نمی‌دونم",
        "می دونم" to "می‌دونم", "میدونم" to "می‌دونم",
        "نمی تونم" to "نمی‌تونم", "نمیتونم" to "نمی‌تونم",
        "می تونم" to "می‌تونم", "میتونم" to "می‌تونم",
        "نمی شه" to "نمی‌شه", "نمیشه" to "نمی‌شه",
        "می شه" to "می‌شه", "میشه" to "می‌شه",
        "می خوام" to "می‌خوام", "میخوام" to "می‌خوام",
        "می خوای" to "می‌خوای", "میخوای" to "می‌خوای",
        "می خواد" to "می‌خواد", "میخواد" to "می‌خواد",
        "می خوان" to "می‌خوان", "میخوان" to "می‌خوان",
        "نمی خوام" to "نمی‌خوام", "نمیخوام" to "نمی‌خوام",
        "نمی خوای" to "نمی‌خوای", "نمیخوای" to "نمی‌خوای",
        "نمی کنم" to "نمی‌کنم", "نمیکنم" to "نمی‌کنم",
        "می کنم" to "می‌کنم", "میکنم" to "می‌کنم",
        "می کنی" to "می‌کنی", "میکنی" to "می‌کنی",
        "نمی کنه" to "نمی‌کنه", "نمیکنه" to "نمی‌کنه",
        "می کنه" to "می‌کنه", "میکنه" to "می‌کنه",
        "می کند" to "می‌کند", "میکند" to "می‌کند",
        "می کنیم" to "می‌کنیم", "میکنیم" to "می‌کنیم",
        "می کنند" to "می‌کنند", "میکنند" to "می‌کنند",
        "می رم" to "می‌رم", "میرم" to "می‌رم",
        "می ری" to "می‌ری", "میری" to "می‌ری",
        "می ره" to "می‌ره", "میره" to "می‌ره",
        "می ریم" to "می‌ریم", "میریم" to "می‌ریم",
        "می رن" to "می‌رن", "میرن" to "می‌رن",
        "می گه" to "می‌گه", "میگه" to "می‌گه",
        "می گم" to "می‌گم", "میگم" to "می‌گم",
        "می گی" to "می‌گی", "میگی" to "می‌گی"
    )

    private val spokenPunctuationFixes = listOf(
        Regex("\\s+علامت\\s+س[ؤو]ال\\s+") to "؟ ",
        Regex("\\s+سه\\s+نقطه\\s+") to "... ",
        Regex("\\s+دو\\s+نقطه\\s+") to ": ",
        Regex("\\s+(?:ویرگول|کاما)\\s+") to "، ",
        Regex("\\s+نقطه\\s+") to ". "
    )

    private val openingCommaWords = listOf("راستی", "خب", "البته", "اتفاقاً", "اتفاقا")
    private val questionHints = listOf(
        "چرا", "چطور", "چجوری", "کجا", "کی ", "چی", "چه ", "چقدر", "چند", "مگه", "آیا",
        "میای", "می‌آی", "می‌خوای", "میخوای", "می‌تونی", "میتونی", "هستی"
    )

    /**
     * Offline cleanup for Persian speech transcription.
     * Live text gets spelling/orthography cleanup; the final pass additionally removes
     * obvious false starts/repetitions and restores light punctuation without paraphrasing.
     */
    fun clean(input: String, final: Boolean = false): String {
        if (input.isBlank()) return ""

        var text = input
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')
            .replace('ة', 'ه')
            .replace('ۀ', 'ه')
            .replace('\u200e', ' ')
            .replace('\u200f', ' ')
            .replace('\u00A0', ' ')
            .replace(Regex("[\\t\\n\\r]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        phraseFixes.forEach { (from, to) -> text = text.replace(from, to, ignoreCase = false) }

        text = text
            .replace(',', '،')
            .replace('?', '؟')
            .replace(';', '؛')
            .replace(Regex("\\s+([،؛:؟!.])"), "$1")

        text = removeFillers(text)
        text = normalizeSpokenNumbers(text)

        if (final && text.isNotBlank()) {
            knownTranscriptionFixes.forEach { (from, to) -> text = text.replace(from, to) }
            text = removeImmediateWordStutters(text)
            text = collapseRepeatedPhrases(text)
            text = restoreFinalPunctuation(text)
        }

        return normalizePunctuationSpacing(text).trim()
    }

    private fun removeFillers(text: String): String = text
        .split(Regex("\\s+"))
        .filter { token ->
            val normalized = normalizeToken(token)
            normalized.isNotBlank() && normalized !in fillers
        }
        .joinToString(" ")

    private fun removeImmediateWordStutters(text: String): String {
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        var i = 1
        while (i < tokens.size) {
            val previous = normalizeToken(tokens[i - 1])
            val current = normalizeToken(tokens[i])
            if (previous.isNotBlank() && previous == current && previous !in intentionalRepeats) {
                tokens.removeAt(i)
            } else {
                i++
            }
        }
        return tokens.joinToString(" ")
    }

    /** Collapse immediately repeated 2–8 word spans, a common voice false-start pattern. */
    private fun collapseRepeatedPhrases(text: String): String {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.toMutableList()
        if (words.size < 4) return words.joinToString(" ")

        var changed = true
        while (changed) {
            changed = false
            outer@ for (start in words.indices) {
                val maxSpan = minOf(8, (words.size - start) / 2)
                for (span in maxSpan downTo 2) {
                    val first = (0 until span).map { normalizeToken(words[start + it]) }
                    val second = (0 until span).map { normalizeToken(words[start + span + it]) }
                    if (first == second && first.any { it.isNotBlank() }) {
                        repeat(span) { words.removeAt(start + span) }
                        changed = true
                        break@outer
                    }
                }
            }
        }
        return words.joinToString(" ")
    }

    private fun restoreFinalPunctuation(input: String): String {
        var text = " ${input.trim()} "
        spokenPunctuationFixes.forEach { (pattern, replacement) -> text = text.replace(pattern, replacement) }
        text = normalizePunctuationSpacing(text.trim())

        openingCommaWords.forEach { word ->
            if (text != word && text.startsWith("$word ") && !text.startsWith("$word،")) {
                text = "$word، " + text.removePrefix("$word ")
            }
        }

        if (text.isNotBlank() && text.last() !in charArrayOf('.', '!', '؟')) {
            val looksLikeQuestion = questionHints.any { hint -> text.contains(hint) }
            text += if (looksLikeQuestion) "؟" else "."
        }
        return text
    }

    private fun numberValue(word: String): Long? = units[word] ?: teens[word] ?: tens[word] ?: hundreds[word]

    private fun toPersianDigits(value: Long): String = value.toString().map { persianDigits[it - '0'] }.joinToString("")

    private fun splitNumberToken(token: String): Pair<String, String> {
        var cut = token.length
        while (cut > 0 && token[cut - 1] in numberTrailingPunctuation) cut--
        return token.substring(0, cut) to token.substring(cut)
    }

    private fun normalizeSpokenNumbers(text: String): String {
        if (text.isBlank()) return text
        val tokens = text.split(' ')
        val out = ArrayList<String>(tokens.size)
        var i = 0
        while (i < tokens.size) {
            val (firstCore, _) = splitNumberToken(tokens[i])

            // «نه» is far more often Persian negation than the digit 9. Only treat it as
            // a number when it begins a scaled number such as «نه هزار». It is still
            // consumed normally inside sequences such as «بیست و نه» because those begin
            // parsing from the earlier numeric word.
            if (firstCore == "نه") {
                val nextCore = tokens.getOrNull(i + 1)?.let { splitNumberToken(it).first }
                if (nextCore !in scales) {
                    out += tokens[i]
                    i++
                    continue
                }
            }

            if (numberValue(firstCore) == null && firstCore !in scales) {
                out += tokens[i]
                i++
                continue
            }

            var total = 0L
            var current = 0L
            var j = i
            var consumedNumber = false
            var suffix = ""
            while (j < tokens.size) {
                val (word, trailing) = splitNumberToken(tokens[j])
                if (word == "و") {
                    if (trailing.isNotEmpty() || !consumedNumber || j + 1 >= tokens.size) break
                    val (next, _) = splitNumberToken(tokens[j + 1])
                    if (numberValue(next) == null && next !in scales) break
                    j++
                    continue
                }

                val scale = scales[word]
                val value = numberValue(word)
                when {
                    scale != null -> {
                        total += (if (current == 0L) 1L else current) * scale
                        current = 0L
                        consumedNumber = true
                    }
                    value != null -> {
                        current += value
                        consumedNumber = true
                    }
                    else -> break
                }
                j++
                if (trailing.isNotEmpty()) {
                    suffix = trailing
                    break
                }
            }

            if (consumedNumber) {
                out += toPersianDigits(total + current) + suffix
                i = j
            } else {
                out += tokens[i]
                i++
            }
        }
        return out.joinToString(" ")
    }

    private fun normalizePunctuationSpacing(input: String): String = input
        .replace(',', '،')
        .replace('?', '؟')
        .replace(';', '؛')
        .replace(Regex("\\s+([،؛:؟!.])"), "$1")
        .replace(Regex("([،؛:؟!.])(?=\\S)"), "$1 ")
        .replace(Regex("\\s+"), " ")

    private fun normalizeToken(token: String): String = token
        .trim()
        .trim('،', ',', '.', '!', '؟', '?', ':', '؛', ';', '…', '"', '\'', '«', '»', '(', ')')
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
}
