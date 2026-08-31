# Emrooz Hybrid Persian ASR Design

## Goal
Replace the inaccurate final CTC transcription path with a higher-accuracy fully offline Whisper final pass while preserving the exact v1.1.9 UI, branding, Doran clock/date, clipped recording glow, journal data model, archive, settings, and privacy behavior.

## Baseline
- Start from the exact v1.1.9 Quality Fix reconstruction.
- UI/resource files are immutable for this change.
- Streaming Shenava CTC remains only for low-latency live draft text.
- The old non-streaming CTC finalizer is removed from the packaged assets and runtime path.

## Final ASR Architecture
1. Capture 16 kHz mono PCM continuously in RAM.
2. Prefer Android `UNPROCESSED` microphone input only when the device explicitly reports support; otherwise use `VOICE_RECOGNITION`.
3. Feed chunks to the existing streaming Shenava CTC model for live draft text.
4. Keep the same PCM samples in RAM for the final pass; never write raw audio to disk.
5. On Stop, drain capture, finalize the live draft, release the streaming CTC model to reduce peak RAM, then run Whisper Small multilingual Q5_1 over the full session.
6. Whisper is pinned to Persian (`fa`), transcription mode, no translation, no timestamps, beam search tuned for final accuracy.
7. Prefer a sane non-empty Whisper result as final text. Fall back to the live CTC final only when Whisper fails, is blank, or is rejected by hallucination/degeneracy guards.
8. Apply the existing conservative Persian proofreading and proper-name correction only after ASR selection.
9. Release the Whisper context and wipe PCM buffers after finalization, then reload the streaming CTC model for the next recording.

## Native Engine
- Pin `ggml-org/whisper.cpp` to release `v1.9.3`.
- Build the official Android library implementation in CI with a small Persian-specific JNI patch instead of maintaining a forked native engine.
- Package only `arm64-v8a`, matching the current app.
- Use `ggml-small-q5_1.bin`, SHA256 `ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb`.
- Keep the model uncompressed in Android assets for predictable loading.

## Accuracy and Safety Rules
- No per-user semantic rewriting.
- No conversion of spoken numbers unless the ASR itself outputs digits.
- Preserve natural repetition such as `خیلی خیلی`; remove only existing high-confidence ASR stutter artifacts.
- Normalize safe Persian orthography such as `روح الله` -> `روح‌الله` after recognition.
- Do not claim perfect recognition. Acoustic quality is device/speaker dependent.

## Concurrency and Reliability
- Separate `sessionActive` from microphone capture state so a second recording cannot start while Whisper is finalizing.
- Microphone capture and streaming decode stay on independent coroutine dispatchers.
- Finalization runs off the main thread.
- Any Whisper/native failure must fall back to the CTC final text and leave the app ready for another recording.
- Every allocated PCM buffer is zeroed after use.

## UI Lock
The v1.2.0 build must fail if any pre-existing file under `app/src/main/res` or `NameEmroozApp.kt` changes. The only allowed runtime-source changes are speech/finalization helpers and build metadata/dependencies.

## Verification
- RED/GREEN unit tests for final transcript selection, natural repetition preservation, proper-name correction, audio-source selection, and session-state behavior.
- Full existing unit suite.
- Android lint.
- Native Whisper Android AAR build pinned to v1.9.3.
- Model SHA verification.
- APK verification: signature, package/version, RECORD_AUDIO present, INTERNET absent, Whisper model present, streaming CTC model present, old offline CTC model absent.
- Acoustic smoke test using a real public Persian WAV through the same pinned Whisper model before publishing; this is a smoke check, not a claim of production WER.

## Version
- Version name: `1.2.0`
- Test-safe application id: `com.nameemrooz.journal.v120hybrid`
