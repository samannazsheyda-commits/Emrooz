#!/usr/bin/env bash
set -uo pipefail
mkdir -p out
adb logcat -c
# Install and launch the actual release variant before the debug instrumentation suite.
adb install -r app/build/outputs/apk/release/app-release.apk || exit $?
adb shell am start -W -n com.nameemrooz.journal.preview/com.nameemrooz.journal.MainActivity >out/release-launch.txt || exit $?
adb shell pidof com.nameemrooz.journal.preview >out/release-process.txt || exit $?
adb shell am force-stop com.nameemrooz.journal.preview
gradle --no-daemon -PemroozPreview -PemroozAbi=x86_64 :app:connectedDebugAndroidTest
result=$?
mkdir -p out
adb logcat -d >out/android-logcat.txt
if [ "$result" -ne 0 ]; then
    rg -n -A45 -B5 'FATAL EXCEPTION|AndroidRuntime|NoClassDefFound|Exception|Error' out/android-logcat.txt | tail -250
fi
exit "$result"
