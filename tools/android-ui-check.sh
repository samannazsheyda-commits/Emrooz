#!/usr/bin/env bash
set -uo pipefail
mkdir -p out
timeout 15s adb logcat -c
# Install and launch the actual release variant before the release instrumentation suite.
printf '%s\n' 'Installing the release APK'
timeout 120s adb install -r app/build/outputs/apk/release/app-release.apk || exit $?
printf '%s\n' 'Launching the release activity'
timeout 45s adb shell am start -W -n com.nameemrooz.journal.preview/com.nameemrooz.journal.MainActivity | tee out/release-launch.txt || exit $?
timeout 15s adb shell pidof com.nameemrooz.journal.preview >out/release-process.txt || exit $?
timeout 15s adb shell am force-stop com.nameemrooz.journal.preview
printf '%s\n' 'Running release instrumentation with bounded memory and time'
timeout 420s gradle --no-daemon -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' -PemroozPreview -PemroozAbi=x86_64 -PemroozReleaseTests -Pandroid.testInstrumentationRunnerArguments.timeout_msec=90000 :app:connectedReleaseAndroidTest
result=$?
mkdir -p out
timeout 30s adb logcat -d >out/android-logcat.txt
if [ "$result" -ne 0 ]; then
    timeout 15s adb shell dumpsys activity activities >out/android-activities.txt
    timeout 15s adb shell dumpsys meminfo >out/android-memory.txt
fi
if [ "$result" -ne 0 ]; then
    grep -n -E -A45 -B5 'FATAL EXCEPTION|AndroidRuntime|NoClassDefFound|Exception|Error' out/android-logcat.txt | tail -250
fi
exit "$result"
