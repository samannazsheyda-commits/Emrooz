from pathlib import Path
import re

ROOT = Path('.')

# Keep the vNext UI but reduce the logo used by the app's own privacy/splash intro.
ui = ROOT / 'app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt'
s = ui.read_text(encoding='utf-8')
idx = s.find('R.drawable.emrooz_logo')
if idx >= 0:
    a, b = max(0, idx - 900), min(len(s), idx + 1200)
    segment = s[a:b]
    before = segment
    segment, size_hits = re.subn(r'\.size\(\s*\d+(?:\.\d+)?\.dp\s*\)', '.size(124.dp)', segment, count=1)
    if size_hits == 0:
        segment, width_hits = re.subn(r'\.fillMaxWidth\(\s*0?\.\d+f?\s*\)', '.fillMaxWidth(0.46f)', segment, count=1)
    else:
        width_hits = 0
    s = s[:a] + segment + s[b:]
    ui.write_text(s, encoding='utf-8')
    print('intro_logo_found=1 size_hits=', size_hits, 'width_hits=', width_hits)
    print('intro_logo_context_after:')
    idx2 = s.find('R.drawable.emrooz_logo')
    print(s[max(0, idx2-300): min(len(s), idx2+500)])
else:
    print('intro_logo_found=0')

# Conservative Persian spelling/orthography fixes. Do not paraphrase the speaker.
persian = ROOT / 'app/src/main/java/com/nameemrooz/journal/util/PersianText.kt'
s = persian.read_text(encoding='utf-8')
marker = '    private val phraseFixes = listOf(\n'
additions = '''        "می خام" to "می‌خوام", "میخام" to "می‌خوام",\n        "می خاد" to "می‌خواد", "میخاد" to "می‌خواد",\n        "می خواد" to "می‌خواد", "میخواد" to "می‌خواد",\n        "پیاده روی" to "پیاده‌روی",\n        "خونه ی" to "خونه‌ی", "خانه ی" to "خانه‌ی",\n        "بعدشم" to "بعدش هم", "قبلشم" to "قبلش هم",\n        "واقعا" to "واقعاً", "حتما" to "حتماً", "اصلا" to "اصلاً",\n'''
if '"میخام" to "می‌خوام"' not in s:
    if marker not in s:
        raise SystemExit('phraseFixes marker not found')
    s = s.replace(marker, marker + additions, 1)
persian.write_text(s, encoding='utf-8')

# Clean stale Compose imports found in staged vNext copies.
s = ui.read_text(encoding='utf-8')
s = s.replace('import androidx.compose.foundation.layout.matchParentSize\n', '')
s = s.replace('import androidx.compose.foundation.layout.weight\n', '')
ui.write_text(s, encoding='utf-8')

# Version bump.
gradle = ROOT / 'app/build.gradle.kts'
s = gradle.read_text(encoding='utf-8')
s = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 16', s, count=1)
s = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.1.6"', s, count=1)
gradle.write_text(s, encoding='utf-8')

print('v1.1.6 source patch applied')
