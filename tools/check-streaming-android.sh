#!/usr/bin/env bash
# android-emulator-runner executes each script line in a separate shell.
# Keep status handling and pre-shutdown log capture in one process.
set -uo pipefail
mkdir -p out
adb shell settings put secure show_ime_with_hard_keyboard 1
result=0
timeout 600s gradle --no-daemon -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' -PemroozPreview -PemroozAbi=x86_64 -Pandroid.testInstrumentationRunnerArguments.notClass=com.nameemrooz.journal.StoreAssetsTest :app:connectedDebugAndroidTest || result=$?
timeout 15s adb logcat -d >out/android-logcat.txt || true
exit "$result"
