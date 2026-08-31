# Emrooz v1.2.6 ASR Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the v1.2.5 whole-session/offline-finalization pipeline with a segmented, bounded-memory, low-latency Persian ASR pipeline and a deterministic Persian editor v2 while preserving the existing UI and offline privacy model.

**Architecture:** Keep `VOICE_RECOGNITION` 16 kHz mono capture but remove the stacked AGC/NoiseSuppressor/software-gain chain. Use sherpa-onnx online endpoint detection to close short speech segments, immediately decode each completed segment with a persistent Koochik full-context recognizer, merge only finalized segments, and expose live text only through a faster stable-prefix policy. The final editor is deterministic and stage-separated; it never paraphrases or rewrites protected names, numbers, or intentional repetition.

**Tech Stack:** Android/Kotlin, Jetpack Compose, coroutines/channels, sherpa-onnx NeMo CTC online/offline APIs, JUnit, Gradle 8.10.2, GitHub Actions, Python benchmark harness.

**Spec:** `docs/superpowers/specs/2026-08-31-emrooz-v126-asr-architecture-design.md`

## Global Constraints

- Baseline is v1.2.5 from branch `fix/v125-stable-live-font`.
- Keep the existing UI layout, colors, archive, journal persistence, microphone interaction, and Vazirmatn clock/date renderer unchanged.
- Runtime stays fully offline and the final APK must not request `android.permission.INTERNET`.
- No raw audio may be persisted to disk; PCM lives only in bounded RAM and is zeroed/discarded after segment finalization.
- Capture is mono PCM16 at 16,000 Hz from `MediaRecorder.AudioSource.VOICE_RECOGNITION`.
- Do not combine Android AGC, Android NoiseSuppressor, and `AdaptiveSpeechGain`; v1.2.6 production capture uses none of those additional gain stages unless benchmark evidence explicitly selects one.
- Live text must never show one-off unstable hypotheses and must not deliberately wait more than 600 ms solely for stabilization.
- Final text is decoded per segment while recording continues; Stop may finalize only the open tail segment.
- Final editor is deterministic only: no LLM, paraphrasing, synonym substitution, or semantic completion.
- `خیلی خیلی`, personal names, and spoken numbers are protected from destructive editing.
- Release app id: `com.nameemrooz.journal.v126segmented`; versionCode `29`; versionName `1.2.6`.
- Release requires all ten spec gates plus 10 consecutive deterministic regression passes with zero failures.

---

### Task 1: Lock v1.2.5 failures with RED architecture tests

**Files:**
- Create: `overlay/v126/tests/AudioFrontEndV126Test.kt`
- Create: `overlay/v126/tests/LiveTranscriptStabilizerV126Test.kt`
- Create: `overlay/v126/tests/SegmentBufferV126Test.kt`
- Create: `overlay/v126/tests/SegmentTranscriptMergerV126Test.kt`
- Create: `overlay/v126/tests/PersianEditorV2Test.kt`
- Create: `.github/workflows/build-v126-red.yml`

**Interfaces:**
- Consumes current v1.2.5 reconstructed source.
- Produces failing tests that explicitly describe the new contracts before any production overlay is applied.

- [ ] **Step 1: Write the audio-front-end RED test**

```kotlin
@Test fun productionCaptureHasNoStackedGainChain() {
    val source = File("src/main/java/com/nameemrooz/journal/speech/LiveSpeechEngine.kt").readText()
    assertTrue(source.contains("MediaRecorder.AudioSource.VOICE_RECOGNITION"))
    assertFalse(source.contains("AdaptiveSpeechGain()"))
    assertFalse(source.contains("AutomaticGainControl.create"))
    assertFalse(source.contains("NoiseSuppressor.create"))
}
```

- [ ] **Step 2: Write the stable-live RED tests**

```kotlin
@Test fun twoConsistentHypothesesCanCommitWithin600ms() {
    val s = LiveTranscriptStabilizer(minAgreementMs = 280)
    assertNull(s.offer("امروز رفتم", 0))
    assertEquals("امروز رفتم", s.offer("امروز رفتم بیرون", 320))
}

@Test fun oneOffGarbageIsNeverShown() {
    val s = LiveTranscriptStabilizer(minAgreementMs = 280)
    assertNull(s.offer("روکا شیمی فند", 0))
    assertNull(s.offer("امروز رفتم خرید", 300))
}
```

- [ ] **Step 3: Write segment-buffer RED tests**

```kotlin
@Test fun forcedSplitKeepsOnly250msOverlap() {
    val b = SegmentPcmBuffer(sampleRate = 16_000, hardLimitSeconds = 12, overlapMs = 250)
    repeat(12 * 16_000) { b.append(it.toShort()) }
    val first = b.forceClose()
    assertEquals(12 * 16_000, first.size)
    assertEquals(4_000, b.sampleCount())
}

@Test fun finalizedSegmentsDoNotAccumulateSessionPcm() {
    val b = SegmentPcmBuffer(16_000, 12, 250)
    repeat(100) {
        repeat(8_000) { b.append(1) }
        b.closeWithoutOverlap().fill(0)
    }
    assertTrue(b.sampleCount() <= 4_000)
}
```

- [ ] **Step 4: Write merge and editor RED tests**

```kotlin
@Test fun overlapWordsAreNotDuplicated() {
    val m = SegmentTranscriptMerger()
    assertEquals("امروز رفتم خرید و برگشتم خونه", m.merge("امروز رفتم خرید و برگشتم", "خرید و برگشتم خونه"))
}

@Test fun editorPreservesIntentionalRepetitionNamesAndNumbers() {
    val out = PersianEditorV2.editFinal("من خیلی خیلی خوشحال بودم سهیل ممدوحی ساعت هشت اومد")
    assertTrue(out.contains("خیلی خیلی"))
    assertTrue(out.contains("سهیل ممدوحی"))
    assertTrue(out.contains("هشت"))
}
```

- [ ] **Step 5: Run RED workflow and verify it fails for the intended missing classes/current stacked front-end**

Run in CI: targeted v126 tests against reconstructed v1.2.5 before production overlay.
Expected: FAIL only because v1.2.6 contracts are not implemented yet.

- [ ] **Step 6: Commit RED tests**

```bash
git add overlay/v126/tests .github/workflows/build-v126-red.yml
git commit -m "test(v126): lock segmented ASR failures"
```

---

### Task 2: Build a clean bounded audio and segment buffer layer

**Files:**
- Create: `overlay/v126/app/src/main/java/com/nameemrooz/journal/speech/SegmentPcmBuffer.kt`
- Create: `overlay/v126/app/src/main/java/com/nameemrooz/journal/speech/AudioCaptureSource.kt`
- Test: `overlay/v126/tests/SegmentBufferV126Test.kt`
- Test: `overlay/v126/tests/AudioFrontEndV126Test.kt`

**Interfaces:**
- Produces `AudioCaptureSource.start(onPcm: (ShortArray, Int) -> Unit)` and `stop()`.
- Produces `SegmentPcmBuffer.append(samples: ShortArray, length: Int)`, `closeWithoutOverlap(): ShortArray`, `forceClose(): ShortArray`, `sampleCount(): Int`, `clear()`.
- Later tasks consume raw PCM chunks without gain mutation.

- [ ] **Step 1: Implement `SegmentPcmBuffer` with a hard 12-second cap and 250 ms forced-split overlap**

```kotlin
class SegmentPcmBuffer(
    private val sampleRate: Int = 16_000,
    hardLimitSeconds: Int = 12,
    overlapMs: Int = 250,
) {
    private val hardLimit = sampleRate * hardLimitSeconds
    private val overlap = sampleRate * overlapMs / 1000
    private var data = ShortArray(hardLimit + overlap)
    private var size = 0

    fun append(samples: ShortArray, length: Int) { /* bounded copy only */ }
    fun shouldForceClose(): Boolean = size >= hardLimit
    fun closeWithoutOverlap(): ShortArray { /* return copy, zero source, size=0 */ }
    fun forceClose(): ShortArray { /* return hardLimit samples, retain only last overlap samples */ }
    fun sampleCount(): Int = size
    fun clear() { data.fill(0); size = 0 }
}
```

- [ ] **Step 2: Implement `AudioCaptureSource` with no explicit AGC/NS/software gain**

```kotlin
val record = AudioRecord(
    MediaRecorder.AudioSource.VOICE_RECOGNITION,
    16_000,
    AudioFormat.CHANNEL_IN_MONO,
    AudioFormat.ENCODING_PCM_16BIT,
    max(minBuffer * 4, 32_768)
)
```

`AudioCaptureSource` must copy each read into a bounded callback buffer and zero the reusable read buffer after delivery.

- [ ] **Step 3: Run targeted tests**

Run: `:app:testReleaseUnitTest --tests '*SegmentBufferV126Test' --tests '*AudioFrontEndV126Test'`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add overlay/v126/app/src/main/java/com/nameemrooz/journal/speech/SegmentPcmBuffer.kt overlay/v126/app/src/main/java/com/nameemrooz/journal/speech/AudioCaptureSource.kt overlay/v126/tests
git commit -m "feat(v126): add bounded clean audio capture"
```

---

### Task 3: Replace fixed 850 ms live delay with endpoint-aware streaming ASR

**Files:**
- Create: `overlay/v126/app/src/main/java/com/nameemrooz/journal/speech/LiveTranscriptStabilizer.kt`
- Create: `overlay/v126/app/src/main/java/com/nameemrooz/journal/speech/LiveAsrEngine.kt`
- Modify via overlay: production `ShenavaRecognizer.kt`
- Test: `overlay/v126/tests/LiveTranscriptStabilizerV126Test.kt`
- Test: `overlay/v126/tests/EndpointPolicyV126Test.kt`

**Interfaces:**
- `LiveAsrEngine.accept(FloatArray): LiveAsrUpdate` where `LiveAsrUpdate(partial: String, endpoint: Boolean)`.
- `LiveAsrEngine.resetSegment()` resets only the streaming decoder state.
- `LiveTranscriptStabilizer.offer(text: String, nowMs: Long): String?` returns monotonic stable text.

- [ ] **Step 1: Add sherpa endpoint config**

Use sherpa-onnx `EndpointConfig`:

```kotlin
endpointConfig = EndpointConfig(
    rule1 = EndpointRule(false, 1.20f, 0f),
    rule2 = EndpointRule(true, 0.50f, 0f),
    rule3 = EndpointRule(false, 0f, 12.0f),
),
enableEndpoint = true,
```

The 0.50 s rule is the production starting value; benchmark may move it only inside 0.45–0.65 s.

- [ ] **Step 2: Implement live stabilizer using two-hypothesis agreement**

```kotlin
class LiveTranscriptStabilizer(private val minAgreementMs: Long = 280) {
    private var previous = ""
    private var previousAt = 0L
    private var committed = ""

    fun offer(value: String, nowMs: Long): String? {
        val text = normalize(value)
        val prefix = commonWordPrefix(previous, text)
        val stableLongEnough = previous.isNotBlank() && nowMs - previousAt >= minAgreementMs
        previous = text
        previousAt = nowMs
        if (!stableLongEnough || prefix.length < 4 || prefix.length <= committed.length) return null
        if (committed.isNotEmpty() && !prefix.startsWith(committed)) return null
        committed = prefix
        return committed
    }
}
```

- [ ] **Step 3: Run targeted tests and confirm no fixed delay above 600 ms exists**

Run: `:app:testReleaseUnitTest --tests '*LiveTranscriptStabilizerV126Test' --tests '*EndpointPolicyV126Test'`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add overlay/v126/app/src/main/java/com/nameemrooz/journal/speech/LiveTranscriptStabilizer.kt overlay/v126/app/src/main/java/com/nameemrooz/journal/speech/LiveAsrEngine.kt overlay/v126/tests
git commit -m "feat(v126): stabilize live Persian text faster"
```

---

### Task 4: Finalize segments continuously with one reusable final recognizer

**Files:**
- Create: `overlay/v126/app/src/main/java/com/nameemrooz/journal/speech/FinalAsrEngine.kt`
- Create: `overlay/v126/app/src/main/java/com/nameemrooz/journal/speech/SegmentTranscriptMerger.kt`
- Test: `overlay/v126/tests/FinalSegmentPolicyV126Test.kt`
- Test: `overlay/v126/tests/SegmentTranscriptMergerV126Test.kt`
- Test: `overlay/v126/tests/StopWorkBoundV126Test.kt`

**Interfaces:**
- `FinalAsrEngine.recognize(segment: ShortArray): String` reuses one `OfflineRecognizer` instance.
- `SegmentTranscriptMerger.accept(stableLive: String, finalText: String): String` chooses safe segment text.
- `SegmentTranscriptMerger.merge(base: String, next: String): String` deduplicates overlap.

- [ ] **Step 1: Refactor the current offline recognizer into a reusable session-scoped engine**

```kotlin
class FinalAsrEngine(context: Context) : AutoCloseable {
    private val recognizer = OfflineRecognizer(/* pinned Koochik CTC config */)
    fun recognize(segment: ShortArray): String {
        val samples = FloatArray(segment.size) { segment[it] / 32768f }
        return try { decode(samples) } finally { samples.fill(0f); segment.fill(0) }
    }
    override fun close() = recognizer.release()
}
```

Do not construct/reload the model for every segment.

- [ ] **Step 2: Implement per-segment degeneration checks**

Reject final output when Persian-letter ratio is below 0.55, when a 4+ word output is dominated >=60% by one repeated token, or when a non-empty stable-live segment collapses to <45% of its word count without sufficient lexical overlap.

- [ ] **Step 3: Implement overlap-aware merge**

Find the largest suffix of the existing finalized words equal to a prefix of the new segment, limited to 8 words, and append only the unmatched suffix.

- [ ] **Step 4: Prove Stop work is tail-bounded**

The unit test constructs a controller accounting model with 100 already-finalized segments plus one 5-second tail and asserts the finalization work count is identical to a short session with the same tail.

- [ ] **Step 5: Run targeted tests and commit**

```bash
gradle :app:testReleaseUnitTest --tests '*FinalSegmentPolicyV126Test' --tests '*SegmentTranscriptMergerV126Test' --tests '*StopWorkBoundV126Test'
git add overlay/v126
git commit -m "feat(v126): finalize Persian speech per segment"
```

---

### Task 5: Replace monolithic cleanup with deterministic `PersianEditorV2`

**Files:**
- Create: `overlay/v126/app/src/main/java/com/nameemrooz/journal/util/PersianEditorV2.kt`
- Modify via overlay: call site previously using `PersianText.clean(..., final = true)`
- Test: `overlay/v126/tests/PersianEditorV2Test.kt`
- Copy/run all editor tests from v1.2.1-v1.2.3.

**Interfaces:**
- `PersianEditorV2.cleanLive(input: String): String` does Unicode/space cleanup only.
- `PersianEditorV2.editFinal(input: String, sentenceBoundary: Boolean = true): String` runs deterministic final stages.

- [ ] **Step 1: Implement explicit stages**

```kotlin
fun editFinal(input: String, sentenceBoundary: Boolean = true): String {
    var text = normalizeUnicode(input)
    text = normalizeWhitespaceAndPunctuation(text)
    text = normalizeMorphology(text)
    text = applyHighConfidenceLexicalFixes(text)
    text = restoreProtectedTokens(input, text)
    if (sentenceBoundary) text = restoreConservativePunctuation(text)
    return text.trim()
}
```

- [ ] **Step 2: Preserve protected content**

Tests must prove exact preservation for:

```text
خیلی خیلی
روح‌الله
سهیل ممدوحی
هشت
بیست و سه
```

Do not run the previous broad `normalizeSpokenNumbers` digit conversion in the final journal editor; spoken numbers remain in the user's spoken form unless already emitted as digits by ASR.

- [ ] **Step 3: Narrow punctuation heuristics**

Only sentence-initial/interrogative structures such as `چرا`, `آیا`, `چطور`, `کجا`, `کی`, `مگه` may imply `؟`; a mere occurrence of `چی` inside a declarative sentence must not force a question mark.

- [ ] **Step 4: Run every old editor regression plus v2 tests**

Expected: all legacy safe orthography tests pass, protected-token tests pass 100%, and intentional repetition remains unchanged.

- [ ] **Step 5: Commit**

```bash
git add overlay/v126/app/src/main/java/com/nameemrooz/journal/util/PersianEditorV2.kt overlay/v126/tests
git commit -m "feat(v126): add deterministic Persian editor v2"
```

---

### Task 6: Introduce `SpeechSessionController` and remove whole-session replay

**Files:**
- Create: `overlay/v126/app/src/main/java/com/nameemrooz/journal/speech/SpeechSessionController.kt`
- Replace via overlay: `app/src/main/java/com/nameemrooz/journal/speech/LiveSpeechEngine.kt` with a compatibility facade delegating to the controller.
- Create: `overlay/v126/apply_v126.py`
- Test: `overlay/v126/tests/SpeechSessionControllerV126Test.kt`
- Test: `overlay/v126/tests/LongSessionMemoryV126Test.kt`

**Interfaces:**
- Existing UI-facing callbacks remain unchanged: `onText`, `onListening`, `onFinalizing`, `onReady`, `onError`.
- Controller consumes `AudioCaptureSource`, `LiveAsrEngine`, `FinalAsrEngine`, `SegmentPcmBuffer`, `SegmentTranscriptMerger`, and `PersianEditorV2`.

- [ ] **Step 1: Implement orchestration state**

Flow per PCM chunk:

```text
capture chunk
  -> append only to active SegmentPcmBuffer
  -> feed FloatArray copy to LiveAsrEngine
  -> maybe emit stable live preview
  -> if endpoint OR hard 12s cap: enqueue current segment for final decode, reset live segment
```

The final queue capacity is 2. If it fills, capture keeps only the active segment and applies backpressure; never create an unbounded queue.

- [ ] **Step 2: Finalize completed segment while recording continues**

Each segment is decoded on one dedicated coroutine. After final text is selected and edited, emit the entire assembled finalized transcript plus current stable live tail; then zero the segment PCM.

- [ ] **Step 3: Stop behavior**

`stop()` stops capture, closes only the current non-empty tail segment, waits for at most the already-enqueued segment plus tail, then emits the assembled final text. It must not concatenate or decode historical PCM.

- [ ] **Step 4: Long-session test**

Simulate 10 minutes as repeated finalized short segments and assert retained PCM never exceeds `activeSegmentCapacity + finalQueueCapacity * maxSegmentSamples` and does not scale with elapsed session duration.

- [ ] **Step 5: Apply version identity and remove v1.2.4 processors**

`apply_v126.py` must set:

```text
applicationId = "com.nameemrooz.journal.v126segmented"
versionCode = 29
versionName = "1.2.6"
```

It also deletes production references to `AdaptiveSpeechGain`, `AutomaticGainControl`, and `NoiseSuppressor` while leaving unrelated UI byte-equivalent to v1.2.5.

- [ ] **Step 6: Run controller/long-session tests and commit**

```bash
gradle :app:testReleaseUnitTest --tests '*SpeechSessionControllerV126Test' --tests '*LongSessionMemoryV126Test'
git add overlay/v126
git commit -m "refactor(v126): replace whole-session ASR pipeline"
```

---

### Task 7: Freeze a real Persian acoustic benchmark and select models from evidence

**Files:**
- Create: `overlay/v126/benchmark/benchmark_segmented_asr.py`
- Create: `overlay/v126/benchmark/README.md`
- Create: `overlay/v126/benchmark/expected-thresholds.json`
- Create: `.github/workflows/benchmark-v126.yml`

**Interfaces:**
- Uses FLEURS Persian `fa_ir` test data under CC-BY-4.0, pinned to dataset revision `61f6cd085b42379eeb6b2d3560e827e741e195c3`.
- Produces `benchmark-v126.json` with WER, CER, model load time, decode time/RTF, and segmented-vs-whole-session scores.

- [ ] **Step 1: Build the fixture pack from real FLEURS clips**

Take a deterministic fixed index set, e.g. `[3, 17, 41, 66, 91, 124, 188, 231, 305, 377, 510, 702]`, normalize to 16 kHz mono, and construct both individual clips and four 30–60 second sessions by concatenating 3 clips with 600 ms silence. Cache by SHA256 during the workflow.

- [ ] **Step 2: Score v1.2.5 behavior versus segmented behavior**

Baseline: decode each concatenated session in one full-context pass and run the legacy editor normalization.
Candidate: decode the same session at known silence boundaries per segment, merge, then apply `PersianEditorV2`-equivalent normalization in the harness.

- [ ] **Step 3: Model bake-off**

Compare at minimum:

```text
live: Koochik streaming INT8
live: Rizeh streaming INT8
final: Koochik non-streaming INT8
```

Record published/reference model metadata and actual fixture results. VisualEars fixed-frame W4 may be documented as investigated but must not enter production unless the Android runtime path is implemented and benchmarked end-to-end; fixed acoustic-core parity alone is insufficient.

- [ ] **Step 4: Enforce release threshold**

The workflow fails unless candidate weighted WER is at least 10% relatively lower than v1.2.5 on the constructed long-session fixture pack, protected categories do not regress >5%, and the selected final model RTF <=1.0 on the same runner.

- [ ] **Step 5: Commit benchmark**

```bash
git add overlay/v126/benchmark .github/workflows/benchmark-v126.yml
git commit -m "test(v126): add pinned Persian acoustic benchmark"
```

---

### Task 8: Final release workflow and ten-pass reliability audit

**Files:**
- Create: `.github/workflows/build-v126-final.yml`
- Reuse: all v1.2.1-v1.2.5 regression tests plus all `overlay/v126/tests/*.kt`.

**Interfaces:**
- Produces `Emrooz-v1.2.6-SEGMENTED-PERSIAN.apk` and `apk-sha256.txt` only after all gates pass.

- [ ] **Step 1: Reconstruct exact v1.2.5 baseline and apply v1.2.6 overlay**

Workflow must prove the UI clock/date renderer still contains:

```kotlin
text = JalaliDate.time(now)
text = JalaliDate.pretty(now).replace("  ", " ")
fontFamily = UiFont
```

and no Doran digit renderer is restored in `CurrentClockAndDate()`.

- [ ] **Step 2: Run targeted v1.2.6 tests once**

All architecture, segment, live, editor, stop-work, privacy and memory tests must pass.

- [ ] **Step 3: Run deterministic regression suite 10 consecutive times**

```bash
for i in $(seq 1 10); do
  gradle --no-daemon :app:testReleaseUnitTest || exit 1
done
```

- [ ] **Step 4: Run Android lint and release assemble**

```bash
gradle --no-daemon :app:lintRelease
gradle --no-daemon :app:assembleRelease
```

- [ ] **Step 5: Independently inspect APK**

Verify:

```text
package = com.nameemrooz.journal.v126segmented
versionCode = 29
versionName = 1.2.6
RECORD_AUDIO present
INTERNET absent
only selected live + final model assets present
no Whisper assets
no obsolete Rizeh/Koochik duplicate not selected by benchmark
APK Signature v2 = true
unzip integrity = clean
```

- [ ] **Step 6: Publish artifact only after benchmark + ten gates are green**

Upload APK + checksum. Do not call the release “fixed” or “final” unless this fresh workflow is green end-to-end.

- [ ] **Step 7: Commit workflow**

```bash
git add .github/workflows/build-v126-final.yml
git commit -m "build(v126): add ten-gate final release audit"
```

---

## Self-review result

- Spec coverage: all ten release gates map to Tasks 2–8; privacy and UI invariants are explicitly checked in Task 8.
- Root cause coverage: stacked signal processing is removed; fixed 850 ms/three-snapshot live behavior is replaced; whole-session PCM retention and model reload on Stop are removed; monolithic editor heuristics are replaced.
- Model evidence: production selection is gated by a pinned real Persian fixture benchmark; fixed-frame VisualEars exports are not treated as drop-in Android models without an end-to-end runtime implementation.
- Type consistency: controller and tests use the same `SegmentPcmBuffer`, `LiveTranscriptStabilizer`, `FinalAsrEngine`, `SegmentTranscriptMerger`, and `PersianEditorV2` contracts throughout.
- Placeholder scan: no TBD/TODO implementation steps remain.
