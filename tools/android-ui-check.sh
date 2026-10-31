#!/usr/bin/env bash
set -uo pipefail
adb logcat -c
gradle --no-daemon -PemroozPreview -PemroozAbi=x86_64 :app:connectedDebugAndroidTest
result=$?
mkdir -p out
adb logcat -d >out/android-logcat.txt
if [ "$result" -ne 0 ]; then
    rg -n -A45 -B5 'FATAL EXCEPTION|AndroidRuntime|NoClassDefFound|Exception|Error' out/android-logcat.txt | tail -250
fi
exit "$result"
