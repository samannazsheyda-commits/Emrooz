from pathlib import Path
import re

ROOT = Path('.')
UI = ROOT / 'app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt'
ENGINE = ROOT / 'app/src/main/java/com/nameemrooz/journal/speech/LiveSpeechEngine.kt'
GRADLE = ROOT / 'app/build.gradle.kts'

# 1) Replace the vector-based Doran clock/date with real text rendered by
# Vazirmatn. JalaliDate.time/pretty already return Persian digits, so ۰ and :
# are normal font glyphs instead of separate drawable assets.
s = UI.read_text(encoding='utf-8')
old = '''@Composable
private fun CurrentClockAndDate() {
    val colors = MaterialTheme.colorScheme
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    DoranTimeLine(
        millis = now,
        tint = colors.onBackground,
        height = 12.dp,
        includeLabel = false,
        centered = true
    )
    Spacer(Modifier.height(12.dp))
    DoranDateLine(
        millis = now,
        tint = colors.onSurfaceVariant,
        height = 10.dp,
        centered = true,
        words = false
    )
}'''
new = '''@Composable
private fun CurrentClockAndDate() {
    val colors = MaterialTheme.colorScheme
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }
    Text(
        text = JalaliDate.time(now),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontFamily = UiFont,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
        color = colors.onBackground
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = JalaliDate.pretty(now).replace("  ", " "),
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        fontFamily = UiFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = colors.onSurfaceVariant
    )
}'''
if s.count(old) != 1:
    raise SystemExit(f'CurrentClockAndDate v1.2.4 block count={s.count(old)}')
s = s.replace(old, new, 1)
UI.write_text(s, encoding='utf-8')

# 2) Do not expose every unstable ASR hypothesis. Hold live text until a
# common prefix has remained stable for about one second.
s = ENGINE.read_text(encoding='utf-8')
if 'import android.os.SystemClock' not in s:
    marker = 'import android.os.Looper\n'
    if marker not in s:
        raise SystemExit('Looper import marker missing')
    s = s.replace(marker, marker + 'import android.os.SystemClock\n', 1)

marker = '''        emitListening(true)\n        emitFinalizing(false)\n\n        decodeJob = scope.launch(Dispatchers.Default) {'''
replacement = '''        emitListening(true)\n        emitFinalizing(false)\n        val liveStabilizer = StableLiveTranscript()\n\n        decodeJob = scope.launch(Dispatchers.Default) {'''
if s.count(marker) != 1:
    raise SystemExit(f'live stabilizer insertion marker count={s.count(marker)}')
s = s.replace(marker, replacement, 1)

raw = '''                    if (partial.isNotBlank() && partial != lastEmitted) {\n                        lastEmitted = partial\n                        emitText(partial)\n                    }'''
stable = '''                    val stablePartial = liveStabilizer.offer(partial, SystemClock.elapsedRealtime())\n                    if (!stablePartial.isNullOrBlank() && stablePartial != lastEmitted) {\n                        lastEmitted = stablePartial\n                        emitText(stablePartial)\n                    }'''
if s.count(raw) != 1:
    raise SystemExit(f'raw live emit block count={s.count(raw)}')
s = s.replace(raw, stable, 1)
ENGINE.write_text(s, encoding='utf-8')

# 3) Side-by-side install identity for safe physical-device testing.
s = GRADLE.read_text(encoding='utf-8')
s, a = re.subn(r'applicationId\s*=\s*"com\.nameemrooz\.journal\.v124mic"', 'applicationId = "com.nameemrooz.journal.v125stable"', s, count=1)
s, b = re.subn(r'versionCode\s*=\s*27\b', 'versionCode = 28', s, count=1)
s, c = re.subn(r'versionName\s*=\s*"1\.2\.4"', 'versionName = "1.2.5"', s, count=1)
if (a, b, c) != (1, 1, 1):
    raise SystemExit(f'Version patch failed: {(a, b, c)}')
GRADLE.write_text(s, encoding='utf-8')

print('V125_STABLE_LIVE_FONT_PATCH_OK')
