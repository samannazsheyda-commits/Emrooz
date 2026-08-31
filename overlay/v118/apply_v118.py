from pathlib import Path
import re

ROOT = Path('.')

# 1) Splash: preserve the exact logo resource and all UI; only reduce its displayed size.
ui = ROOT / 'app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt'
s = ui.read_text(encoding='utf-8')
old = 'modifier = Modifier.fillMaxWidth(0.70f).height(205.dp),'
new = 'modifier = Modifier.fillMaxWidth(0.55f).height(160.dp),'
if s.count(old) != 1:
    raise SystemExit(f'Expected exactly one v1.1.7 splash-size line, found {s.count(old)}')
s = s.replace(old, new, 1)
ui.write_text(s, encoding='utf-8')

# 2) Persian spelling/orthography: add only conservative, explicit fixes.
persian = ROOT / 'app/src/main/java/com/nameemrooz/journal/util/PersianText.kt'
s = persian.read_text(encoding='utf-8')
marker = '    private val phraseFixes = listOf(\n'
additions = '''        "می خام" to "می‌خوام", "میخام" to "می‌خوام",\n        "می خاد" to "می‌خواد", "میخاد" to "می‌خواد",\n        "پیاده روی" to "پیاده‌روی",\n        "خونه ی" to "خونه‌ی", "خانه ی" to "خانه‌ی",\n        "بعدشم" to "بعدش هم", "قبلشم" to "قبلش هم",\n        "واقعا" to "واقعاً", "حتما" to "حتماً", "اصلا" to "اصلاً",\n'''
if '"میخام" to "می‌خوام"' not in s:
    if marker not in s:
        raise SystemExit('phraseFixes marker not found')
    s = s.replace(marker, marker + additions, 1)
persian.write_text(s, encoding='utf-8')

# 3) Version/package only. A unique package id avoids Android rejecting the CI-signed test build
# when another Emrooz test build signed with a different temporary key is already installed.
gradle = ROOT / 'app/build.gradle.kts'
s = gradle.read_text(encoding='utf-8')
s, app_hits = re.subn(r'applicationId\s*=\s*"com\.nameemrooz\.journal\.v117final"', 'applicationId = "com.nameemrooz.journal.v118final"', s, count=1)
s, code_hits = re.subn(r'versionCode\s*=\s*20\b', 'versionCode = 21', s, count=1)
s, name_hits = re.subn(r'versionName\s*=\s*"1\.1\.7"', 'versionName = "1.1.8"', s, count=1)
if (app_hits, code_hits, name_hits) != (1, 1, 1):
    raise SystemExit(f'Unexpected version patch counts: app={app_hits}, code={code_hits}, name={name_hits}')
gradle.write_text(s, encoding='utf-8')

print('V118_MINIMAL_PATCH_OK')
