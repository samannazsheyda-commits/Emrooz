from pathlib import Path
import re

GRADLE = Path('app/build.gradle.kts')

g = GRADLE.read_text(encoding='utf-8')

g, a = re.subn(
    r'applicationId\s*=\s*"com\.nameemrooz\.journal\.v119final"',
    'applicationId = "com.nameemrooz.journal.v120hybrid"',
    g,
    count=1,
)
g, b = re.subn(r'versionCode\s*=\s*22\b', 'versionCode = 23', g, count=1)
g, c = re.subn(r'versionName\s*=\s*"1\.1\.9"', 'versionName = "1.2.0"', g, count=1)
if (a, b, c) != (1, 1, 1):
    raise SystemExit(f'v1.2.0 metadata patch failed: {(a, b, c)}')

if 'implementation(files("libs/whispercpp-fa-release.aar"))' not in g:
    anchor = 'dependencies {\n'
    if g.count(anchor) != 1:
        raise SystemExit(f'Expected one dependencies block, found {g.count(anchor)}')
    g = g.replace(
        anchor,
        anchor + '    implementation(files("libs/whispercpp-fa-release.aar"))\n',
        1,
    )

GRADLE.write_text(g, encoding='utf-8')
print('V120_BUILD_PATCH_OK')
