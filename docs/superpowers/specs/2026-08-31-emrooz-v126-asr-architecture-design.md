# Emrooz v1.2.6 — Persian ASR Architecture Redesign

Date: 2026-08-31
Branch: `fix/v126-architectural-asr`
Baseline: v1.2.5 (`fix/v125-stable-live-font`)

## Goal

Deliver the lowest-error practical offline Persian voice-journal experience on Android while keeping the existing visual design and privacy guarantees. The redesign targets the two user-visible failures that remain in v1.2.5:

1. live transcription is often wrong or confusing before the final text is corrected;
2. final transcription/editor latency is too high and final editing is not consistently reliable.

This is an architectural redesign of the speech pipeline, not another tuning patch.

## Non-goals / locked behavior

The following stay unchanged unless a regression requires a minimal fix:

- overall UI layout, colors, navigation, archive, journal persistence, microphone button behavior;
- clock/date text-renderer introduced in v1.2.5;
- offline-first privacy model;
- no raw audio persisted to disk;
- no `INTERNET` permission in the final APK;
- no semantic rewriting by the editor;
- previously stored journal entries remain untouched.

## Architecture

### 1. One clean microphone path

Capture remains 16 kHz mono PCM from `VOICE_RECOGNITION`, but the current stacked signal-processing chain is removed.

The default path is:

`AudioRecord(VOICE_RECOGNITION) -> bounded PCM ring buffer -> VAD/segmenter -> ASR`

Rules:

- do not stack Android AGC, Android noise suppression, and software adaptive gain;
- software gain is disabled by default;
- any optional noise suppression or normalization must be selected by measured fixture performance, not volume alone;
- prevent clipping and log per-segment peak/RMS in tests only, never user audio.

### 2. Speech segmentation while the user is still recording

A lightweight offline VAD/endpoint detector splits speech into short utterance segments while recording continues.

Target behavior:

- close a segment after roughly 450–650 ms of confirmed silence;
- hard-cap a speech segment at about 12 seconds;
- retain a small overlap (about 200–300 ms) between adjacent segments where needed;
- process completed segments immediately in the background;
- discard their PCM from RAM as soon as final text has been produced.

This removes the v1.2.5 behavior where the entire session is held and reprocessed after Stop.

### 3. Live transcript: quick but never raw-noisy

The live model is used only to reassure the speaker that the app is hearing roughly the right content.

A new `LiveTranscriptStabilizer` replaces the current fixed 850 ms / 3-snapshot rule.

Behavior:

- compare consecutive hypotheses;
- commit only a stable word prefix;
- normally require two consistent hypotheses rather than three;
- target 250–600 ms stability depending on hypothesis agreement;
- never retract committed live words;
- never emit a one-off unstable guess;
- apply only lightweight Unicode/spacing cleanup to live text;
- do not run heavy final editing on every partial.

When a segment is finalized, its final text replaces the temporary live text for that segment only.

### 4. Final ASR: segment-by-segment, not whole-session replay

Each completed segment is decoded with the accuracy-first final recognizer while recording continues.

The final recognizer is loaded once per recording session (or lazily on first segment) and reused for subsequent segments. It is released after the session or on memory pressure.

When Stop is pressed:

- only the currently open segment remains to finalize;
- already finalized segments are not decoded again;
- final Stop latency therefore should not grow linearly with total session duration.

### 5. Model bake-off before choosing production models

No model is declared “best” by reputation alone. The implementation phase must benchmark compatible on-device Persian candidates against the same fixed fixture pack.

At minimum compare:

- Shenava Koochik streaming INT8;
- Shenava Rizeh streaming INT8 for live-only use;
- Shenava Koochik non-streaming INT8 for final use;
- any better sherpa-onnx-compatible Persian FastConformer/CTC model discovered during implementation, provided its license and Android runtime compatibility are acceptable.

Score each candidate on:

- Persian WER/CER;
- first-text latency;
- real-time factor / decode latency;
- APK size;
- peak RAM;
- model-load time.

Selection priority is: final accuracy first, then latency, then size. A candidate that is only slightly more accurate but causes unacceptable multi-second pauses is rejected.

### 6. Final transcript selection and merging

The current live-vs-final whole-session `FinalTranscriptPolicy` is replaced by per-segment reconciliation.

Rules:

- final segment text is authoritative only if it passes Persian-content and degeneration checks;
- reject repeated-token degeneration and obviously collapsed fragments;
- if final decode is empty/broken, keep the stable live segment text;
- merge adjacent segments with overlap-aware token deduplication;
- never erase already finalized earlier segments because of a later bad hypothesis.

### 7. Editor v2: deterministic and confidence-aware

The editor is split into explicit stages:

1. Unicode normalization (`ي/ى -> ی`, `ك -> ک`, Arabic/Persian digits policy);
2. whitespace and punctuation spacing;
3. Persian morphology / ZWNJ (`می/نمی`, `ها/های`, common enclitics, `ه‌ی`);
4. high-confidence lexical corrections;
5. protected-token restoration (names, numbers, repeated emphasis);
6. punctuation restoration using segment boundaries plus narrow question rules.

Important safety rules:

- no generative language model;
- no paraphrasing;
- no synonym replacement;
- no deleting intentional repetitions such as `خیلی خیلی`;
- no changing names or numbers unless a correction is explicitly high-confidence;
- a correction that cannot be justified by a deterministic rule or strong lexicon match is not applied.

The editor must be testable stage by stage so a regression can be traced to one rule family.

## Persian evaluation fixture pack

Create a deterministic test corpus covering both formal and colloquial Persian. It must include clean speech and controlled noisy variants.

Text domains:

- daily-journal phrases;
- colloquial verbs (`می‌خوام`, `نمی‌دونستم`, `می‌اومدم`, `می‌ذارم`, `می‌خوابم`);
- names such as `روح‌الله`, `سهیل`, `ممدوحی`;
- dates and numbers;
- short questions with `چرا`;
- repeated emphasis (`خیلی خیلی`);
- similar-sounding Persian words;
- 3–15 second utterances;
- longer 30–60 second multi-sentence recordings.

Where external audio fixtures are used, they must have a compatible redistribution/test license and be pinned by checksum. Synthetic fixtures may supplement but may not be the only accuracy evidence.

## Ten release gates

A release candidate is not published unless all ten gates pass.

### Gate 1 — Audio integrity

- no clipping regression;
- correct 16 kHz mono capture;
- no stacked AGC/NS/software gain chain;
- bounded input queues.

### Gate 2 — Segmentation

- silence closes segments reliably;
- no missing first/last words at boundaries;
- overlap merge has no duplicate phrases.

### Gate 3 — Live stability

- one-off hypotheses are not shown;
- stable live text appears quickly;
- committed live text is monotonic and is not repeatedly rewritten.

### Gate 4 — Final ASR accuracy

- measure WER/CER on the fixed Persian fixture pack;
- v1.2.6 must beat v1.2.5 on the same fixture set;
- if the new architecture does not produce a meaningful accuracy improvement, do not release it.

### Gate 5 — Editor fidelity

- all existing editor regressions pass;
- new broad morphology/spacing tests pass;
- names, numbers and intentional repetitions are preserved;
- editor output must not increase semantic error versus raw final ASR on the fixture set.

### Gate 6 — Stop latency

- completed segments are not reprocessed on Stop;
- Stop finalizes only the open tail segment;
- algorithmic tests prove finalization work is bounded by tail length, not total session length.

### Gate 7 — Memory / long sessions

- no whole-session PCM retention;
- processed PCM is zeroed/discarded;
- queue sizes are bounded;
- 10+ minute synthetic session does not show linear RAM growth.

### Gate 8 — Privacy / permissions

- no raw audio file is written;
- no network calls in runtime speech/editor code;
- final APK has no `INTERNET` permission;
- only pinned local model assets are packaged.

### Gate 9 — UI/regression

- v1.2.5 visual structure remains intact;
- clock/date continue to use the real text font renderer;
- journal save/archive/resume behavior passes existing tests.

### Gate 10 — APK verification

- complete unit/regression suite passes;
- Android lint passes;
- release APK builds and is signed;
- unzip integrity passes;
- expected model assets are present and no obsolete model is bundled;
- package/version are correct;
- APK checksum is emitted.

## Performance targets

These are release targets, not promises for every Android device:

- stable live text should normally appear within about 1 second of clear speech onset;
- no deliberate fixed delay above 600 ms solely for text stabilization;
- Stop should normally require only the tail-segment finalization rather than replaying the full recording;
- long-session RAM use must remain approximately bounded after model memory is accounted for.

If measured accuracy requires a trade-off, final transcription accuracy has priority over instantaneous live text, but the UI must not show obviously unstable garbage.

## Failure handling

- If final recognizer fails for one segment, keep the stable live text for that segment and continue recording.
- If VAD fails, fall back to a conservative maximum-segment timer rather than losing audio.
- If memory pressure prevents loading the final model, retain live text and show a non-destructive error; do not crash or erase the entry.
- If a model candidate fails the benchmark or Android compatibility checks, exclude it rather than adding runtime complexity.

## Planned code boundaries

The redesign should separate responsibilities rather than expand one large engine file:

- `AudioCaptureSource` — AudioRecord lifecycle and PCM delivery;
- `SpeechSegmenter` — VAD/endpointing and segment boundaries;
- `LiveAsrEngine` — streaming hypotheses only;
- `LiveTranscriptStabilizer` — stable-prefix policy;
- `FinalAsrEngine` — full-context segment decoding;
- `SegmentTranscriptMerger` — overlap/deduplication and fallback;
- `PersianEditorV2` — deterministic final editing stages;
- `SpeechSessionController` — orchestration/state only.

Each unit must have direct tests and no hidden dependency on UI code.

## Release policy

v1.2.6 will be packaged with a side-by-side application ID for safe comparison against v1.2.5. It will not be described as fixed or final until the ten gates above pass in a fresh CI run and the produced APK is independently inspected.
