from pathlib import Path
import re

ROOT = Path('.')
UI = ROOT / 'app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt'
TEXT = ROOT / 'app/src/main/java/com/nameemrooz/journal/util/PersianText.kt'
GRADLE = ROOT / 'app/build.gradle.kts'

# 1) Only requested visual changes: smaller clock + more breathing room above date.
s = UI.read_text(encoding='utf-8')
old_clock = '''    DoranTimeLine(\n        millis = now,\n        tint = colors.onBackground,\n        height = 21.dp,\n        includeLabel = false,\n        centered = true\n    )\n    Spacer(Modifier.height(3.dp))\n    DoranDateLine(\n        millis = now,\n        tint = colors.onSurfaceVariant,\n        height = 13.dp,\n        centered = true,\n        words = true\n    )'''
new_clock = '''    DoranTimeLine(\n        millis = now,\n        tint = colors.onBackground,\n        height = 17.dp,\n        includeLabel = false,\n        centered = true\n    )\n    Spacer(Modifier.height(7.dp))\n    DoranDateLine(\n        millis = now,\n        tint = colors.onSurfaceVariant,\n        height = 13.dp,\n        centered = true,\n        words = true\n    )'''
if s.count(old_clock) != 1:
    raise SystemExit(f'Expected v1.1.9 clock block exactly once; found {s.count(old_clock)}')
s = s.replace(old_clock, new_clock, 1)
UI.write_text(s, encoding='utf-8')

# 2) Strengthen spelling/orthography without paraphrasing or changing meaning.
s = TEXT.read_text(encoding='utf-8')
marker = '    private val phraseFixes = listOf(\n'
extra_rules = '''        "نمی خونم" to "نمی‌خونم", "نمیخونم" to "نمی‌خونم",\n        "می خونم" to "می‌خونم", "میخونم" to "می‌خونم",\n        "نمی بینم" to "نمی‌بینم", "نمیبینم" to "نمی‌بینم",\n        "می بینم" to "می‌بینم", "میبینم" to "می‌بینم",\n'''
if '"نمی خونم" to "نمی‌خونم"' not in s:
    if marker not in s:
        raise SystemExit('phraseFixes marker missing')
    s = s.replace(marker, marker + extra_rules, 1)

call_anchor = '''        phraseFixes.forEach { (from, to) -> text = text.replace(from, to, ignoreCase = false) }\n\n        text = text'''
call_replacement = '''        phraseFixes.forEach { (from, to) -> text = text.replace(from, to, ignoreCase = false) }\n        text = normalizeCommonOrthography(text)\n\n        text = text'''
if 'text = normalizeCommonOrthography(text)' not in s:
    if s.count(call_anchor) != 1:
        raise SystemExit(f'Expected phraseFixes pipeline anchor once; found {s.count(call_anchor)}')
    s = s.replace(call_anchor, call_replacement, 1)

# The baseline final punctuation pass can leave a trailing period even when a clear
# Persian question cue is present. Correct only that trailing mark; do not paraphrase.
final_anchor = '            text = restoreFinalPunctuation(text)\n'
final_replacement = '            text = enforceQuestionEnding(restoreFinalPunctuation(text))\n'
if 'enforceQuestionEnding(restoreFinalPunctuation(text))' not in s:
    if s.count(final_anchor) != 1:
        raise SystemExit(f'Expected final punctuation anchor once; found {s.count(final_anchor)}')
    s = s.replace(final_anchor, final_replacement, 1)

func_anchor = '    private fun restoreFinalPunctuation(input: String): String {'
func = '''    private fun normalizeCommonOrthography(input: String): String {\n        var text = input\n        // Persian plural suffixes: «کتاب های» -> «کتاب‌های», «روز ها» -> «روزها».\n        text = text.replace(\n            Regex("(?<=[\\u0600-\\u06FF])\\\\s+های(?=\\\\s|$|[،,.!؟؛:])"),\n            "‌های"\n        )\n        text = text.replace(\n            Regex("(?<=[\\u0600-\\u06FF])\\\\s+ها(?=\\\\s|$|[،,.!؟؛:])"),\n            "‌ها"\n        )\n        return text\n    }\n\n    private fun enforceQuestionEnding(input: String): String {\n        val text = input.trim()\n        if (!text.endsWith('.')) return text\n        val looksLikeQuestion = questionHints.any { hint -> text.contains(hint) }\n        return if (looksLikeQuestion) text.dropLast(1) + "؟" else text\n    }\n\n'''
if 'private fun normalizeCommonOrthography' not in s:
    if func_anchor not in s:
        raise SystemExit('restoreFinalPunctuation anchor missing')
    s = s.replace(func_anchor, func + func_anchor, 1)
elif 'private fun enforceQuestionEnding' not in s:
    enforce_only = '''    private fun enforceQuestionEnding(input: String): String {\n        val text = input.trim()\n        if (!text.endsWith('.')) return text\n        val looksLikeQuestion = questionHints.any { hint -> text.contains(hint) }\n        return if (looksLikeQuestion) text.dropLast(1) + "؟" else text\n    }\n\n'''
    if func_anchor not in s:
        raise SystemExit('restoreFinalPunctuation anchor missing for question helper')
    s = s.replace(func_anchor, enforce_only + func_anchor, 1)
TEXT.write_text(s, encoding='utf-8')

# 3) Side-by-side test install and explicit version.
s = GRADLE.read_text(encoding='utf-8')
s, a = re.subn(r'applicationId\s*=\s*"com\.nameemrooz\.journal\.v119final"', 'applicationId = "com.nameemrooz.journal.v121fast"', s, count=1)
s, b = re.subn(r'versionCode\s*=\s*22\b', 'versionCode = 24', s, count=1)
s, c = re.subn(r'versionName\s*=\s*"1\.1\.9"', 'versionName = "1.2.1"', s, count=1)
if (a, b, c) != (1, 1, 1):
    raise SystemExit(f'Version patch failed: {(a, b, c)}')
GRADLE.write_text(s, encoding='utf-8')

print('V121_FAST_EDITOR_PATCH_OK')
