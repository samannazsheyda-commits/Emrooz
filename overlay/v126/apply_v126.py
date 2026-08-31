from pathlib import Path
import re

ROOT = Path('.')
GRADLE = ROOT / 'app/build.gradle.kts'

s = GRADLE.read_text(encoding='utf-8')
s, a = re.subn(r'applicationId\s*=\s*"com\.nameemrooz\.journal\.v125stable"', 'applicationId = "com.nameemrooz.journal.v126architectural"', s, count=1)
s, b = re.subn(r'versionCode\s*=\s*28\b', 'versionCode = 29', s, count=1)
s, c = re.subn(r'versionName\s*=\s*"1\.2\.5"', 'versionName = "1.2.6"', s, count=1)
if (a, b, c) != (1, 1, 1):
    raise SystemExit(f'v1.2.6 version patch failed: {(a, b, c)}')
GRADLE.write_text(s, encoding='utf-8')

print('V126_VERSION_PATCH_OK')
