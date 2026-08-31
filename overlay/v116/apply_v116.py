from pathlib import Path
import re

ROOT = Path('.')

# Keep the vNext UI but make the actual Compose intro/splash logo noticeably smaller.
ui = ROOT / 'app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt'
s = ui.read_text(encoding='utf-8')
pattern = re.compile(
    r'(painter\s*=\s*painterResource\(R\.drawable\.emrooz_logo\).*?'
    r'modifier\s*=\s*Modifier)\.fillMaxWidth\(\s*0?\.48f?\s*\)(\.aspectRatio\(\s*1\.9f\s*\))',
    re.S,
)
s, logo_hits = pattern.subn(r'\1.fillMaxWidth(0.34f)\2', s, count=1)
if logo_hits == 0:
    s, logo_hits = re.subn(
        r'modifier\s*=\s*Modifier\.fillMaxWidth\(\s*0?\.48f?\s*\)\.aspectRatio\(\s*1\.9f\s*\)',
        'modifier = Modifier.fillMaxWidth(0.34f).aspectRatio(1.9f)',
        s,
        count=1,
    )
if logo_hits == 0:
    raise SystemExit('Could not locate the Emrooz intro logo sizing rule')
ui.write_text(s, encoding='utf-8')

idx = s.find('R.drawable.emrooz_logo')
print('intro_logo_hits=', logo_hits)
print('intro_logo_context_after:')
print(s[max(0, idx - 280): min(len(s), idx + 480)])
if 'fillMaxWidth(0.34f).aspectRatio(1.9f)' not in s[max(0, idx - 500): min(len(s), idx + 800)]:
    raise SystemExit('Emrooz intro logo did not reach target 0.34f width')

# The vNext archive is truncated at its final PNG. Remove only malformed PNG files.
removed = []
for p in (ROOT / 'app/src/main/res').rglob('*.png'):
    data = p.read_bytes()
    valid = (
        len(data) >= 20
        and data.startswith(b'\x89PNG\r\n\x1a\n')
        and data[-12:-8] == b'\x00\x00\x00\x00'
        and data[-8:-4] == b'IEND'
    )
    if not valid:
        removed.append(str(p))
        p.unlink()
print('removed_malformed_pngs=', removed)

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

# Version bump and JVM target alignment. Kotlin/KAPT in this project targets Java 17,
# so javac must use the same target or modern Gradle rejects the build before tests.
gradle = ROOT / 'app/build.gradle.kts'
s = gradle.read_text(encoding='utf-8')
s = re.sub(r'versionCode\s*=\s*\d+', 'versionCode = 16', s, count=1)
s = re.sub(r'versionName\s*=\s*"[^"]+"', 'versionName = "1.1.6"', s, count=1)
if 'sourceCompatibility = JavaVersion.VERSION_17' not in s:
    android_pos = s.find('android {')
    if android_pos < 0:
        raise SystemExit('android block not found in app/build.gradle.kts')
    brace_pos = s.find('{', android_pos)
    insert = '''\n    compileOptions {\n        sourceCompatibility = JavaVersion.VERSION_17\n        targetCompatibility = JavaVersion.VERSION_17\n    }\n'''
    s = s[:brace_pos + 1] + insert + s[brace_pos + 1:]
gradle.write_text(s, encoding='utf-8')

print('v1.1.6 source patch applied')
