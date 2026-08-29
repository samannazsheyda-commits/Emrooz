from pathlib import Path

path = Path("app/src/main/java/com/nameemrooz/journal/util/PersianText.kt")
text = path.read_text(encoding="utf-8")

old = '''        "می گم" to "می‌گم", "میگم" to "می‌گم",
        "می گی" to "می‌گی", "میگی" to "می‌گی"
'''
new = '''        "می گم" to "می‌گم", "میگم" to "می‌گم",
        "می گی" to "می‌گی", "میگی" to "می‌گی",
        "می رفتم" to "می‌رفتم", "میرفتم" to "می‌رفتم",
        "می گفتم" to "می‌گفتم", "میگفتم" to "می‌گفتم",
        "نمی دونستم" to "نمی‌دونستم", "نمیدونستم" to "نمی‌دونستم",
        "می اومدم" to "می‌اومدم", "میومدم" to "می‌اومدم",
        "می خاستم" to "می‌خواستم", "میخاستم" to "می‌خواستم",
        "نمی فهمیدم" to "نمی‌فهمیدم", "نمیفهمیدم" to "نمی‌فهمیدم"
'''

count = text.count(old)
if count != 1:
    raise SystemExit(f"colloquial phrase anchor: expected exactly one match, got {count}")
text = text.replace(old, new, 1)

old_question = '            val looksLikeQuestion = questionHints.any { hint -> text.contains(hint) }\n'
new_question = '''            // Generic interrogatives such as «چی» are only trusted at the start;
            // otherwise ordinary statements like «نمی‌دونستم چی بگم» get a false question mark.
            // Second-person question predicates are safe enough to detect anywhere in the sentence,
            // e.g. «امروز میای خونه».
            val openingQuestion = questionHints.any { hint ->
                val cue = hint.trim()
                text == cue || text.startsWith("$cue ")
            }
            val conversationalQuestionPredicates = listOf(
                "میای", "می‌آی", "می‌خوای", "میخوای", "می‌تونی", "میتونی", "هستی"
            )
            val conversationalQuestion = conversationalQuestionPredicates.any { cue ->
                Regex("(^|\\\\s)${Regex.escape(cue)}(\\\\s|$)").containsMatchIn(text)
            }
            val looksLikeQuestion = openingQuestion || conversationalQuestion
'''
if text.count(old_question) != 1:
    raise SystemExit("question inference anchor did not match exactly once")
text = text.replace(old_question, new_question, 1)

path.write_text(text, encoding="utf-8")
print("TEXT_PATCH_OK")