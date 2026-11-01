#!/usr/bin/env bash
set -uo pipefail
mkdir -p out
adb logcat -c
adb shell wm size 1080x1920
adb shell wm density 420
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell settings put system time_12_24 24
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 0941
adb shell am broadcast -a com.android.systemui.demo -e command battery -e level 100 -e plugged false
# Synthetic data only; StoreAssetsTest clears FLAG_SECURE for its emulator window only.
test_filter=()
if [ -n "${EMROOZ_INSTRUMENTATION_CLASS:-}" ]; then
    test_filter+=("-Pandroid.testInstrumentationRunnerArguments.class=$EMROOZ_INSTRUMENTATION_CLASS")
fi
timeout 600s gradle --no-daemon -Dorg.gradle.jvmargs='-Xmx1536m -Dfile.encoding=UTF-8' -PemroozPreview -PemroozAbi=x86_64 -Pandroid.testInstrumentationRunnerArguments.timeout_msec=90000 "${test_filter[@]}" :app:connectedDebugAndroidTest
result=$?
if ! adb pull /sdcard/Download/Emrooz-Store-Screenshots out/screenshots; then
    printf '%s\n' 'Requested screenshots were not exported from the emulator.'
    result=1
fi
adb logcat -d >out/android-logcat.txt
if [ "$result" -ne 0 ]; then
    grep -n -E -A35 -B5 'FATAL EXCEPTION|AndroidRuntime|NoClassDefFound|Exception|Error' out/android-logcat.txt | tail -200
fi
exit "$result"
