package com.nameemrooz.journal.util

object PersianText {
    private val fillers = setOf(
        "اِ", "اِم", "اِمم", "ام", "امم", "اوم", "هوم", "همم", "ممم", "آآ", "اا", "مثلا", "مثلاً"
    )

    // Repetition can be intentional in Persian. Keep a small, conservative allow-list.
    private val intentionalRepeats = setOf("نه", "کم", "تک", "یواش", "آروم", "دونه", "قدم")

    // High-confidence corrections learned from recurring ASR confusions. These are
    // deliberately phrase-level so an ordinary word is never changed globally.
    private val knownTranscriptionFixes = linkedMapOf(
        "سهیل ممنوعی" to "سهیل ممدوحی",
        "سهیل ممدوهی" to "سهیل ممدوحی",
        "سهیل ممدو حی" to "سهیل ممدوحی"
    )

    private val phraseFixes = linkedMapOf(
        "میخوام" to "می‌خوام",
        "میخواد" to "می‌خواد",
        "میخوای" to "می‌خوای",
        "میخوایم" to "می‌خوایم",
        "میخوان" to "می‌خوان",
        "میشه" to "می‌شه",
        "میشود" to "می‌شود",
        "میشد" to "می‌شد",
        "میکنم" to "می‌کنم",
        "میکنی" to "می‌کنی",
        "میکنه" to "می‌کنه",
        "میکند" to "می‌کند",
        "میکنیم" to "می‌کنیم",
        "میکنن" to "می‌کنن",
        "میکنند" to "می‌کنند",
        "میرم" to "می‌رم",
        "میری" to "می‌ری",
        "میره" to "می‌ره",
        "میریم" to "می‌ریم",
        "میرن" to "می‌رن",
        "میام" to "میام",
        "میگه" to "می‌گه",
        "میگم" to "می‌گم",
        "میگی" to "می‌گی",
        "میدونم" to "می‌دونم",
        "نمیدونم" to "نمی‌دونم",
        "نمیشه" to "نمی‌شه",
        "نمیکنم" to "نمی‌کنم",
        "نمیکنی" to "نمی‌کنی",
        "نمیخوام" to "نمی‌خوام",
        "نمیخواد" to "نمی‌خواد",
        "نمیخوای" to "نمی‌خوای",
        "خونه" to "خونه",
        "اونجا" to "اونجا",
        "اینجا" to "اینجا"
    )

    private val spokenNumbers = linkedMapOf(
        "صفر" to "۰", "یک" to "۱", "دو" to "۲", "سه" to "۳", "چهار" to "۴",
        "پنج" to "۵", "شش" to "۶", "هفت" to "۷", "هشت" to "۸", "نه" to "۹", "ده" to "۱۰"
    )

    /**
     * Offline Persian cleanup for live speech transcription.
     * It fixes orthography, punctuation and obvious disfluencies while preserving meaning.
     * final=true enables phrase-level de-stuttering and sentence punctuation.
     */
    fun clean(input: String, final: Boolean = false): String {
        if (input.isBlank()) return ""

        var text = input
            .replace('ي', 'ی')
            .replace('ى', 'ی')
            .replace('ك', 'ک')
            .replace('ة', 'ه')
            .replace('ۀ', 'ه')
            .replace(Regex("[\\t\\u00A0]+"), " ")
            .replace(Regex(" +"), " ")
            .replace(Regex(" *\\n *"), "\n")
            .trim()

        // Remove obvious filler tokens but keep real discourse markers such as «خب».
        text = text.split(Regex("\\s+"))
            .filterNot { token -> normalizeToken(token) in fillers }
            .joinToString(" ")

        // Fix common colloquial orthography before repetition analysis.
        phraseFixes.forEach { (from, to) ->
            text = text.replace(Regex("(?<![\\p{L}])${Regex.escape(from)}(?![\\p{L}])"), to)
        }

        if (final) {
            knownTranscriptionFixes.forEach { (from, to) ->
                text = text.replace(from, to)
            }

            text = removeImmediateWordStutters(text)
            text = collapseRepeatedPhrases(text)
            text = addLightPunctuation(text)
        }

        text = normalizePunctuationSpacing(text)
        return text.trim()
    }

    private fun removeImmediateWordStutters(text: String): String {
        if (text.isBlank()) return text
        val tokens = text.split(Regex("\\s+")).toMutableList()
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

    /** Collapse immediately repeated 2–8 word phrases, a common voice false-start pattern. */
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

    private fun addLightPunctuation(input: String): String {
        var text = input.trim()
        if (text.isBlank()) return text

        // Safe sentence-initial discourse markers.
        val commaOpeners = listOf("راستی", "خب", "البته", "اتفاقاً", "اتفاقا")
        commaOpeners.forEach { opener ->
            text = text.replace(Regex("^${Regex.escape(opener)}\\s+(?![،,])"), "$opener، ")
        }

        // Spoken punctuation commands that are unambiguous.
        text = text
            .replace(Regex("\\s+(نقطه)\\s*"), ". ")
            .replace(Regex("\\s+(ویرگول|کاما)\\s*"), "، ")
            .replace(Regex("\\s+(علامت سوال|علامت سؤال)\\s*"), "؟ ")

        text = normalizePunctuationSpacing(text)

        if (!text.endsWith(".") && !text.endsWith("!") && !text.endsWith("؟") && !text.endsWith("…")) {
            val questionHints = listOf("چرا", "چطور", "چجوری", "کجا", "کی ", "چی", "مگه", "آیا")
            text += if (questionHints.any { hint -> text.contains(hint) }) "؟" else "."
        }
        return text
    }

    private fun normalizePunctuationSpacing(input: String): String = input
        .replace(Regex("\\s+([،,.!؟:؛])"), "$1")
        .replace(Regex("([،,:؛])(?=\\S)"), "$1 ")
        .replace(Regex("([.!؟]) +"), "$1 ")
        .replace(Regex(" +"), " ")

    private fun normalizeToken(token: String): String = token
        .trim()
        .trim('،', ',', '.', '!', '؟', '?', ':', '؛', '…', '"', '\'', '«', '»', '(', ')')
        .replace('ي', 'ی')
        .replace('ك', 'ک')
}
