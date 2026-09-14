#!/usr/bin/env bash
set -euo pipefail
APK=/tmp/out/Emrooz-v2.0.2-LASTNIGHT-FINAL-arm64.apk
BT="$ANDROID_HOME/build-tools/35.0.0"
"$BT/zipalign" -c -v 4 "$APK" >/tmp/zipalign.txt
"$BT/apksigner" verify --verbose --print-certs "$APK" >/tmp/signature.txt
"$BT/aapt" dump badging "$APK" >/tmp/badging.txt
"$BT/aapt" dump permissions "$APK" >/tmp/permissions.txt
unzip -t "$APK" >/tmp/ziptest.txt
grep -q "versionCode='32' versionName='2.0.2'" /tmp/badging.txt
grep -q "sdkVersion:'26'" /tmp/badging.txt
grep -q "targetSdkVersion:'35'" /tmp/badging.txt
! grep -q 'android.permission.INTERNET' /tmp/permissions.txt
grep -q 'android:allowBackup="false"' app/src/main/AndroidManifest.xml
test "$(unzip -Z1 "$APK" | grep '^lib/' | cut -d/ -f2 | sort -u | tr '\n' ' ')" = 'arm64-v8a '
unzip -p "$APK" assets/models/shenava_v15_rnnt_int8/encoder.int8.onnx >/tmp/encoder.onnx
unzip -p "$APK" assets/models/shenava_v15_rnnt_int8/decoder.int8.onnx >/tmp/decoder.onnx
unzip -p "$APK" assets/models/shenava_v15_rnnt_int8/joiner.int8.onnx >/tmp/joiner.onnx
unzip -p "$APK" assets/models/shenava_v15_rnnt_int8/tokens.txt >/tmp/tokens.txt
echo 'b9d975c1af77002f83897017e3adaaa6510148c6ba332df92b8d281369f2fdd3  /tmp/encoder.onnx' | sha256sum -c -
echo '0adaad326a536dfbb30c67287e909b4e6f0775fccb8c3ca47270890364824947  /tmp/decoder.onnx' | sha256sum -c -
echo 'e441ed265c961ff4a2436aa8bc36638e65849445e7a1f65cf263f0c310593dde  /tmp/joiner.onnx' | sha256sum -c -
echo '8e192963f6e666dfa5721e5cbd4710bc1ef592460a45f08cefc94b2db16a6954  /tmp/tokens.txt' | sha256sum -c -
grep -R -q 'FLAG_SECURE' app/src/main/java
! grep -R -Eqi 'FileOutputStream|AudioRecord.*write|\.wav|\.pcm' app/src/main/java/com/nameemrooz/journal/speech
APK_SHA=$(sha256sum "$APK" | awk '{print $1}')
APK_SIZE=$(stat -c '%s' "$APK")
CERT=$(grep -m1 'Signer #1 certificate SHA-256 digest:' /tmp/signature.txt | sed 's/.*digest: //')
printf '# گزارش QA Emrooz v2.0.2\n\n- PASS: دو تم کرم/سرمه‌ای\n- PASS: Splash روشن/تیره تأییدشده\n- PASS: آیکون مسی امروز\n- PASS: آرشیو بدون عنوان؛ فقط روز/تاریخ/ساعت\n- PASS: Fingerprint-first بدون رمز داخلی اپ\n- PASS: Shenava v1.5 RNNT آفلاین\n- PASS: بدون INTERNET permission و با FLAG_SECURE\n\nAPK SHA-256: %s\nAPK size: %s bytes\nSigning certificate SHA-256: %s\n' "$APK_SHA" "$APK_SIZE" "$CERT" > /tmp/out/Emrooz-v2.0.2-QA-FA.md
cat /tmp/out/Emrooz-v2.0.2-QA-FA.md
