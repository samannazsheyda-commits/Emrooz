from pathlib import Path
import re

ROOT = Path('.')
UI = ROOT / 'app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt'
TEXT = ROOT / 'app/src/main/java/com/nameemrooz/journal/util/PersianText.kt'
NAMES = ROOT / 'app/src/main/java/com/nameemrooz/journal/util/PersianNameCorrector.kt'
GRADLE = ROOT / 'app/build.gradle.kts'

# 1) Splash: same exact artwork, substantially smaller on screen.
s = UI.read_text(encoding='utf-8')
old = 'modifier = Modifier.fillMaxWidth(0.55f).height(160.dp),'
new = 'modifier = Modifier.fillMaxWidth(0.38f).height(115.dp),'
if s.count(old) != 1:
    raise SystemExit(f'Expected v1.1.8 splash line exactly once; found {s.count(old)}')
s = s.replace(old, new, 1)

# 2) Clock/date: use the exact pre-existing Doran vector glyphs instead of Vazirmatn Text.
old_clock = '''    Text(\n        JalaliDate.time(now),\n        fontFamily = UiFont,\n        fontWeight = FontWeight.Normal,\n        fontSize = 21.sp,\n        color = colors.onBackground,\n        textAlign = TextAlign.Center\n    )\n    Spacer(Modifier.height(2.dp))\n    Text(\n        JalaliDate.pretty(now),\n        fontFamily = UiFont,\n        fontWeight = FontWeight.Normal,\n        fontSize = 13.sp,\n        color = colors.onSurfaceVariant,\n        textAlign = TextAlign.Center\n    )'''
new_clock = '''    DoranTimeLine(\n        millis = now,\n        tint = colors.onBackground,\n        height = 21.dp,\n        includeLabel = false,\n        centered = true\n    )\n    Spacer(Modifier.height(3.dp))\n    DoranDateLine(\n        millis = now,\n        tint = colors.onSurfaceVariant,\n        height = 13.dp,\n        centered = true,\n        words = true\n    )'''
if s.count(old_clock) != 1:
    raise SystemExit(f'Expected old Vazirmatn clock/date block once; found {s.count(old_clock)}')
s = s.replace(old_clock, new_clock, 1)

# 3) Glow: clip to the same rounded shape BEFORE the glow is drawn.
old_glow = '''        modifier = modifier\n            .transcriptionGlow(converting)\n            .border(1.dp, colors.primary.copy(alpha = if (converting) 0.32f else 0.14f), shape),'''
new_glow = '''        modifier = modifier\n            .clip(shape)\n            .transcriptionGlow(converting)\n            .border(1.dp, colors.primary.copy(alpha = if (converting) 0.32f else 0.14f), shape),'''
if s.count(old_glow) != 1:
    raise SystemExit(f'Expected glow modifier block once; found {s.count(old_glow)}')
s = s.replace(old_glow, new_glow, 1)
UI.write_text(s, encoding='utf-8')

# 4) Proofreading: keep it conservative. Do not rewrite spoken numbers, fillers, or repetitions.
s = TEXT.read_text(encoding='utf-8')
old_pipeline = '''        text = removeFillers(text)\n        text = normalizeSpokenNumbers(text)\n\n        if (final && text.isNotBlank()) {\n            knownTranscriptionFixes.forEach { (from, to) -> text = text.replace(from, to) }\n            text = removeImmediateWordStutters(text)\n            text = collapseRepeatedPhrases(text)\n            text = restoreFinalPunctuation(text)\n        }'''
new_pipeline = '''        if (final && text.isNotBlank()) {\n            // Final editing is deliberately conservative: spelling, known ASR corrections,\n            // punctuation and spacing only. Preserve the speaker's words, numbers and repetition.\n            knownTranscriptionFixes.forEach { (from, to) -> text = text.replace(from, to) }\n            text = restoreFinalPunctuation(text)\n        }'''
if s.count(old_pipeline) != 1:
    raise SystemExit(f'Expected aggressive Persian cleanup pipeline once; found {s.count(old_pipeline)}')
s = s.replace(old_pipeline, new_pipeline, 1)
# Normalize the common compound name orthography even when ASR gets both words right.
marker = '    private val phraseFixes = listOf(\n'
compound_rules = '''        "روح الله" to "روح‌الله", "روح اله" to "روح‌الله",\n'''
if '"روح الله" to "روح‌الله"' not in s:
    if marker not in s: raise SystemExit('phraseFixes marker missing')
    s = s.replace(marker, marker + compound_rules, 1)
TEXT.write_text(s, encoding='utf-8')

# 5) Proper-name recovery: the old corrector only fixed surnames after known first names.
# Add a small, high-confidence ASR alias layer for compound Persian given names.
s = NAMES.read_text(encoding='utf-8')
anchor = '    fun warmUp(): Int = firstNames.size + surnamesByName.size\n\n'
alias_code = '''    private val givenNameAliases = linkedMapOf(\n        "روح الله" to "روح‌الله",\n        "روح اله" to "روح‌الله",\n        "روحلا" to "روح‌الله",\n        "روهلا" to "روح‌الله",\n        "روالله" to "روح‌الله",\n        "رولا" to "روح‌الله"\n    )\n\n    private fun correctGivenNameAliases(input: String): String {\n        var out = input\n        givenNameAliases.forEach { (from, to) ->\n            val pattern = Regex("(^|\\\\s)${Regex.escape(from)}(?=\\\\s|$|[،,.!؟؛:])")\n            out = out.replace(pattern) { match -> match.groupValues[1] + to }\n        }\n        return out\n    }\n\n'''
if 'private val givenNameAliases' not in s:
    if anchor not in s: raise SystemExit('PersianNameCorrector warmUp anchor missing')
    s = s.replace(anchor, anchor + alias_code, 1)
old_start = '''    fun correct(input: String): String {\n        if (input.isBlank() || firstNames.isEmpty() || surnamesByName.isEmpty()) return input\n\n        val tokens = input.split(Regex("\\\\s+")).toMutableList()\n        if (tokens.size < 2) return input'''
new_start = '''    fun correct(input: String): String {\n        if (input.isBlank()) return input\n        val aliasCorrected = correctGivenNameAliases(input)\n        if (firstNames.isEmpty() || surnamesByName.isEmpty()) return aliasCorrected\n\n        val tokens = aliasCorrected.split(Regex("\\\\s+")).toMutableList()\n        if (tokens.size < 2) return aliasCorrected'''
if s.count(old_start) != 1:
    raise SystemExit(f'Expected PersianNameCorrector correct() start once; found {s.count(old_start)}')
s = s.replace(old_start, new_start, 1)
NAMES.write_text(s, encoding='utf-8')

# 6) Unique install id/version for this testable build only.
s = GRADLE.read_text(encoding='utf-8')
s, a = re.subn(r'applicationId\s*=\s*"com\.nameemrooz\.journal\.v118final"', 'applicationId = "com.nameemrooz.journal.v119final"', s, count=1)
s, b = re.subn(r'versionCode\s*=\s*21\b', 'versionCode = 22', s, count=1)
s, c = re.subn(r'versionName\s*=\s*"1\.1\.8"', 'versionName = "1.1.9"', s, count=1)
if (a,b,c) != (1,1,1):
    raise SystemExit(f'Version patch failed: {(a,b,c)}')
GRADLE.write_text(s, encoding='utf-8')

print('V119_QUALITY_PATCH_OK')
