#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
UI="$ROOT/app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt"
THEME="$ROOT/app/src/main/java/com/nameemrooz/journal/ui/theme/Theme.kt"
SETTINGS="$ROOT/app/src/main/java/com/nameemrooz/journal/data/SettingsStore.kt"
VM="$ROOT/app/src/main/java/com/nameemrooz/journal/ui/AppViewModel.kt"
MAIN="$ROOT/app/src/main/java/com/nameemrooz/journal/MainActivity.kt"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
SPEECH="$ROOT/app/src/main/java/com/nameemrooz/journal/speech"
ADAPTIVE="$ROOT/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml"
STYLE="$ROOT/app/src/main/res/values/styles.xml"

# API 26 compatibility: navigation-bar light appearance is applied at runtime via
# WindowCompat in Theme.kt. Do not leave the API-27-only XML attribute in values/.
if [[ -f "$STYLE" ]]; then
  sed -i '/android:windowLightNavigationBar/d' "$STYLE"
fi

fail() { echo "FAIL: $*" >&2; exit 1; }
need() { rg -q --fixed-strings "$2" "$1" || fail "$3"; }
forbid() { ! rg -q --fixed-strings "$2" "$1" || fail "$3"; }

[[ -s "$ROOT/app/src/main/res/font/abar_mid_fa_num_regular.otf" ]] || fail "Abar regular missing"
[[ -s "$ROOT/app/src/main/res/font/abar_mid_fa_num_semibold.otf" ]] || fail "Abar semibold missing"
[[ -s "$ROOT/app/src/main/res/font/lato_regular.ttf" ]] || fail "Lato regular missing"
[[ -s "$ROOT/app/src/main/res/font/lato_bold.ttf" ]] || fail "Lato bold missing"
need "$THEME" 'R.font.abar_mid_fa_num_regular' 'Persian typography is not wired to Abar regular'
need "$THEME" 'R.font.abar_mid_fa_num_semibold' 'Persian typography is not wired to Abar semibold'
need "$THEME" 'R.font.lato_regular' 'English typography is not wired to Lato regular'
need "$THEME" 'R.font.lato_bold' 'English typography is not wired to Lato bold'
need "$THEME" '0xFFF7F2EA' 'light palette changed'
need "$THEME" '0xFF0D1726' 'dark palette changed'
need "$THEME" '0xFFD68149' 'copper accent changed'

need "$UI" 'تو حرف بزن، من می‌نویسم' 'approved Persian slogan missing'
need "$UI" 'You talk, I write' 'approved English slogan missing'
need "$UI" 'AppLanguage.FA' 'FA mode missing'
need "$UI" 'AppLanguage.EN' 'EN mode missing'
need "$SETTINGS" 'enum class UiScale { SMALL, MEDIUM, LARGE }' 'UI scale choices missing'
need "$UI" '"کوچک", "معمولی", "بزرگ"' 'Persian UI scale labels missing'
need "$UI" '"Small", "Medium", "Large"' 'English UI scale labels missing'
need "$UI" 'LocalLayoutDirection provides' 'language-specific layout direction not enforced'

need "$UI" 'live-transcript-glow' 'live transcript glow missing'
need "$UI" 'Writing live…' 'live English transcription state missing'
forbid "$UI" 'Waveform' 'waveform UI must not be present'
forbid "$UI" 'waveform' 'waveform UI must not be present'

forbid "$UI" 'entry.title' 'archive/detail must not render generated titles'
forbid "$VM" 'TitleGenerator.generate' 'new journal entries must not generate titles'
need "$UI" 'JalaliDate.weekday' 'archive weekday missing'
need "$UI" 'JalaliDate.date' 'archive date missing'
need "$UI" 'JalaliDate.time' 'archive time missing'

need "$SETTINGS" '?: AppTheme.DAY' 'first-run theme must be light'
need "$SETTINGS" 'AppLanguage.FA.name' 'first-run language must be Persian'
need "$SETTINGS" 'UiScale.MEDIUM.name' 'first-run UI size must be medium'
need "$SETTINGS" 'lockKey] ?: true' 'first-run fingerprint lock must default on'
need "$MAIN" 'unlockBiometricFirst' 'biometric-first entry missing'
need "$MAIN" 'splash_emrooz_light' 'approved light splash is not used at runtime'
need "$MAIN" 'splash_emrooz_dark' 'approved dark splash is not used at runtime'

[[ -s "$ROOT/app/src/main/res/drawable-nodpi/splash_emrooz_light.webp" ]] || fail "light splash missing"
[[ -s "$ROOT/app/src/main/res/drawable-nodpi/splash_emrooz_dark.webp" ]] || fail "dark splash missing"
need "$ADAPTIVE" '@drawable/ic_launcher_foreground_png' 'adaptive launcher is not using approved icon foreground'
forbid "$MANIFEST" 'android.permission.INTERNET' 'INTERNET permission must remain absent'
! rg -n 'FileOutputStream|MediaRecorder\.OutputFormat|setOutputFile|openFileOutput|\.wav|\.mp3|\.m4a|\.aac' "$SPEECH" >/dev/null || fail "speech package contains an audio-file write path"

echo "Approved UI contract: PASS"
