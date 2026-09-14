#!/usr/bin/env bash
set -euo pipefail
UI="app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt"
SETTINGS="app/src/main/java/com/nameemrooz/journal/data/SettingsStore.kt"
MANIFEST="app/src/main/AndroidManifest.xml"

grep -q 'HybridSpeechController' "$UI"
! grep -q 'LiveSpeechEngine(' "$UI"
grep -q 'speechMode.collectAsState' "$UI"
grep -q 'موتور تبدیل صدا به متن' "$UI"
grep -q 'خصوصی و آفلاین' "$UI"
grep -q 'دقت بیشتر' "$UI"
grep -q 'سرویس گفتار گوشی ممکن است' "$UI"
grep -q 'highAccuracyDisclosureAccepted' "$SETTINGS"
grep -q 'android.speech.RecognitionService' "$MANIFEST"
! grep -q 'android.permission.INTERNET' "$MANIFEST"
grep -q 'LocalPunctuationModel' app/src/main/java/com/nameemrooz/journal/speech/HybridSpeechController.kt
grep -q 'LexicalSafety.isPunctuationOnlyChange' app/src/main/java/com/nameemrooz/journal/writing/LocalPunctuationModel.kt

echo 'Hybrid speech + local writing contract: PASS'
