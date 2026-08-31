# Emrooz v1.2.3 — ASR + Persian Editor Overhaul

## Goal
Fix the device-reported failures without redesigning the app: incorrect Persian ASR, ineffective editing, mismatched numeric zero, and oversized clock colon.

## Architecture
1. Keep the v1.2.2 UI/resources unchanged except the Doran numeric renderer.
2. Use the existing low-latency embedded Rizeh streaming recognizer only as live preview.
3. Replace the v1.2.2 final Koochik path with an independently integrated final recognizer and make final output authoritative only when it passes sanity checks.
4. Do not persist raw audio; keep bounded PCM in RAM and wipe it after finalization.
5. Persian editor is conservative: Unicode normalization, deterministic morphology/ZWNJ, protected names, high-confidence lexical correction only, then punctuation. Never paraphrase or delete semantic content.
6. Correct Doran numeric rendering: scale the colon independently and normalize the zero glyph geometry instead of treating all glyph resources identically.

## Model selection
- Absolute accuracy models that are multi-gigabyte are rejected for this offline Android app because prior device feedback explicitly reports unacceptable latency.
- Primary practical final candidate: VisualEars FastConformer Persian full A+B ONNX W4, with its documented 16 kHz / 80-bin feature contract and CTC tokenizer.
- If its fixed-frame runtime cannot pass an acoustic smoke test on short Persian speech, fail the build and do not release; use the proven length-aware fallback rather than shipping an unverified integration.

## Editor selection
- Do not bundle the 0.2B ParsBERT PersianPunc checkpoint directly: it is too large for this app and its public distribution is gated.
- Keep punctuation conservative on-device and use deterministic question/period/comma restoration where confidence is high.
- Add a compact high-confidence Persian spelling layer using a pinned Persian frequency lexicon; edit-distance corrections are allowed only when the original token is unknown, candidate distance is 1, frequency dominance is strong, and the token is not a name/number/short token.

## TDD gates
### RED tests first
- CTC collapse removes blank/repeats and reconstructs SentencePiece spaces.
- Chunk merger does not duplicate overlap.
- Final transcript guard rejects blank/non-Persian/obviously degenerate final output and falls back to live.
- Persian editor fixes representative ZWNJ forms without changing repeated emphasis or numbers.
- High-confidence lexical corrector fixes a known typo and leaves valid/ambiguous words unchanged.
- Doran renderer special-cases colon scale and zero geometry.

### GREEN verification
- Targeted v1.2.3 tests pass.
- Entire prior release unit suite passes.
- Android lint passes.
- Release APK builds and verifies signature.
- APK contains exactly expected ASR assets and no Whisper / old final model.
- RECORD_AUDIO exists; INTERNET permission absent.
- Independent unzip and SHA-256 verification on the downloaded artifact.

## Release rule
No APK is published if any model smoke test, regression test, lint, build, signature, or content gate fails.
