# Google Gemini Nano Editor v2.0.4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an opt-in on-device Gemini Nano editor to Emrooz, with deterministic safety checks and the existing local editor as a permanent fallback.

**Architecture:** Stable/final transcript segments pass through `WritingEnhancementCoordinator`. When the user has opted in and AICore reports Gemini Nano available, `GeminiNanoWritingEnhancer` requests a narrowly constrained spelling/half-space/punctuation correction, then `WritingEditSafety` decides whether the candidate may replace the baseline. Any unsupported state, download state, timeout, quota/error, cancellation, or unsafe candidate immediately returns the local result.

**Tech Stack:** Kotlin, Jetpack Compose, DataStore, Kotlin coroutines, ML Kit Prompt API `com.google.mlkit:genai-prompt:1.0.0-beta4`, Android AICore/Gemini Nano, existing ONNX punctuation model, JUnit 4, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-09-15-google-gemini-nano-editor-design.md`

## Global Constraints

- Android minSdk remains 26; compileSdk and targetSdk remain 35.
- Release becomes versionCode 34 / versionName `2.0.4`.
- Google editor defaults OFF and is explicitly opt-in.
- Gemini Nano receives text only, never raw microphone buffers.
- No audio file is written.
- `android.permission.INTERNET` must be absent from source manifest, merged manifest, and final APK.
- Shenava v1.5 RNNT remains the guaranteed offline ASR path.
- Android/System speech remains optional and independent from the writing enhancer.
- Existing Abar/Lato UI, archive, biometric-first lock, and transcript glow stay unchanged.
- `اصلاح کن` must not return.
- Nano runs only on stable/final text, never partial ASR updates.
- Any failure keeps the local text intact.
- Successful Nano inference must not be described as production-verified until it is exercised on a supported physical device.

---

### Task 1: Safety Boundary

**Files:**
- Create: `hybrid_v204/WritingEnhancer.kt`
- Create: `hybrid_v204/WritingEditSafety.kt`
- Create: `hybrid_v204/WritingEditSafetyTest.kt`

**Interfaces:**

```kotlin
interface WritingEnhancer {
    suspend fun enhance(text: String, languageTag: String): EnhancementResult
}

sealed interface EnhancementResult {
    data class Applied(val text: String) : EnhancementResult
    data class Unavailable(val reason: String) : EnhancementResult
    data class Failed(val reason: String) : EnhancementResult
}

object WritingEditSafety {
    fun accept(original: String, candidate: String): Boolean
}
```

- [ ] **Step 1: Write failing tests**

```kotlin
@Test fun accepts_punctuation_only()
@Test fun accepts_half_space_only()
@Test fun accepts_one_small_persian_spelling_fix()
@Test fun rejects_number_change()
@Test fun rejects_latin_token_change()
@Test fun rejects_removed_repeated_emphasis()
@Test fun rejects_inserted_or_deleted_words()
@Test fun rejects_widespread_rewrite()
```

Example assertions:

```kotlin
assertTrue(WritingEditSafety.accept("من امروز رفتم خونه", "من امروز رفتم خونه."))
assertTrue(WritingEditSafety.accept("می روم خونه", "می‌روم خونه"))
assertTrue(WritingEditSafety.accept("امروز هوا عاللی بود", "امروز هوا عالی بود"))
assertFalse(WritingEditSafety.accept("ساعت ۸ رسیدم", "ساعت ۹ رسیدم"))
assertFalse(WritingEditSafety.accept("به WhatsApp پیام دادم", "به Whatsapp پیام دادم"))
assertFalse(WritingEditSafety.accept("خیلی خیلی خسته بودم", "خیلی خسته بودم"))
```

- [ ] **Step 2: Run RED**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests '*WritingEditSafetyTest*'
```

Expected: FAIL because the implementation is absent.

- [ ] **Step 3: Implement deterministic acceptance**

Rules: normalize whitespace and Persian `ي/ى/ك` only for comparison; preserve every numeric run exactly; preserve every Latin/mixed alphanumeric token exactly and in order; preserve consecutive repeated lexical tokens; require identical lexical token count; permit changed Persian token only at the same position with Levenshtein distance <=1 for length <=5 or <=2 for length >5; reject if changed lexical tokens exceed `max(1, floor(tokenCount * 0.20))`; reject blank or structurally mismatched candidates.

- [ ] **Step 4: Run GREEN plus existing safety tests**

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

### Task 2: ML Kit / AICore Adapter

**Files:**
- Create: `hybrid_v204/build.gradle.kts` from current `hybrid_v203/build.gradle.kts`
- Create: `hybrid_v204/NanoPromptClient.kt`
- Create: `hybrid_v204/MlKitNanoPromptClient.kt`
- Create: `hybrid_v204/GeminiNanoWritingEnhancer.kt`
- Create: `hybrid_v204/GeminiNanoWritingEnhancerTest.kt`

**Interfaces:**

```kotlin
enum class NanoStatus { AVAILABLE, DOWNLOADABLE, DOWNLOADING, UNAVAILABLE }

interface NanoPromptClient : AutoCloseable {
    suspend fun status(): NanoStatus
    suspend fun requestDownload(): NanoStatus
    suspend fun generate(systemInstruction: String, prompt: String): String
}
```

- [ ] **Step 1: Write failing adapter-policy tests**

```kotlin
@Test fun unavailable_never_generates()
@Test fun downloadable_requests_download_and_returns_unavailable_for_current_call()
@Test fun available_safe_candidate_is_applied()
@Test fun unsafe_candidate_is_failed()
@Test fun generation_exception_is_failed()
@Test fun timeout_is_failed()
```

Use a fake `NanoPromptClient` with call counters.

- [ ] **Step 2: Run RED**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests '*GeminiNanoWritingEnhancerTest*'
```

- [ ] **Step 3: Add official dependency and version bump**

```kotlin
implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
```

Set:

```kotlin
versionCode = 34
versionName = "2.0.4"
```

Do not add `INTERNET` permission.

- [ ] **Step 4: Implement the concrete ML Kit client**

Use `Generation.getClient()`. Map `checkStatus()` into `NanoStatus`. If status is downloadable, invoke the SDK download API and immediately return local fallback for the current edit rather than blocking save/recording.

For inference construct:

```kotlin
val request = generateContentRequest(
    SystemInstruction(SYSTEM_INSTRUCTION),
    TextPart(text)
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

System instruction must be under 150 words and require: output corrected text only; keep meaning, names, numbers, Latin tokens, language, repetition/emphasis, and sentence content; allow only spelling, Persian character normalization, half-space, whitespace, and punctuation fixes.

- [ ] **Step 5: Implement `GeminiNanoWritingEnhancer`**

Wrap status + generation in `withTimeoutOrNull(2500)`. Strip only a single matching quote wrapper or single Markdown code fence. Pass every candidate through `WritingEditSafety.accept`. Return `Failed("unsafe_candidate")` when rejected.

- [ ] **Step 6: Run GREEN and compile**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests '*GeminiNanoWritingEnhancerTest*' :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 7: Verify merged-manifest privacy**

```bash
gradle --no-daemon :app:processDebugMainManifest
! grep -R "android.permission.INTERNET" app/build/intermediates/merged_manifests app/build/intermediates/merged_manifest 2>/dev/null
```

If this check fails, stop implementation at this task and keep Nano disabled; do not mask the permission with manifest-removal directives.

- [ ] **Step 8: Commit**

```bash
git add hybrid_v204/build.gradle.kts hybrid_v204/NanoPromptClient.kt hybrid_v204/MlKitNanoPromptClient.kt hybrid_v204/GeminiNanoWritingEnhancer.kt hybrid_v204/GeminiNanoWritingEnhancerTest.kt
git commit -m "feat: add on-device Gemini Nano writing adapter"
```

---

### Task 3: Coordinator and Local Fallback

**Files:**
- Create: `hybrid_v204/LocalWritingEnhancer.kt`
- Create: `hybrid_v204/WritingEnhancementCoordinator.kt`
- Create: `hybrid_v204/WritingEnhancementCoordinatorTest.kt`
- Create: `hybrid_v204/HybridSpeechController.kt` from current `hybrid_v203/HybridSpeechController.kt`

**Interfaces:**

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

- [ ] **Step 1: Write failing coordinator tests**

```kotlin
@Test fun disabled_never_calls_nano()
@Test fun unavailable_uses_local_result()
@Test fun failed_uses_local_result()
@Test fun safe_nano_output_is_then_locally_punctuated()
@Test fun nonblank_input_never_becomes_blank_on_fallback()
```

- [ ] **Step 2: Run RED**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests '*WritingEnhancementCoordinatorTest*'
```

- [ ] **Step 3: Implement `LocalWritingEnhancer` and coordinator**

Local provider delegates to existing `TextEditor`; do not duplicate `PersianEditorial`. Stable flow is: deterministic live cleanup → optional Nano → safety gate inside Nano enhancer → existing local punctuation → deterministic cleanup. Nano `Unavailable`/`Failed` uses the pre-Nano local-clean text.

- [ ] **Step 4: Integrate stable callback only**

Add to `HybridSpeechController`:

```kotlin
private val enhancedEditingEnabled: Boolean = false
```

Keep partial `textCallback` on the current fast local path. Replace the stable coroutine's direct `editor.stable(...)` with `coordinator.stable(...)`. Nano must not block microphone capture.

- [ ] **Step 5: Run GREEN/regression tests**

```bash
gradle --no-daemon :app:testDebugUnitTest --tests '*WritingEnhancementCoordinatorTest*' --tests '*StableDisplayComposerTest*' --tests '*TranscriptContinuityTest*'
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add hybrid_v204/LocalWritingEnhancer.kt hybrid_v204/WritingEnhancementCoordinator.kt hybrid_v204/WritingEnhancementCoordinatorTest.kt hybrid_v204/HybridSpeechController.kt
git commit -m "feat: route stable transcript through optional Nano editor"
```

---

### Task 4: Opt-In Settings UI

**Files:**
- Create: `hybrid_v204/SettingsStore.kt` from current `hybrid_v203/SettingsStore.kt`
- Create: `hybrid_v204/NameEmroozApp.part00` from current part00
- Create: `hybrid_v204/NameEmroozApp.part03` from current part03

**Interfaces:**

```kotlin
val enhancedOnDeviceEditingEnabled: Flow<Boolean>
suspend fun setEnhancedOnDeviceEditingEnabled(value: Boolean)
```

- [ ] **Step 1: Add DataStore default-off state**

```kotlin
private val enhancedOnDeviceEditingKey = booleanPreferencesKey("enhanced_on_device_editing")
val enhancedOnDeviceEditingEnabled = context.dataStore.data.map { it[enhancedOnDeviceEditingKey] ?: false }
suspend fun setEnhancedOnDeviceEditingEnabled(value: Boolean) = context.dataStore.edit { it[enhancedOnDeviceEditingKey] = value }
```

- [ ] **Step 2: Wire setting into Home/controller**

```kotlin
val enhancedEditing by store.enhancedOnDeviceEditingEnabled.collectAsState(initial = false)
```

Include `enhancedEditing` in the `remember(...)` key and pass `enhancedEditingEnabled = enhancedEditing` into `HybridSpeechController`.

- [ ] **Step 3: Add settings copy**

FA title: `ویراستاری دقیق‌تر با مدل روی دستگاه`

FA helper: `اگر مدل گوگل روی گوشی پشتیبانی شود، املا و نیم‌فاصله با حفظ معنی بهتر می‌شود. در غیر این صورت ویراستار آفلاین امروز استفاده می‌شود.`

EN title: `Enhanced on-device editing`

EN helper: `Uses the device's supported Google on-device model when available; otherwise Emrooz keeps its local editor.`

No modal error dialog. The switch may stay enabled while the model is downloading; local fallback remains immediate.

- [ ] **Step 4: Compile UI**

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

### Task 5: Release Contract and CI

**Files:**
- Create: `hybrid_v204/verify_google_editor_contract.sh`
- Create: `.github/workflows/build-v204-release.yml`
- Stage all v2.0.4 overlay files from Tasks 1–4.

- [ ] **Step 1: Add contract checks**

```bash
rg -q 'com.google.mlkit:genai-prompt:1.0.0-beta4' app/build.gradle.kts
rg -q 'enhanced_on_device_editing' app/src/main/java/com/nameemrooz/journal/data/SettingsStore.kt
rg -q 'GeminiNanoWritingEnhancer' app/src/main/java/com/nameemrooz/journal/writing
rg -q 'WritingEditSafety' app/src/main/java/com/nameemrooz/journal/writing
! rg -q 'android.permission.INTERNET' app/src/main/AndroidManifest.xml
! rg -q 'اصلاح کن' app/src/main/java
```

- [ ] **Step 2: Build reconstructed v2.0.4 overlay in CI**

Copy new writing files, updated controller/settings/UI/build file and tests into the reconstructed project before Gradle runs.

- [ ] **Step 3: Run full gates**

```bash
gradle --no-daemon clean :app:testDebugUnitTest :app:lintRelease :app:assembleRelease
```

Expected: PASS.

- [ ] **Step 4: Verify final APK**

Check versionCode 34/versionName 2.0.4, no INTERNET permission via `aapt dump permissions`, arm64 release ABI only, Shenava hashes, local punctuation hash, zip integrity, zipalign, and APK signature.

- [ ] **Step 5: Commit**

```bash
git add hybrid_v204 .github/workflows/build-v204-release.yml
git commit -m "ci: verify v2.0.4 on-device editor release"
```

---

### Task 6: Runtime and Release Truthfulness

**Files:**
- Modify: `.github/workflows/build-v204-release.yml`
- Read diagnostics from: `.github/workflows/runtime-debug-v203.yml`
- Create: `docs/qa/2026-09-15-emrooz-v204-nano-editor.md`

- [ ] **Step 1: Build/install x86_64 debug for emulator smoke**

Use the existing CI-only ABI replacement to x86_64, then install on Android API 35 emulator.

- [ ] **Step 2: Launch directly and always capture diagnostics before asserting**

```bash
adb logcat -c
adb shell am force-stop com.nameemrooz.journal
adb shell am start -W -n com.nameemrooz.journal/.MainActivity
sleep 20
adb logcat -d > /tmp/out/runtime-logcat.txt
adb shell dumpsys activity exit-info com.nameemrooz.journal > /tmp/out/exit-info.txt || true
adb shell pidof com.nameemrooz.journal > /tmp/out/pid.txt || true
test -s /tmp/out/pid.txt
! grep -E 'FATAL EXCEPTION|Process: com\.nameemrooz\.journal.*has died' /tmp/out/runtime-logcat.txt
```

Do not weaken the 20-second liveness assertion. If the current process-exit bug reproduces, use the captured exit-info/logcat to identify and fix its root cause before release verification is considered complete.

- [ ] **Step 3: Confirm Nano-unavailable fallback on emulator**

The emulator is not expected to expose production Gemini Nano. With the toggle OFF by default, startup must not initialize Nano. In unit/integration tests, an `UNAVAILABLE` Nano client must return local text and keep saving functional.

- [ ] **Step 4: Write QA report with exact status**

`docs/qa/2026-09-15-emrooz-v204-nano-editor.md` must record: unit/lint/release result, APK SHA-256 and size, permission result, runtime liveness result, and Nano hardware-validation status.

- [ ] **Step 5: Supported-device validation rule**

On a supported locked-bootloader device with AICore, enable the feature and verify at least one safe Persian spelling/half-space correction. Also verify a debug-injected unsafe candidate that changes a number or removes `خیلی خیلی` is rejected. Record the device model and Gemini Nano base-model name when the SDK exposes it; never record journal content.

- [ ] **Step 6: Final labeling rule**

If supported-device Nano inference has not been exercised, release wording must be: `Gemini Nano integration compiled and fallback-verified; successful Nano inference still requires supported-device validation.` Only after the supported-device test passes may the feature be called production-verified.
