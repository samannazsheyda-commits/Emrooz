# Device speech release implementation plan

> **For agentic workers:** use superpowers:executing-plans for the scoped tasks. The user approved this implementation directly and opted out of further planning questions.

**Goal:** Ship a smaller signed Emrooz APK without bundled speech models, using device speech plus local/Nano editing.

**Architecture:** PRIVATE_OFFLINE now uses Android's explicit on-device recognizer, never the ordinary network-capable factory. SYSTEM_HIGH_ACCURACY remains an explicitly disclosed choice of system-default speech service. Unsupported offline Persian stops cleanly and exposes that choice without silently switching. Editing stays asynchronous and never holds the microphone/listening UI open.

**Tech stack:** existing Kotlin/Compose, Android SpeechRecognizer, ML Kit Nano, INT8 punctuation/ONNX.

**Spec:** user requests in the conversation: remove Whisper/Shenava, phone recognizer, private offline where genuinely supported, local Nano editing, 90–100 MB estimated download, exact name/logo, retain clear/re-record and private signing identity.

## Global constraints
- Display name exactly امروز. Preserve all approved branding bytes and dimensions.
- Keep saved notes, Room schema 1, and hotfix update signing/application ID.
- No automatic transition from offline to network speech, and no app voice files.
- Nano enabled by default only executes where supported; local punctuation remains 58.7 MB.
- Unknown-provider consent cannot promise Google or zero provider retention.

## Review focus
- Offline API/service/language missing: no crash, no network fallback, typing still works.
- Late recognizer callbacks after close/reset/new session: reject; cleared text stays cleared.
- Stop during editing: microphone/listening state stops immediately, not after inference.
- Record again when offline unavailable: no draft loss on canceled network disclosure.
- Final APK: no Whisper/Shenava model/JNI/dependency, 16 KB alignment, exact label/private signature.

## Task 1: device speech engine and controller
Files: SystemSpeechEngine.kt, HybridSpeechController.kt, speech policy helpers and focused tests; delete Whisper speech implementation/segmenter/decoder and their obsolete tests after references removed.
- [ ] Add focused pure support/session tests and observe failures.
- [ ] Add onDeviceOnly flag to existing SystemSpeechEngine constructor, default false; explicit API31 factory, API33 support check, bounded preparation and clear unavailable errors.
- [ ] Use SystemSpeechEngine for both modes. Offline failure never auto-switches; online failure stops cleanly. Listening false delivered independently of editingJob.
- [ ] Reject stale callbacks after cancel/close and per-session listener changes. Run focused/full JVM tests after build setup.

## Task 2: UI, disclosure and Android flow coverage
Files: NameEmroozApp.kt, Android UI tests. Existing enum values remain stable for stored preferences.
- [ ] Replace Whisper labels with device-offline language and accurate privacy copy.
- [ ] Expose an obvious consent-gated system-service choice if offline speech is unavailable; cancellation preserves draft and never starts recording.
- [ ] Retain confirmed clear/re-record; stop/discard and locked/background lifecycle remain safe.
- [ ] Update obsolete native-model tests to device-unavailable and lifecycle/disclosure tests; keep real Room/draft integration checks.

## Task 3: packaging, build and delivery
Files: app/build.gradle.kts, settings.gradle.kts, build verification scripts, CI, notices, release report.
- [ ] Remove whisper-runtime project and all bundled speech assets/tasks. Keep verified punctuation asset and tokenizer; preserve logos/fonts.
- [ ] Bump to 2.3.1 / versionCode 41. Verify no old Whisper caches remain after update.
- [ ] Run full JVM suite, release lint, APK integrity/signing/alignment checks. Fix previous CI ABI mismatch by explicitly building x86_64 release before installing on emulator; run Android flow suite.
- [ ] Build and privately sign hotfix update + production IDs, retain source in git, save APKs/report and deliver real download links with measured size and honest device limitations.
