#!/usr/bin/env bash
set -euo pipefail

OUT=/tmp/out
APK=/tmp/emrooz/app/build/outputs/apk/debug/app-debug.apk
PKG=com.nameemrooz.journal
ACTIVITY=.MainActivity

mkdir -p "$OUT"
adb install -r "$APK"
adb logcat -c
adb shell am force-stop "$PKG"

set +e
adb shell am start -W -n "$PKG/$ACTIVITY" > "$OUT/activity-start.txt" 2>&1
START_RC=$?
set -e
printf '%s\n' "$START_RC" > "$OUT/start-rc.txt"

# Software-only GitHub emulators are very slow. Give Compose/biometric fallback time to settle.
sleep 25

adb logcat -d > "$OUT/runtime-logcat.txt" 2>&1 || true
adb shell dumpsys activity exit-info "$PKG" > "$OUT/exit-info.txt" 2>&1 || true
adb shell dumpsys meminfo "$PKG" > "$OUT/meminfo.txt" 2>&1 || true
adb shell dumpsys activity activities > "$OUT/activities.txt" 2>&1 || true
adb shell pidof "$PKG" 2>/dev/null | tr -d '\r' > "$OUT/pid.txt" || true

grep -E -i 'FATAL EXCEPTION|AndroidRuntime|OutOfMemoryError|am_crash|am_kill|lowmemory|lmkd|Killing.*com\.nameemrooz|com\.nameemrooz\.journal' "$OUT/runtime-logcat.txt" > "$OUT/runtime-highlights.txt" || true

cat "$OUT/activity-start.txt"
echo "start_rc=$START_RC pid=$(cat "$OUT/pid.txt" 2>/dev/null || true)"
cat "$OUT/exit-info.txt" || true
cat "$OUT/runtime-highlights.txt" || true

# am start may report a framework timeout on the non-accelerated emulator, so runtime health is
# determined by the process/activity still being alive and the absence of an app fatal exception.
test -s "$OUT/pid.txt"
grep -q 'com.nameemrooz.journal/.MainActivity' "$OUT/activities.txt"
! grep -E 'FATAL EXCEPTION|Process: com\.nameemrooz\.journal' "$OUT/runtime-logcat.txt"

echo "RUNTIME_SMOKE_PASS pid=$(cat "$OUT/pid.txt")"
