from pathlib import Path
import re

ROOT = Path('.')
UI = ROOT / 'app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt'
TEXT = ROOT / 'app/src/main/java/com/nameemrooz/journal/util/PersianText.kt'
GRADLE = ROOT / 'app/build.gradle.kts'

# 1) Conservative Persian editor: only a high-confidence lexical correction.
# Productive نیم‌فاصله/orthography rules from v1.2.2 remain unchanged.
s = TEXT.read_text(encoding='utf-8')
if '"اهوار" to "اهواز"' not in s:
    marker = '    private val knownTranscriptionFixes = linkedMapOf(\n'
    if marker not in s:
        raise SystemExit('knownTranscriptionFixes marker missing')
    s = s.replace(marker, marker + '        "اهوار" to "اهواز",\n', 1)
TEXT.write_text(s, encoding='utf-8')

# 2) Doran clock/date glyph tuning only. Keep every other UI element untouched.
s = UI.read_text(encoding='utf-8')
old = '''            value.forEach { char ->\n                doranDigitRes(char)?.let { DoranImage(it, height, tint) }\n            }'''
new = '''            value.forEach { char ->\n                doranDigitRes(char)?.let { resId ->\n                    val glyphHeight = when (char) {\n                        '0', '۰' -> height * 1.35f\n                        ':' -> height * 0.52f\n                        else -> height\n                    }\n                    DoranImage(resId, glyphHeight, tint)\n                }\n            }'''
if s.count(old) != 1:
    raise SystemExit(f'Expected DoranDigits body exactly once; found {s.count(old)}')
s = s.replace(old, new, 1)
UI.write_text(s, encoding='utf-8')

# 3) Side-by-side test identity. This avoids signature conflicts with prior test APKs.
s = GRADLE.read_text(encoding='utf-8')
s, a = re.subn(r'applicationId\s*=\s*"com\.nameemrooz\.journal\.v122accuracy"', 'applicationId = "com.nameemrooz.journal.v123best"', s, count=1)
s, b = re.subn(r'versionCode\s*=\s*25\b', 'versionCode = 26', s, count=1)
s, c = re.subn(r'versionName\s*=\s*"1\.2\.2"', 'versionName = "1.2.3"', s, count=1)
if (a, b, c) != (1, 1, 1):
    raise SystemExit(f'Version patch failed: {(a, b, c)}')

# 4) Accuracy-first live ASR: replace only the v1.2.2 Rizeh streaming asset
# with the Koochik streaming INT8 asset. Keep the proven Koochik final pass.
s = s.replace('src/main/assets/models/shenava_rizeh_stream', 'src/main/assets/models/shenava_koochik_stream')
s = s.replace(
    'mah92/sherpa-onnx-nemo-ctc-fa-shenava-rizeh-v1.0-streaming-int8-2026-06-26',
    'mah92/sherpa-onnx-nemo-ctc-fa-shenava-koochik-v1.0-streaming-int8-2026-06-26'
)
s = s.replace(
    '889a4fdfeb25ea0858493294842d36c637acf391f7f49f6e98881709a468b6bc',
    '439983c95ab83c55c841e0795ba3a61d56718ec5c972a3a208548b93470b04b1'
)
if 'shenava_rizeh_stream' in s or '889a4fdfeb25ea0858493294842d36c637acf391f7f49f6e98881709a468b6bc' in s:
    raise SystemExit('Rizeh live model reference still present after v1.2.3 patch')
if 'shenava_koochik_stream' not in s or '439983c95ab83c55c841e0795ba3a61d56718ec5c972a3a208548b93470b04b1' not in s:
    raise SystemExit('Koochik live model patch missing')
GRADLE.write_text(s, encoding='utf-8')

print('V123_BEST_PERSIAN_PATCH_OK')
