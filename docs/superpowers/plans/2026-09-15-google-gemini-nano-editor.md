# Google Gemini Nano Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in, on-device Google Gemini Nano writing enhancer to Emrooz while preserving the current local editor as a guaranteed fallback and never weakening the no-cloud/no-audio-persistence contract.

**Architecture:** Add a small `WritingEnhancer` provider boundary. `GeminiNanoWritingEnhancer` wraps ML Kit Prompt API / AICore behind a testable adapter, while `LocalWritingEnhancer` keeps the existing deterministic cleanup and local punctuation model. `WritingEnhancementCoordinator` invokes Gemini only for stable/final text, applies a deterministic safety gate, and falls back to local text on unsupported devices, download-not-ready state, timeout, quota/error, unsafe output, or cancellation.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, coroutines, ML Kit GenAI Prompt API `com.google.mlkit:genai-prompt:1.0.0-beta4`, Android AICore / Gemini Nano, existing ONNX Runtime punctuation model, JUnit 4, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-15-google-gemini-nano-editor-design.md`

## Global Constraints

- Minimum Android API remains 26; compile/target SDK remain 35 for this release.
- Shenava v1.5 RNNT remains the guaranteed offline ASR path.
- Android/System speech remains optional and independent from the writing enhancer.
- Google editing is opt-in and defaults OFF.
- Gemini Nano receives text only; it never receives Emrooz's raw audio buffer.
- No audio is persisted to disk.
- No `android.permission.INTERNET` may appear in the app manifest or final APK.
- Cloud Gemini, server grammar APIs, Gboard private APIs, and semantic rewriting are out of scope.
- Existing Abar/Lato typography, approved UI, archive behavior, biometric-first lock, and transcript glow remain unchanged.
- The removed voice command `اصلاح کن` must not return.
- Google inference runs only on stable/final segments and never on partial ASR callbacks.
- Any unsupported/error/timeout/unsafe result must preserve the current local text exactly.
- Do not label Gemini Nano editing production-verified until a supported physical device completes at least one successful on-device enhancement.

---

## File Structure

New focused files under `app/src/main/java/com/nameemrooz/journal/writing/`:

- `WritingEnhancer.kt` — provider interface and provider-result/status types.
- `WritingEditSafety.kt` — deterministic acceptance rules for a Gemini candidate.
- `NanoPromptClient.kt` — small adapter interface around ML Kit Prompt API so policy is unit-testable without AICore hardware.
- `MlKitNanoPromptClient.kt` — concrete ML Kit/AICore implementation.
- `GeminiNanoWritingEnhancer.kt` — prompt construction, timeout, status handling, candidate cleanup.
- `LocalWritingEnhancer.kt` — current local `TextEditor` path exposed behind the common interface.
- `WritingEnhancementCoordinator.kt` — opt-in provider selection and fail-open fallback.

Existing files to modify:

- `hybrid_v203/build.gradle.kts` — add ML Kit Prompt dependency and bump app version to 2.0.4 / versionCode 34 in the staged overlay.
- `hybrid_v203/SettingsStore.kt` — persist opt-in toggle, default false.
- `hybrid_v203/HybridSpeechController.kt` — route stable/final text through the coordinator only, never partial text.
- `hybrid_v203/NameEmroozApp.part00` — pass the setting into the speech controller.
- `hybrid_v203/NameEmroozApp.part03` — add the settings toggle and capability state.
- `hybrid_v203/verify_hybrid_speech_contract.sh` — add privacy/dependency/feature-contract checks.
- `.github/workflows/build-v203-release.yml` or successor `build-v204-release.yml` — build, merged-manifest privacy check, unsupported-emulator fallback smoke, APK integrity.

Tests under `app/src/test/java/com/nameemrooz/journal/writing/`:

- `WritingEditSafetyTest.kt`
- `WritingEnhancementCoordinatorTest.kt`
- `GeminiNanoWritingEnhancerTest.kt`

---

### Task 1: Lock Down Gemini Edit Safety

**Files:**
- Create: `hybrid_v204/WritingEnhancer.kt`
- Create: `hybrid_v204/WritingEditSafety.kt`
- Create: `hybrid_v204/WritingEditSafetyTest.kt`

**Interfaces:**
- Produces:
  - `interface WritingEnhancer { suspend fun enhance(text: String, languageTag: String): EnhancementResult }`
  - `sealed interface EnhancementResult { data class Applied(val text: String); data class Unavailable(val reason: String); data class Failed(val reason: String) }`
  - `object WritingEditSafety { fun accept(original: String, candidate: String): Boolean }`

- [ ] **Step 1: Write failing safety tests**

Create tests covering the exact product constraints:

```kotlin
class WritingEditSafetyTest {
    @Test fun accepts_punctuation_and_half_space_only() {
        assertTrue(WritingEditSafety.accept("من امروز رفتم خونه", "من امروز رفتم خونه."))
        assertTrue(WritingEditSafety.accept("می روم خونه", "می‌روم خونه"))
    }

    @Test fun rejects_number_change() {
        assertFalse(WritingEditSafety.accept("ساعت ۸ رسیدم", "ساعت ۹ رسیدم"))
    }

    @Test fun rejects_latin_token_change() {
        assertFalse(WritingEditSafety.accept("به WhatsApp پیام دادم", "به Whatsapp پیام دادم"))
    }

    @Test fun preserves_repeated_emphasis() {
        assertFalse(WritingEditSafety.accept("خیلی خیلی خسته بودم", "خیلی خسته بودم"))
    }

    @Test fun rejects_insertions_and_deletions() {
        assertFalse(WritingEditSafety.accept("امروز رفتم بیرون", "امروز با دوستم رفتم بیرون"))
        assertFalse(WritingEditSafety.accept("امروز خیلی خوب بود", "امروز خوب بود"))
    }

    @Test fun allows_small_same_position_persian_spelling_fix() {
        assertTrue(WritingEditSafety.accept("امروز هوا عاللی بود", "امروز هوا عالی بود"))
    }

    @Test fun rejects_large_or_widespread_lexical_rewrite() {
        assertFalse(WritingEditSafety.accept("امروز هوا خوب بود و رفتم بیرون", "امروز روز فوق العاده ای داشتم و بیرون قدم زدم"))
    }
}
```

- [ ] **Step 2: Run the new test and verify RED**

Run:

```bash
gradle --no-daemon :app:testDebugUnitTest --tests '*WritingEditSafetyTest*'
```

Expected: FAIL because `WritingEditSafety` does not exist.

- [ ] **Step 3: Implement the minimum deterministic validator**

Rules:

```text
1. Trim/normalize whitespace, Persian ي/ى→ی and ك→ک for comparison only.
2. Preserve all numeric runs exactly, including Persian/Arabic/Latin digits.
3. Preserve all Latin/alphanumeric mixed tokens exactly and in the same order.
4. Preserve consecutive repeated normalized lexical tokens.
5. Tokenize after stripping punctuation and ZWNJ differences.
6. Token count must remain identical; no insertions/deletions.
7. Unchanged tokens pass.
8. Changed Persian token at the same position may pass only when:
   - both tokens contain Persian letters only after normalization,
   - edit distance <= 1 for token length <= 5, or <= 2 for token length > 5,
   - total changed lexical tokens <= max(1, floor(tokenCount * 0.20)).
9. Empty candidate or suspicious structural mismatch fails.
```

Use a small internal Levenshtein implementation; do not add a dependency.

- [ ] **Step 4: Run the safety tests and existing local safety tests**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests '*WritingEditSafetyTest*' --tests '*TextEditorSafetyTest*'
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add hybrid_v204/WritingEnhancer.kt hybrid_v204/WritingEditSafety.kt hybrid_v204/WritingEditSafetyTest.kt
git commit -m "feat: add safe writing enhancement boundary"
```

---

### Task 2: Add a Testable ML Kit / AICore Adapter

**Files:**
- Modify: `hybrid_v203/build.gradle.kts` into staged `hybrid_v204/build.gradle.kts`
- Create: `hybrid_v204/NanoPromptClient.kt`
- Create: `hybrid_v204/MlKitNanoPromptClient.kt`
- Create: `hybrid_v204/GeminiNanoWritingEnhancer.kt`
- Create: `hybrid_v204/GeminiNanoWritingEnhancerTest.kt`

**Interfaces:**
- Consumes: `WritingEnhancer`, `EnhancementResult`, `WritingEditSafety.accept(...)` from Task 1.
- Produces:

```kotlin
enum class NanoStatus { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

interface NanoPromptClient : AutoCloseable {
    suspend fun status(): NanoStatus
    suspend fun requestDownload(): NanoStatus
    suspend fun generate(systemInstruction: String, prompt: String): String
}
```

- [ ] **Step 1: Write RED tests using a fake `NanoPromptClient`**

Cover:

```kotlin
@Test fun unavailable_returns_unavailable_without_generation()
@Test fun downloadable_requests_download_and_falls_back_for_current_call()
@Test fun available_safe_candidate_is_applied()
@Test fun unsafe_candidate_is_rejected()
@Test fun generation_exception_returns_failed()
@Test fun timeout_returns_failed_and_keeps_input()
```

The fake client records `generate` calls so unsupported devices prove that inference is never attempted.

- [ ] **Step 2: Run tests and verify RED**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests '*GeminiNanoWritingEnhancerTest*'
```

Expected: FAIL because the Nano classes do not exist.

- [ ] **Step 3: Add the official Prompt API dependency**

In `dependencies`:

```kotlin
implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
```

Keep `minSdk = 26`. Bump:

```kotlin
versionCode = 34
versionName = "2.0.4"
```

Do not add `INTERNET` permission.

- [ ] **Step 4: Implement `MlKitNanoPromptClient` with current ML Kit API**

Use the official entry point:

```kotlin
private val model = Generation.getClient()
```

Map `model.checkStatus()` values into `NanoStatus`. For `DOWNLOADABLE`, call the SDK's `download(...)` API but return immediately to local fallback for the current edit instead of blocking recording/saving.

For generation use a short system instruction and deterministic request:

```kotlin
val request = generateContentRequest(
    SystemInstruction(SYSTEM_INSTRUCTION),
    TextPart(prompt)
) {
    temperature = 0.0f
    topK = 1
    candidateCount = 1
    maxOutputTokens = 512
    enableThinking = false
}
val response = model.generateContent(request)
return response.candidates.firstOrNull()?.text.orEmpty()
```

`SYSTEM_INSTRUCTION` must remain under 150 words and say, in substance: return only corrected text; preserve meaning, word order unless required for spelling, names, numbers, Latin tokens, repetitions, and language; only spelling, Persian character normalization, half-space, whitespace, and punctuation are allowed.

- [ ] **Step 5: Implement `GeminiNanoWritingEnhancer`**

Use `withTimeoutOrNull(2500)` around status/generation. Never include instructions in the user text that ask for rewriting/style. Strip only obvious wrappers such as matching leading/trailing quotes or a single Markdown code fence; do not parse prose explanations into accepted text.

Flow:

```kotlin
when (client.status()) {
    NanoStatus.UNAVAILABLE -> EnhancementResult.Unavailable("unsupported")
    NanoStatus.DOWNLOADING -> EnhancementResult.Unavailable("downloading")
    NanoStatus.DOWNLOADABLE -> {
        client.requestDownload()
        EnhancementResult.Unavailable("download_requested")
    }
    NanoStatus.AVAILABLE -> {
        val candidate = client.generate(SYSTEM_INSTRUCTION, text)
        if (WritingEditSafety.accept(text, candidate)) EnhancementResult.Applied(candidate)
        else EnhancementResult.Failed("unsafe_candidate")
    }
}
```

- [ ] **Step 6: Run unit tests and compile**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests '*GeminiNanoWritingEnhancerTest*' :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 7: Verify privacy at merged-manifest level**

```bash
gradle --no-daemon :app:processDebugMainManifest
! grep -R "android.permission.INTERNET" app/build/intermediates/merged_manifests app/build/intermediates/merged_manifest 2>/dev/null
```

If the dependency introduces `INTERNET`, stop the feature integration and do not use manifest removal tricks until the privacy conflict is reviewed.

- [ ] **Step 8: Commit**

```bash
git add hybrid_v204/build.gradle.kts hybrid_v204/NanoPromptClient.kt hybrid_v204/MlKitNanoPromptClient.kt hybrid_v204/GeminiNanoWritingEnhancer.kt hybrid_v204/GeminiNanoWritingEnhancerTest.kt
git commit -m "feat: add on-device Gemini Nano writing adapter"
```

---

### Task 3: Add Coordinator and Guaranteed Local Fallback

**Files:**
- Create: `hybrid_v204/LocalWritingEnhancer.kt`
- Create: `hybrid_v204/WritingEnhancementCoordinator.kt`
- Create: `hybrid_v204/WritingEnhancementCoordinatorTest.kt`
- Modify staged copy: `hybrid_v204/HybridSpeechController.kt`

**Interfaces:**
- Produces:

```kotlin
class WritingEnhancementCoordinator(
    private val local: LocalWritingEnhancer,
    private val nano: WritingEnhancer?,
    private val nanoEnabled: Boolean,
) {
    suspend fun stable(text: String, languageTag: String): String
    suspend fun final(text: String, languageTag: String): String
}
```

- [ ] **Step 1: Write RED coordinator tests**

Required cases:

```kotlin
@Test fun disabled_never_calls_nano()
@Test fun unavailable_uses_local_result()
@Test fun failed_uses_local_result()
@Test fun unsafe_google_output_never_replaces_local_result()
@Test fun safe_google_output_is_then_locally_punctuated()
@Test fun fallback_never_returns_blank_when_input_is_nonblank()
```

Use fake local/nano enhancers and call counters.

- [ ] **Step 2: Verify RED**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests '*WritingEnhancementCoordinatorTest*'
```

- [ ] **Step 3: Implement local provider**

`LocalWritingEnhancer` wraps the existing `TextEditor` behavior so the current local punctuation path stays authoritative as fallback. Do not duplicate `PersianEditorial` logic.

- [ ] **Step 4: Implement coordinator**

Recommended order for stable Persian text:

```text
1. deterministic local live cleanup
2. optional Nano enhancement when enabled/available
3. safety acceptance inside Nano enhancer
4. existing local punctuation model
5. deterministic final cleanup
```

If Nano returns `Unavailable` or `Failed`, use step 1 input directly for the local punctuation path.

- [ ] **Step 5: Integrate only at stable callback in `HybridSpeechController`**

Add constructor parameter:

```kotlin
private val enhancedEditingEnabled: Boolean = false
```

Keep `textCallback` exactly local/fast. Replace only the stable coroutine's `editor.stable(...)` call with `coordinator.stable(...)`.

The microphone and partial transcript must not wait for Gemini Nano.

- [ ] **Step 6: Run controller and writing tests**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests '*WritingEnhancementCoordinatorTest*' --tests '*StableDisplayComposerTest*' --tests '*TranscriptContinuityTest*'
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add hybrid_v204/LocalWritingEnhancer.kt hybrid_v204/WritingEnhancementCoordinator.kt hybrid_v204/WritingEnhancementCoordinatorTest.kt hybrid_v204/HybridSpeechController.kt
git commit -m "feat: route stable transcript through optional Nano editor"
```

---

### Task 4: Add Explicit Opt-In Setting and Capability UI

**Files:**
- Modify staged copy: `hybrid_v204/SettingsStore.kt`
- Modify staged copy: `hybrid_v204/NameEmroozApp.part00`
- Modify staged copy: `hybrid_v204/NameEmroozApp.part03`

**Interfaces:**
- Produces:

```kotlin
val enhancedOnDeviceEditingEnabled: Flow<Boolean>
suspend fun setEnhancedOnDeviceEditingEnabled(value: Boolean)
```

Default must be `false`.

- [ ] **Step 1: Add DataStore key and default-off flow**

```kotlin
private val enhancedOnDeviceEditingKey = booleanPreferencesKey("enhanced_on_device_editing")
val enhancedOnDeviceEditingEnabled = context.dataStore.data.map { it[enhancedOnDeviceEditingKey] ?: false }
suspend fun setEnhancedOnDeviceEditingEnabled(value: Boolean) = context.dataStore.edit { it[enhancedOnDeviceEditingKey] = value }
```

- [ ] **Step 2: Pass the toggle into `HybridSpeechController`**

In `Home`:

```kotlin
val enhancedEditing by store.enhancedOnDeviceEditingEnabled.collectAsState(initial = false)
```

Include it in the `remember(...)` key and constructor:

```kotlin
enhancedEditingEnabled = enhancedEditing
```

Changing the setting recreates the controller cleanly.

- [ ] **Step 3: Add the settings row**

Add under speech/writing settings:

```text
FA title: ویراستاری دقیق‌تر با مدل روی دستگاه
FA helper: اگر مدل گوگل روی گوشی پشتیبانی شود، املا و نیم‌فاصله با حفظ معنی بهتر می‌شود. در غیر این صورت ویراستار آفلاین امروز استفاده می‌شود.
EN title: Enhanced on-device editing
EN helper: Uses the device's supported Google on-device model when available; otherwise Emrooz keeps its local editor.
```

The switch is OFF by default. It may be enabled even if the model is currently downloadable/not-ready, because local fallback remains immediate; show a small state label rather than blocking the toggle.

Do not add an error dialog and do not alter the existing speech-engine disclosure.

- [ ] **Step 4: Compile Compose UI**

```bash
gradle --no-daemon :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add hybrid_v204/SettingsStore.kt hybrid_v204/NameEmroozApp.part00 hybrid_v204/NameEmroozApp.part03
git commit -m "feat: add opt-in on-device editing setting"
```

---

### Task 5: Strengthen Contract Tests and Release Overlay

**Files:**
- Create: `hybrid_v204/verify_google_editor_contract.sh`
- Modify/create: `.github/workflows/build-v204-release.yml`
- Stage all v2.0.4 source overlays required by the build workflow.

**Interfaces:**
- Consumes all files from Tasks 1–4.
- Produces a reproducible v2.0.4 release build.

- [ ] **Step 1: Write a contract script that initially fails before the v2.0.4 overlay is complete**

Require these facts:

```bash
rg -q 'com.google.mlkit:genai-prompt:1.0.0-beta4' app/build.gradle.kts
rg -q 'enhanced_on_device_editing' app/src/main/java/com/nameemrooz/journal/data/SettingsStore.kt
rg -q 'GeminiNanoWritingEnhancer' app/src/main/java/com/nameemrooz/journal/writing
rg -q 'WritingEditSafety' app/src/main/java/com/nameemrooz/journal/writing
! rg -q 'android.permission.INTERNET' app/src/main/AndroidManifest.xml
! rg -q 'اصلاح کن' app/src/main/java
```

- [ ] **Step 2: Update the CI overlay to reconstruct v2.0.4**

The workflow must copy the new writing files, updated settings/controller/UI/build file, and tests before running Gradle.

- [ ] **Step 3: Run full unit/lint/release build**

```bash
gradle --no-daemon clean :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Expected: PASS.

- [ ] **Step 4: Verify APK privacy and contents**

Use `aapt dump permissions` and fail if INTERNET is present. Verify versionCode 34/versionName 2.0.4, arm64-only release ABI, Shenava model hashes, local punctuation model hash, zip integrity, zipalign, and signature.

- [ ] **Step 5: Commit**

```bash
git add hybrid_v204 .github/workflows/build-v204-release.yml
git commit -m "ci: verify v2.0.4 on-device editor release"
```

---

### Task 6: Runtime Regression and Unsupported-Device Fallback Smoke

**Files:**
- Modify: `.github/workflows/build-v204-release.yml`
- Reuse: `.github/workflows/runtime-debug-v203.yml` evidence to resolve the pre-existing process-exit issue before final delivery if it still reproduces.

**Interfaces:**
- Produces runtime evidence that the app stays alive with Nano unavailable and that the new dependency does not cause startup failure.

- [ ] **Step 1: Build x86_64 debug only for emulator smoke**

As in the current workflow, temporarily change ABI filter to `x86_64`, build debug, and install it on Android API 35 emulator.

- [ ] **Step 2: Launch `MainActivity` directly and capture diagnostics even on failure**

```bash
adb install -r app-debug.apk
adb logcat -c
adb shell am force-stop com.nameemrooz.journal
adb shell am start -W -n com.nameemrooz.journal/.MainActivity
sleep 20
adb logcat -d > runtime-logcat.txt
adb shell dumpsys activity exit-info com.nameemrooz.journal > exit-info.txt || true
adb shell pidof com.nameemrooz.journal > pid.txt || true
```

Only after capture, assert that PID is non-empty and no app `FATAL EXCEPTION` exists.

- [ ] **Step 3: Confirm unsupported AICore/Nano does not block startup**

The emulator is not expected to provide a production Gemini Nano model. The setting defaults OFF, so startup must not initialize/download Nano. Even if enabled in a dedicated test harness, `UNAVAILABLE` must return local text rather than crash.

- [ ] **Step 4: If the current 20-second process exit reproduces, fix its actual logged root cause before calling the APK final**

Use the existing runtime-debug workflow output; do not weaken the PID assertion or shorten the wait to hide the failure.

- [ ] **Step 5: Commit any root-cause runtime fix separately**

```bash
git add <actual files changed by the root-cause fix>
git commit -m "fix: keep Emrooz process alive after cold launch"
```

---

### Task 7: Supported Physical-Device Nano Validation and Release Labeling

**Files:**
- Create/update QA report artifact only; no production code unless a device-specific bug is found.

**Interfaces:**
- Produces truthful release status: APK may be release-tested with fallback before Nano hardware validation, but Gemini enhancement itself is not called production-verified until this task passes.

- [ ] **Step 1: On a supported locked-bootloader Android device with AICore, install v2.0.4**

- [ ] **Step 2: Keep enhanced editor OFF and verify baseline recording/save**

Expected: behavior matches local v2.0.3 writing path.

- [ ] **Step 3: Enable `ویراستاری دقیق‌تر با مدل روی دستگاه`**

If status is DOWNLOADABLE, allow the system-managed model provisioning; current text/save must continue through local editor while it downloads.

- [ ] **Step 4: Verify one safe Persian enhancement**

Use a sentence with a harmless spelling/half-space issue and verify the candidate is accepted only if numbers/names/Latin tokens/repetitions remain intact.

- [ ] **Step 5: Verify one unsafe-output fallback case using a deterministic test hook/debug build**

Inject a candidate that changes a number or removes `خیلی خیلی`; verify UI/save keep the local text.

- [ ] **Step 6: Record device/model version and QA result**

Use ML Kit's available base-model metadata (`getBaseModelName()` where exposed by the current client) in the QA note. Do not collect user journal content.

- [ ] **Step 7: Final verification before delivery**

Required evidence:

```text
unit tests PASS
lintRelease PASS
assembleRelease PASS
APK signed/zipaligned/integrity PASS
no INTERNET permission PASS
Shenava hashes PASS
local punctuation hash PASS
API 35 cold-launch smoke PASS
no FATAL EXCEPTION PASS
physical-device Nano enhancement PASS (required only for claiming Nano production-verified)
```

If the physical Nano test is not available, deliver only with explicit wording: `Gemini Nano integration compiled and fallback-verified; successful Nano inference still requires supported-device validation.`
