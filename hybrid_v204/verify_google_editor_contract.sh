#!/usr/bin/env bash
set -euo pipefail

APP_ROOT="${1:-.}"
cd "$APP_ROOT"

rg -q 'versionCode = 34' app/build.gradle.kts
rg -q 'versionName = "2\.0\.4"' app/build.gradle.kts
rg -q 'com\.google\.mlkit:genai-prompt:1\.0\.0-beta4' app/build.gradle.kts
rg -q 'exclude\(group = "com\.google\.android\.datatransport", module = "transport-backend-cct"\)' app/build.gradle.kts
rg -q 'kotlinx-coroutines-android:1\.11\.0' app/build.gradle.kts
rg -q 'enhanced_on_device_editing' app/src/main/java/com/nameemrooz/journal/data/SettingsStore.kt
rg -q 'enhancedOnDeviceEditingEnabled.*false' app/src/main/java/com/nameemrooz/journal/data/SettingsStore.kt
rg -q 'GeminiNanoWritingEnhancer' app/src/main/java/com/nameemrooz/journal/writing
rg -q 'WritingEditSafety' app/src/main/java/com/nameemrooz/journal/writing
rg -q 'WritingEnhancementCoordinator' app/src/main/java/com/nameemrooz/journal/speech/HybridSpeechController.kt
rg -q 'enhancedEditingEnabled = enhancedEditing' app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt
rg -q 'ویراستاری دقیق‌تر با مدل روی دستگاه' app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt

if rg -q 'android.permission.INTERNET' app/src/main/AndroidManifest.xml; then
  echo 'ERROR: INTERNET permission present in source manifest' >&2
  exit 1
fi
# The old feature was a spoken-command trigger. Do not mistake normal Persian prose
# in the Nano editing prompt (e.g. "علائم نگارشی را اصلاح کن") for that removed path.
if rg -n '"اصلاح کن"|contains\("اصلاح کن"\)|startsWith\("اصلاح کن"\)' \
  app/src/main/java/com/nameemrooz/journal/speech \
  app/src/main/java/com/nameemrooz/journal/ui \
  app/src/main/java/com/nameemrooz/journal/data \
  app/src/main/java/com/nameemrooz/journal/privacy 2>/dev/null; then
  echo 'ERROR: removed voice editing command trigger returned' >&2
  exit 1
fi
if rg -q 'com\.google\.ai\.client\.generativeai|firebase-ai|generativelanguage\.googleapis\.com' app/build.gradle.kts app/src/main/java; then
  echo 'ERROR: cloud Gemini dependency/path detected' >&2
  exit 1
fi

echo 'Google on-device editor contract: PASS'
