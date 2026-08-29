# Emrooz Final UI + Speech Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a verified Android release of «امروز» with professional cream/olive UI, distinct splash/home copy, editable content-aware titles, lower live transcription latency, and stronger conservative offline Persian correction.

**Architecture:** Preserve the existing offline Shenava streaming recognizer and Room schema. Add focused pure-Kotlin title/colloquial correction units, integrate them at final-transcript/save boundaries, then patch the existing Compose UI and release workflow. Keep large lexicon loading off the recognizer-ready critical path.

**Tech Stack:** Kotlin, Jetpack Compose Material3, Room, sherpa-onnx OnlineRecognizer / NeMo CTC, GitHub Actions, Android SDK 35.

**Spec:** `docs/superpowers/specs/2026-08-29-final-ui-stt-design.md`

## Global Constraints
- Android minSdk 26; target/compile SDK 35.
- Speech, title generation, name correction, and editing remain fully offline.
- No INTERNET permission.
- Do not save microphone audio.
- Preserve FLAG_SECURE and existing journal data model.
- Title is editable before save and in detail; user-edited title wins.
- Splash copy and home copy must be different.
- Launcher icon has no leaf decoration.

---

### Task 1: Capture exact current reconstructed source

**Files:**
- Modify: `.github/workflows/diagnose-ui.yml`
- Inspect: reconstructed `app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt`
- Inspect: reconstructed `app/src/main/java/com/nameemrooz/journal/ui/AppViewModel.kt`
- Inspect: reconstructed `app/src/main/java/com/nameemrooz/journal/util/TitleGenerator.kt`

**Interfaces:**
- Consumes: current payload + existing `fixes/app` overlays and `fixes/apply_ui_patch.py`.
- Produces: exact source snapshot used for safe textual patching.

- [ ] Update diagnostic workflow to reconstruct source, apply current overlays/patch, and upload the relevant Kotlin files as a source artifact.
- [ ] Run workflow and download the artifact.
- [ ] Verify exact Home, splash, TranscriptEditor, Detail, save/update signatures before editing.

### Task 2: Content-aware editable title logic (TDD)

**Files:**
- Create/Modify: `fixes/app/src/main/java/com/nameemrooz/journal/util/TitleGenerator.kt`
- Modify: `fixes/app/src/main/java/com/nameemrooz/journal/ui/AppViewModel.kt`
- Create: `fixes/app/src/test/java/com/nameemrooz/journal/util/TitleGeneratorTest.kt`

**Interfaces:**
- Produces: `TitleGenerator.generate(text: String): String` returning a concise transcript-derived title.
- Produces: `AppViewModel.save(text: String, title: String)` preserving nonblank user title and only generating a fallback when title is blank.

- [ ] Write failing tests for a Soheil meeting title, family/home topic title, transcript-derived fallback, and explicit user-title preservation at the pure helper boundary.
- [ ] Run release unit tests and confirm the new title tests fail for the expected fixed-title behavior.
- [ ] Implement conservative keyword/name extraction and 3–6-word title templates.
- [ ] Update save path to accept editable title.
- [ ] Run tests until all title tests pass.

### Task 3: Colloquial Persian correction (TDD)

**Files:**
- Modify: `fixes/app/src/main/java/com/nameemrooz/journal/util/PersianText.kt`
- Create: `fixes/app/src/test/java/com/nameemrooz/journal/util/PersianColloquialEditingTest.kt`

**Interfaces:**
- Consumes: raw/partial/final ASR text.
- Produces: normalized conversational Persian without semantic paraphrase.

- [ ] Add failing cases for common joined/spaced spoken verbs: `میخوام`, `نمیخوام`, `میرم`, `میریم`, `میگم`, `نمیدونم`, plus an ordinary-word preservation case.
- [ ] Confirm RED with unit tests.
- [ ] Add narrow phrase/token corrections only for common conversational verb forms and known recurrent ASR variants.
- [ ] Run the full PersianText test suite and confirm intentional `نه نه` still survives.

### Task 4: Lower perceived STT latency (TDD where pure configuration is testable)

**Files:**
- Create: `fixes/app/src/main/java/com/nameemrooz/journal/speech/SpeechTuning.kt`
- Modify: `fixes/app/src/main/java/com/nameemrooz/journal/speech/LiveSpeechEngine.kt`
- Modify: `fixes/app/src/main/java/com/nameemrooz/journal/speech/ShenavaRecognizer.kt`
- Create: `fixes/app/src/test/java/com/nameemrooz/journal/speech/SpeechTuningTest.kt`

**Interfaces:**
- Produces constants: `READ_SAMPLES = 2048`, `FINAL_SILENCE_SAMPLES = 1600` (unless verification demonstrates lost final words, then use 2400).
- Recognizer readiness is emitted immediately after model load; lexicon warm-up continues independently.

- [ ] Write failing tests asserting the latency tuning constants and a maximum 128 ms input block at 16 kHz.
- [ ] Confirm RED.
- [ ] Move constants into SpeechTuning and use 2048-sample microphone reads.
- [ ] Emit ready after recognizer construction, start name-lexicon warm-up in a separate background coroutine, and do not fail STT readiness if lexicon warm-up fails.
- [ ] Reduce final silence from current 3200 samples and keep final decode flush.
- [ ] Run all unit tests.

### Task 5: Professional Compose UI and editable title

**Files:**
- Modify: `fixes/apply_ui_patch.py`
- If exact source warrants a focused overlay, create: `fixes/app/src/main/java/com/nameemrooz/journal/ui/JournalHomeComponents.kt`

**Interfaces:**
- Home save callback becomes `(text: String, title: String) -> Unit`.
- Draft title state is generated after recording finalizes, editable via a Persian RTL text field, and reset after successful save.

- [ ] Patch splash to show `امروز`, approved line mark, and `هر چیزی که بگی مهمه` only.
- [ ] Patch home header to `از امروزت بگو` and supporting copy `حتی چیزهای کوچیک هم مهم هستن. فقط بگو، من برات می‌نویسم و نگه می‌دارم.`
- [ ] Make Persian-digit time visually primary and date secondary.
- [ ] While listening show only `دارم می‌نویسم…` plus the existing subtle conversion glow; no waveform.
- [ ] After final transcript, generate and show editable title beneath `نامه امروزت`.
- [ ] Redesign actions: dominant olive `ذخیره نامه`; compact aligned `کپی` and restrained red `پاک کردن`.
- [ ] Make detail title editable alongside body.
- [ ] Ensure spacing/radii/type sizes are consistent and avoid raw default Material controls where visible.

### Task 6: Approved splash mark and launcher icon

**Files:**
- Modify: `.github/workflows/build-live-stt-fix.yml`
- Generate during CI into: `app/src/main/res/drawable-nodpi/` and `mipmap-*` from checked-in textual/base64 assets or deterministic vector resources.

**Interfaces:**
- Splash mark: solid dot → short wave → two horizontal strokes in olive.
- Launcher icon: cream rounded-square with `امروز` + line mark; no leaf decoration.

- [ ] Encode only the approved non-font image assets needed for CI reconstruction; do not add user font binaries to the public repo.
- [ ] Generate launcher density assets or adaptive-icon foreground/background deterministically.
- [ ] Verify all launcher resource references resolve at build time.

### Task 7: Release metadata, CI verification, and APK handoff

**Files:**
- Modify: `.github/workflows/build-live-stt-fix.yml`

**Interfaces:**
- Release version: `1.1.3`, versionCode `16`.
- Artifact name: `Emrooz-Final-v1.1.3`.

- [ ] Trigger workflow on `feat/final-ui-stt-v113` and bump version metadata.
- [ ] Run `:app:testReleaseUnitTest` and require 0 failing tests.
- [ ] Run `:app:assembleRelease` and require exit 0.
- [ ] Verify APK with `unzip -t`, `apksigner verify --verbose --print-certs`, `aapt dump permissions`, and `aapt dump badging`.
- [ ] Assert RECORD_AUDIO present and INTERNET absent.
- [ ] Assert model, tokens, offline name lexicons, and arm64 sherpa JNI library are present.
- [ ] Verify versionName `1.1.3` and the intended applicationId/package.
- [ ] Download the artifact, extract the APK, run a second local `unzip -t` and SHA-256 check, then provide the exact APK file.

## Self-review
- Spec coverage: splash/home copy, editable title, transcript-derived title, professional actions, latency, names/colloquial text, offline/privacy, and release verification all map to explicit tasks.
- Placeholder scan: no TBD/TODO implementation placeholders.
- Type consistency: title generator returns String; save accepts text + title; JournalEntry schema remains unchanged.