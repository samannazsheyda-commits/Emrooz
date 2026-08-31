from pathlib import Path
import re

ROOT = Path('.')
UI = ROOT / 'app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt'
TEXT = ROOT / 'app/src/main/java/com/nameemrooz/journal/util/PersianText.kt'
GRADLE = ROOT / 'app/build.gradle.kts'

# 1) Header fix from the physical-device screenshot: much smaller time,
# real breathing room, and numeric day/year while keeping Doran vectors.
s = UI.read_text(encoding='utf-8')
old = '''    DoranTimeLine(\n        millis = now,\n        tint = colors.onBackground,\n        height = 17.dp,\n        includeLabel = false,\n        centered = true\n    )\n    Spacer(Modifier.height(7.dp))\n    DoranDateLine(\n        millis = now,\n        tint = colors.onSurfaceVariant,\n        height = 13.dp,\n        centered = true,\n        words = true\n    )'''
new = '''    DoranTimeLine(\n        millis = now,\n        tint = colors.onBackground,\n        height = 12.dp,\n        includeLabel = false,\n        centered = true\n    )\n    Spacer(Modifier.height(12.dp))\n    DoranDateLine(\n        millis = now,\n        tint = colors.onSurfaceVariant,\n        height = 10.dp,\n        centered = true,\n        words = false\n    )'''
if s.count(old) != 1:
    raise SystemExit(f'Expected v1.2.1 clock/date block once; found {s.count(old)}')
s = s.replace(old, new, 1)
UI.write_text(s, encoding='utf-8')

# 2) Add generic but safe orthographic cleanup. It only joins already-separated
# Persian morphology; it does not invent words or paraphrase the transcript.
s = TEXT.read_text(encoding='utf-8')
call = '        text = normalizeCommonOrthography(text)\n'
replacement = '        text = normalizeCommonOrthography(text)\n        text = normalizeV122Orthography(text)\n'
if 'text = normalizeV122Orthography(text)' not in s:
    if s.count(call) != 1:
        raise SystemExit(f'Expected normalizeCommonOrthography call once; found {s.count(call)}')
    s = s.replace(call, replacement, 1)

anchor = '    private fun restoreFinalPunctuation(input: String): String {'
func = r'''    private fun normalizeV122Orthography(input: String): String {
        var text = input
        // Generic separated verbal prefix: «می روم» -> «می‌روم», «نمی دونم» -> «نمی‌دونم».
        text = text.replace(
            Regex("(^|\\s)(ن?می)\\s+([\\u0600-\\u06FF]+)"),
            "$1$2‌$3"
        )
        // Comparative suffixes: «بهتر ترین» -> «بهتر‌ترین».
        text = text.replace(
            Regex("(?<=[\\u0600-\\u06FF])\\s+(تر|ترین)(?=\\s|$|[،,.!؟؛:])"),
            "‌$1"
        )
        // Colloquial/standard ezafe after heh: «خونه ی» -> «خونه‌ی».
        text = text.replace(
            Regex("ه\\s+ی(?=\\s|$|[،,.!؟؛:])"),
            "ه‌ی"
        )
        return text
    }

'''
if 'private fun normalizeV122Orthography' not in s:
    if anchor not in s:
        raise SystemExit('restoreFinalPunctuation anchor missing')
    s = s.replace(anchor, func + anchor, 1)
TEXT.write_text(s, encoding='utf-8')

# 3) Side-by-side test package so the user's previous journal install is untouched.
s = GRADLE.read_text(encoding='utf-8')
s, a = re.subn(r'applicationId\s*=\s*"com\.nameemrooz\.journal\.v121fast"', 'applicationId = "com.nameemrooz.journal.v122accuracy"', s, count=1)
s, b = re.subn(r'versionCode\s*=\s*24\b', 'versionCode = 25', s, count=1)
s, c = re.subn(r'versionName\s*=\s*"1\.2\.1"', 'versionName = "1.2.2"', s, count=1)
if (a, b, c) != (1, 1, 1):
    raise SystemExit(f'Version patch failed: {(a, b, c)}')
GRADLE.write_text(s, encoding='utf-8')

print('V122_ACCURACY_UI_PATCH_OK')
