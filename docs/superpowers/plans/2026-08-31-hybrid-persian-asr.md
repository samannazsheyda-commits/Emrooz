# Emrooz Hybrid Persian ASR Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Emrooz v1.2.0 with unchanged UI and a fully offline hybrid Persian ASR path: Shenava streaming draft plus Whisper Small Q5_1 final transcription.

**Architecture:** Preserve the exact v1.1.9 application reconstruction and all UI/resources. Capture 16 kHz PCM continuously in RAM, run current CTC for live text, release CTC on Stop, transcribe the full in-memory session with pinned whisper.cpp v1.9.3/Small Q5_1 in Persian, then apply conservative Persian cleanup and fall back to CTC on native failure.

**Tech Stack:** Kotlin/Android/Compose, coroutines, sherpa-onnx CTC, whisper.cpp v1.9.3 Android AAR, C/JNI/CMake/NDK, JUnit4, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-08-31-hybrid-persian-asr-design.md`

## Global Constraints

- Start from exact v1.1.9 Quality Fix reconstruction.
- `NameEmroozApp.kt` and every pre-existing `app/src/main/res/**` file must remain byte-identical.
- Raw microphone audio is RAM-only and wiped after finalization.
- No INTERNET permission.
- Keep only arm64-v8a.
- Streaming Shenava model stays pinned unchanged.
- Old offline Shenava final model is not packaged.
- Whisper model is `ggml-small-q5_1.bin`, SHA256 `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`.
- whisper.cpp native code is pinned to `v1.9.3`.
- Version name is `1.2.0`; test-safe application id is `com.nameemrooz.journal.v120hybrid`.

---

### Task 1: Hybrid transcript policy

**Files:**
- Create: `overlay/v120/app/src/main/java/com/nameemrooz/journal/speech/HybridTranscriptPolicy.kt`
- Create: `overlay/v120/tests/HybridTranscriptPolicyTest.kt`

**Interfaces:**
- Consumes: `streamingFinal: String`, `whisperFinal: String`
- Produces: `HybridTranscriptPolicy.choose(streamingFinal: String, whisperFinal: String): String`

- [ ] **Step 1: Write failing tests** for Whisper preference, blank/failure fallback, repeated-garbage rejection, and Persian text acceptance.
- [ ] **Step 2: Run targeted test pre-patch and require failure.**
- [ ] **Step 3: Implement minimal deterministic selection policy.** Strip Whisper special markers/whitespace; prefer sane Whisper text; fall back when blank or obvious repeated hallucination.
- [ ] **Step 4: Run targeted tests and require PASS.**
- [ ] **Step 5: Commit policy + tests.**

### Task 2: Device audio-source selection

**Files:**
- Create: `overlay/v120/app/src/main/java/com/nameemrooz/journal/speech/AudioSourceSelector.kt`
- Create: `overlay/v120/tests/AudioSourceSelectorTest.kt`

**Interfaces:**
- Produces: `AudioSourceSelector.choose(unprocessedSupported: Boolean): Int`

- [ ] **Step 1: Write failing tests** asserting UNPROCESSED when explicitly supported and VOICE_RECOGNITION otherwise.
- [ ] **Step 2: Run tests and require RED.**
- [ ] **Step 3: Implement pure selector.**
- [ ] **Step 4: Run tests and require GREEN.**
- [ ] **Step 5: Commit selector + tests.**

### Task 3: Whisper final recognizer wrapper

**Files:**
- Create: `overlay/v120/app/src/main/java/com/nameemrooz/journal/speech/WhisperFinalRecognizer.kt`

**Interfaces:**
- Consumes: Android `Context`, 16 kHz `FloatArray` PCM.
- Produces: `suspend fun recognize(samples: FloatArray): String`.

- [ ] **Step 1: Write structural test** that requires model path `models/whisper/ggml-small-q5_1.bin`, `WhisperContext.createContextFromAsset`, and RAM-only input.
- [ ] **Step 2: Require structural test failure before overlay.**
- [ ] **Step 3: Implement wrapper** using the official `com.whispercpp.whisper.WhisperContext` AAR API and guaranteed release in `close()`.
- [ ] **Step 4: Re-run structural test.**
- [ ] **Step 5: Commit wrapper.**

### Task 4: Replace final CTC pass in LiveSpeechEngine

**Files:**
- Create/replace: `overlay/v120/app/src/main/java/com/nameemrooz/journal/speech/LiveSpeechEngine.kt`
- Test: existing speech/util tests plus v120 structural tests.

**Interfaces:**
- Preserve all existing `LiveSpeechEngine` constructor callbacks and public methods.

- [ ] **Step 1: Add failing structural assertions** requiring `sessionActive`, audio-source selection, `WhisperFinalRecognizer`, and absence of `ShenavaOfflineRecognizer` from the engine.
- [ ] **Step 2: Require RED on baseline.**
- [ ] **Step 3: Implement engine**: independent capture/decode, full PCM retained in RAM, stop drains queue, streaming final emitted quickly, streaming model released, Whisper final run, policy selection, cleanup/name correction, PCM wiped, streaming model re-prepared.
- [ ] **Step 4: Add finalization-state gate** so `start()` cannot begin a new session during finalization.
- [ ] **Step 5: Run targeted tests and compile checks.**
- [ ] **Step 6: Commit engine.**

### Task 5: Pin and build official whisper.cpp Android backend

**Files:**
- Create: `overlay/v120/native/patch_whisper_jni.py`
- Modify in CI only: pinned `ggml-org/whisper.cpp` v1.9.3 checkout.

**Interfaces:**
- Produces: `app/libs/whispercpp-fa-release.aar` containing arm64 native whisper library and `com.whispercpp.whisper` Kotlin API.

- [ ] **Step 1: Clone exact tag `v1.9.3` in CI and verify tag/commit.**
- [ ] **Step 2: Patch official JNI minimally**: Persian language `fa`, transcribe mode, no timestamps, beam-search final decode, suppress blank/non-speech tokens, no translation.
- [ ] **Step 3: Build official Android `:lib:assembleRelease`.**
- [ ] **Step 4: Verify AAR contains `classes.jar` and arm64 native libraries.**
- [ ] **Step 5: Copy AAR to `app/libs/whispercpp-fa-release.aar`.**

### Task 6: Build metadata and model packaging

**Files:**
- Create: `overlay/v120/apply_v120.py`
- Modify at build time: `app/build.gradle.kts`

**Interfaces:**
- Adds local AAR dependency and `bin` no-compress packaging.

- [ ] **Step 1: Write build-script assertions** for v1.1.9 metadata before patch.
- [ ] **Step 2: Patch application id/version and local AAR dependency.**
- [ ] **Step 3: Add Android asset no-compress for `.bin` without changing UI/resources.**
- [ ] **Step 4: Download only pinned streaming CTC model and Whisper Small Q5_1; verify SHA256.**
- [ ] **Step 5: Assert old offline CTC model directory is absent.**

### Task 7: UI/resource immutability gate

**Files:**
- Modify: `.github/workflows/build-v120-hybrid.yml`

- [ ] **Step 1: Hash `NameEmroozApp.kt` and all pre-existing `app/src/main/res/**` before v120 overlay.**
- [ ] **Step 2: Apply v120 runtime/build overlay.**
- [ ] **Step 3: Rehash and require byte-identical UI/resources.**
- [ ] **Step 4: Require only approved speech/build files changed.**

### Task 8: Regression, lint, native, and acoustic smoke verification

**Files:**
- Create: `overlay/v120/tests/*`
- Modify: `.github/workflows/build-v120-hybrid.yml`

- [ ] **Step 1: Run v120 targeted unit tests.**
- [ ] **Step 2: Run complete existing unit suite.**
- [ ] **Step 3: Run Android lint.**
- [ ] **Step 4: Run a pinned real Persian public WAV through the same Whisper Small Q5_1 backend/CLI and require non-empty Persian transcription. Label this a smoke test, not WER proof.**
- [ ] **Step 5: Build release APK and sign it.**
- [ ] **Step 6: Verify APK signature, package/version, RECORD_AUDIO present, INTERNET absent.**
- [ ] **Step 7: Verify APK contains Whisper Small Q5_1 and streaming CTC, and does not contain the old offline CTC model.**
- [ ] **Step 8: Verify APK size is below 430 MiB.**
- [ ] **Step 9: Publish release asset plus SHA256 only after every gate passes.**

### Task 9: Completion review

- [ ] **Step 1: Re-run verification evidence from the final workflow run.**
- [ ] **Step 2: Review changed files against spec; no UI drift.**
- [ ] **Step 3: Report only verified claims, and explicitly state that microphone accuracy still requires physical-device testing.**
