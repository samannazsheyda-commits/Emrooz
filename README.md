# امروز — Emrooz

An Android Persian/English voice journal with local notes, live transcription and optional Google on-device editing.

The maintained Android application now lives in **`app/`**. The `payload/`, `approved_ui/`, `hybrid_v203/`, and `hybrid_v204/` directories are historical reconstruction inputs, not the active source tree. Open this repository root in Android Studio.

## Features

- Bundled offline Persian Shenava RNNT speech recognition with the corrected `nemo_transducer` profile.
- Optional Android device speech service, with an explicit disclosure before selecting it.
- Live transcript, repeated recording appended to the current draft, manual editing, and local draft recovery.
- Room-backed notes, edit/archive/restore/delete, and date/time navigation.
- Persian RTL / English LTR, Abar/Lato fonts, day/night themes and UI size settings.
- Biometric-first lock with a fallback passcode; journal content is hidden when the app leaves the foreground.
- Optional ML Kit Prompt / Gemini Nano writing enhancement, ON by default (can be switched off). Text only; no audio is passed to it.
- Local fallback for unsupported devices, model downloads, failures, unsafe edits and timeouts. Names, numbers, links and emphasis are protected.

## Build and verify

Use JDK 17, Gradle 8.13 and Android SDK 35 / build tools 35.0.0. Dependencies and model assets require internet during the build. The app itself has no INTERNET permission. Six model/tokenizer assets are checksum-verified before packaging; they are not committed to Git.

```bash
gradle :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

The production package remains `com.nameemrooz.journal`, with database schema version 1 and version 2.2.1 / code 37. Its release APK is unsigned until the original release key is supplied through a private signing workflow.

Build an independently installable, signed preview without replacing an existing production installation:

```bash
gradle -PemroozPreview :app:assembleRelease
```

That preview uses `com.nameemrooz.journal.preview` and the build machine's debug certificate. Keep the production app installed to retain its existing notes. No private release key is stored in this repository or uploaded with verification artifacts.

For a persistent private preview certificate, set `EMROOZ_PREVIEW_STORE_FILE`,
`EMROOZ_PREVIEW_STORE_PASSWORD`, `EMROOZ_PREVIEW_KEY_ALIAS`, and
`EMROOZ_PREVIEW_KEY_PASSWORD` in the build environment. Keep the key and all
passwords outside Git and public CI artifacts. `-PemroozPreviewApplicationId=...`
can give a separately signed preview its own package; `-PemroozPreviewLabel=...`
can distinguish its launcher label. A separate package avoids requiring an
uninstall of a previous preview and losing its local notes.

For an x86_64 emulator and real UI tests:

```bash
gradle -PemroozPreview -PemroozAbi=x86_64 :app:connectedDebugAndroidTest
```

The `Emrooz complete Android verification` Actions workflow builds the arm64 preview, runs unit tests and lint, verifies packaged hashes/signature/permissions, and tests real draft/settings/Google-toggle/save/lock flows on Android API 26 and 35 emulators. Its downloadable artifact contains the APK and verification reports. The core writing regression suite can also run without the Android SDK using `GRADLE_HOME=/path/to/gradle-8.13 bash tools/test-core.sh`.

## Google editor availability

The default is enabled; Settings includes **ویراستاری دقیق‌تر با مدل روی دستگاه / Enhanced on-device editing**. Emrooz displays the model's runtime availability. Device AICore services may need to provision/download the model separately. Unsupported or unavailable models leave the local editor active. No cloud editing fallback or API key is used.

Successful Gemini Nano inference and Persian correction quality must be validated on a supported physical device. Emulator fallback tests and a successful build do not establish hardware support or actual Nano inference quality.

## Privacy

Microphone samples remain in bounded RAM queues and are wiped after use. Emrooz never saves audio. Notes and draft text stay in private local app storage; backups are disabled and screenshots/Recent Apps capture are blocked. The optional Android speech service can use its own internet access outside Emrooz, as disclosed in Settings. Automatic clipboard cleanup is conditional: it clears only the copied Emrooz text if another app has not replaced it.

## Logo and splash

Approved copper artwork is preserved in `branding/source/`. See
`branding/README.md` and `tools/generate-branding.py` for reproducible density
icons (48/72/96/144/192px plus the 512px store asset), safe adaptive artwork,
and aspect-preserving light/dark splash resources.

The ML Kit runtime dependency is retained because the SDK links its classes
before availability checking. The manifest removes the SDK's INTERNET
permission; removing runtime classes instead caused the Settings crash.
