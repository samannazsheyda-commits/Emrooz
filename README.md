# امروز — Emrooz

A Persian/English Android voice journal. The maintained app is `app/`; speech recognition uses Android device services. Historical reconstruction directories are not packaged.

## Current release

Version **2.3.1 / code 41**, production ID `com.nameemrooz.journal`, minimum Android 8 (API 26). Every launcher label is exactly **امروز**. The approved original copper logo, cream/navy themes, Abar/Lato fonts and aspect-preserving splash images are retained.

- Bundled Whisper/Shenava speech models and their native runtimes are removed. Private mode uses Android’s explicit on-device speech factory (Android 12+), checks language support where available, and reports unsupported or uninstalled languages. It never silently selects a network service.
- Local INT8 punctuation: the original model's MatMul and embedding/Gather weights are quantized, reducing its graph from 232,986,305 to 58,702,685 bytes. Punctuation is inserted into the original source text; names, numbers, whitespace and long text are retained.
- Gemini Nano editing is enabled by default when unset, preserves an explicit OFF preference, and is used only when the device model is available. Unsupported/error/timeout/unsafe generation uses local editing. Nano weights are provisioned by Android AICore, not bundled in the APK.
- Optional device speech service requires explicit disclosure acceptance. It may use its provider's servers; it is never silently enabled as an offline fallback.
- Clear and Record again actions confirm draft removal, invalidate old speech/edit results and preserve saved Room notes. Pending automatic recording is canceled on background/lock.
- Local draft recovery, save/edit/archive/delete, Persian RTL/English LTR, theme/size settings, biometric/passcode lock and conditional clipboard cleanup remain supported.

## Build

Use JDK 17, Gradle 8.13, Android SDK/build tools 35. No NDK/CMake speech build is needed. Python 3.12 with the pinned quantization tools is required only when the INT8 model asset is not already cached:

```bash
python3 -m venv .model-venv
.model-venv/bin/pip install -r tools/model-requirements.txt
export EMROOZ_MODEL_PYTHON="$PWD/.model-venv/bin/python"
gradle :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Build downloads are pinned and SHA-256 checked. Original punctuation weights are stored under the build directory, never packaged. Only the INT8 punctuation graph and tokenizer are bundled. The Apache-2.0 model notice is included in the APK.

## Signing and publication

Set `EMROOZ_RELEASE_STORE_FILE`, `EMROOZ_RELEASE_STORE_PASSWORD`, `EMROOZ_RELEASE_KEY_ALIAS`, and `EMROOZ_RELEASE_KEY_PASSWORD` privately before `:app:assembleRelease`. Without these variables the production release remains unsigned and must not be submitted. Keep the persistent signing key/passwords outside Git and public CI. Updating an installed package requires its existing certificate.

```bash
python3 tools/verify-apk.py app/build/outputs/apk/release/app-release.apk
python3 tools/verify-installation.py app/build/outputs/apk/release/app-release.apk --build-tools "$ANDROID_HOME/build-tools/35.0.0"
```

An independently installed CI build uses `-PemroozPreview` (`com.nameemrooz.journal.preview`, debug certificate). It has the same visible name but is not a store submission. For the privately signed update package delivered after 2.2.2, retain `com.nameemrooz.journal.preview.hotfix` with `-PemroozPreviewApplicationId` and supply the existing `EMROOZ_PREVIEW_*` key variables. `verify-installation.py --application-id ...` checks this explicit update identity while still rejecting a standard debug certificate. A separate production ID does not automatically inherit another package's local notes.

Valid signatures and release flags do not guarantee store approval or absence of Google's unknown-app scan prompt. See [Google's developer guidance](https://developers.google.com/android/play-protect/warning-dev-guidance). No installation bypass or Play Protect disabling is implemented.

## Verification

The Actions workflow builds the arm64 release, runs unit tests/lint and checks actual APK model bytes, ABI, signature/alignment, launcher name, release flags and absence of bundled speech models/runtimes. It separately installs/launches an optimized x86_64 release variant and runs debug journal/lock/draft/Nano, device-unavailable and punctuation-runtime flows on API 26 and 35 emulators. Provider speech accuracy and successful physical microphone recognition are not established by those emulator tests. The core writing suite also runs with `GRADLE_HOME=/path/to/gradle-8.13 bash tools/test-core.sh`.

`tools/verify-punctuation-quality.py` compares the original and quantized graph on a fixed source-preserving smoke set; it is not a gold-label grammar evaluation. Real speech accuracy, especially proper names, varies. A successful build, silence test or small speech smoke set does not establish accuracy on every voice/phone. Actual Nano inference requires compatible physical hardware.

## Privacy

Emrooz has no INTERNET permission. Emrooz does not capture audio into its own files or upload audio itself. Explicit on-device recognition keeps speech processing on the device. The normal device recognition service may send audio to its provider and its retention policy applies; accepting its disclosure is required. Stop does not wait for local editing, service preparation/results have deadlines, and delayed callbacks from discarded sessions are rejected. Notes/drafts remain in app-private local storage, backups are disabled, and screenshots/Recent Apps capture are blocked. The optional device recognition service has its own permissions/network policy, explained before selection.

## Branding

The authoritative bitmap is `branding/source/F7F2EA.png`; see `branding/README.md` and `tools/generate-branding.py`. Uniform resizing preserves the artwork; night mode changes only the approved wordmark color. Density icons, round/adaptive day/night resources and Android 12+ splash assets are included.
