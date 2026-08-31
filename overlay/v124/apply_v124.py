from pathlib import Path
import re

ROOT = Path('.')
UI = ROOT / 'app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt'
ENGINE = ROOT / 'app/src/main/java/com/nameemrooz/journal/speech/LiveSpeechEngine.kt'
GRADLE = ROOT / 'app/build.gradle.kts'

# 1) Undo v1.2.3's per-glyph scaling. The physical-device screenshot proved
# that scaling ۰ and : independently breaks the Doran clock/date geometry.
s = UI.read_text(encoding='utf-8')
bad = '''            value.forEach { char ->\n                doranDigitRes(char)?.let { resId ->\n                    val glyphHeight = when (char) {\n                        '0', '۰' -> height * 1.35f\n                        ':' -> height * 0.52f\n                        else -> height\n                    }\n                    DoranImage(resId, glyphHeight, tint)\n                }\n            }'''
good = '''            value.forEach { char ->\n                doranDigitRes(char)?.let { DoranImage(it, height, tint) }\n            }'''
if s.count(bad) != 1:
    raise SystemExit(f'Expected broken v1.2.3 glyph block once; found {s.count(bad)}')
s = s.replace(bad, good, 1)
UI.write_text(s, encoding='utf-8')

# 2) Stronger but bounded microphone front-end.
s = ENGINE.read_text(encoding='utf-8')
if 'import android.media.audiofx.AutomaticGainControl' not in s:
    marker = 'import android.media.MediaRecorder\n'
    if marker not in s:
        raise SystemExit('MediaRecorder import marker missing')
    s = s.replace(
        marker,
        marker + 'import android.media.audiofx.AutomaticGainControl\nimport android.media.audiofx.NoiseSuppressor\n',
        1,
    )

state_block = '''        if (record.state != AudioRecord.STATE_INITIALIZED) {\n            record.release()\n            sessionGate.finish()\n            emitError("میکروفون آماده نشد")\n            return\n        }\n        try {\n            record.startRecording()\n'''
replacement = '''        if (record.state != AudioRecord.STATE_INITIALIZED) {\n            record.release()\n            sessionGate.finish()\n            emitError("میکروفون آماده نشد")\n            return\n        }\n\n        val autoGain = try {\n            if (AutomaticGainControl.isAvailable()) {\n                AutomaticGainControl.create(record.audioSessionId)?.also { it.enabled = true }\n            } else null\n        } catch (_: Throwable) { null }\n        val noiseSuppressor = try {\n            if (NoiseSuppressor.isAvailable()) {\n                NoiseSuppressor.create(record.audioSessionId)?.also { it.enabled = true }\n            } else null\n        } catch (_: Throwable) { null }\n        val adaptiveGain = AdaptiveSpeechGain()\n\n        try {\n            record.startRecording()\n'''
if s.count(state_block) != 1:
    raise SystemExit(f'AudioRecord state/start marker count={s.count(state_block)}')
s = s.replace(state_block, replacement, 1)

start_fail = '''        } catch (t: Throwable) {\n            record.release()\n            sessionGate.finish()\n            emitError("میکروفون شروع نشد")\n            return\n        }\n'''
start_fail_new = '''        } catch (t: Throwable) {\n            try { autoGain?.release() } catch (_: Throwable) {}\n            try { noiseSuppressor?.release() } catch (_: Throwable) {}\n            record.release()\n            sessionGate.finish()\n            emitError("میکروفون شروع نشد")\n            return\n        }\n'''
if s.count(start_fail) != 1:
    raise SystemExit(f'start failure marker count={s.count(start_fail)}')
s = s.replace(start_fail, start_fail_new, 1)

capture_marker = '''                    if (n == 0) continue\n                    finalPcm.append(readBuffer, n)\n                    pcmQueue.send(readBuffer.copyOf(n))\n'''
capture_new = '''                    if (n == 0) continue\n                    adaptiveGain.processInPlace(readBuffer, n)\n                    finalPcm.append(readBuffer, n)\n                    pcmQueue.send(readBuffer.copyOf(n))\n'''
if s.count(capture_marker) != 1:
    raise SystemExit(f'capture marker count={s.count(capture_marker)}')
s = s.replace(capture_marker, capture_new, 1)

finally_marker = '''                try { record.stop() } catch (_: Throwable) {}\n                try { record.release() } catch (_: Throwable) {}\n                if (audioRecord === record) audioRecord = null\n'''
finally_new = '''                try { record.stop() } catch (_: Throwable) {}\n                try { autoGain?.release() } catch (_: Throwable) {}\n                try { noiseSuppressor?.release() } catch (_: Throwable) {}\n                adaptiveGain.reset()\n                try { record.release() } catch (_: Throwable) {}\n                if (audioRecord === record) audioRecord = null\n'''
if s.count(finally_marker) != 1:
    raise SystemExit(f'capture finally marker count={s.count(finally_marker)}')
s = s.replace(finally_marker, finally_new, 1)
ENGINE.write_text(s, encoding='utf-8')

# 3) Side-by-side identity for safe physical-device testing.
s = GRADLE.read_text(encoding='utf-8')
s, a = re.subn(r'applicationId\s*=\s*"com\.nameemrooz\.journal\.v123best"', 'applicationId = "com.nameemrooz.journal.v124mic"', s, count=1)
s, b = re.subn(r'versionCode\s*=\s*26\b', 'versionCode = 27', s, count=1)
s, c = re.subn(r'versionName\s*=\s*"1\.2\.3"', 'versionName = "1.2.4"', s, count=1)
if (a, b, c) != (1, 1, 1):
    raise SystemExit(f'Version patch failed: {(a, b, c)}')
GRADLE.write_text(s, encoding='utf-8')

print('V124_CLOCK_MIC_PATCH_OK')
