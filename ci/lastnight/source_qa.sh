#!/usr/bin/env bash
set -euo pipefail
bash tools/test_shenava_rnnt_contract.sh
bash tools/verify_home_ui_contract.sh
bash tools/verify_live_engine_contract.sh
bash tools/verify_ui_contract.sh
UI=app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt
MAIN=app/src/main/java/com/nameemrooz/journal/MainActivity.kt
! rg -q 'Text\((e|entry)\.title' "$UI"
rg -q 'archiveDay' "$UI"
rg -q 'archiveDate' "$UI"
rg -q 'JalaliDate.time' "$UI"
! rg -q 'PasscodeStore|OutlinedTextField' "$MAIN"
rg -q 'BiometricGate' "$MAIN"
rg -q 'Icons.Default.Fingerprint' "$MAIN"
rg -q 'ApprovedSplash' "$MAIN"
! rg -q 'painterResource\(R\.(drawable|mipmap)\.(.*logo|ic_launcher|app_icon)' "$UI"
identify app/src/main/res/drawable-nodpi/app_icon_emrooz_final.png | grep -q '1024x1024'
echo '0069292828dfac9a7d98294f1bfefd0e787cb3f008c49e1b2780e9a9f66ca5b5  app/src/main/res/drawable-nodpi/splash_emrooz_light.webp' | sha256sum -c -
echo 'bd36788fa9fdc8dddf58b6221213dc9a390a70f758cd203f7a6efa1b3be499f5  app/src/main/res/drawable-nodpi/splash_emrooz_dark.webp' | sha256sum -c -
echo 'LAST-NIGHT FINAL CONTRACT: PASS'
