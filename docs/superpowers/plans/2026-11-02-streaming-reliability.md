# Continuous speech implementation plan

> **For agentic workers:** Use superpowers:executing-plans inline, then one focused review.

**Goal:** Keep continuous dictation responsive and preserve text across provider cycles/errors.
**Architecture:** Retain Android SpeechRecognizer and real UI. Bound stable neural work at the coordinator boundary, cache deterministic patterns, and harden the existing session/cycle state machine.
**Tech Stack:** Kotlin/Java, Compose, Android RecognitionService, JUnit.
**Spec:** docs/superpowers/specs/2026-11-02-streaming-reliability.md

## Global Constraints
- Name امروز; source art/fonts/themes unchanged.
- No audio files, no INTERNET permission, no silent private-to-network fallback.
- Preserve original signing identity and application IDs.

## Review Focus
- Partial words followed by a recoverable error must remain in the next result.
- Late callbacks after retry/stop/clear must not overwrite newer text or reopen recording.
- Long text must not increase optional model input without bound.
- Formatting latency, punctuation integrity and Unicode/numeric tokens.
- Actual engine callback stream drives long Compose text while preserving UI responsiveness.

## Task1: Reproduce
- [ ] Android test provider: inspect actual request formatting, stream many utterances, emit recoverable errors after visible partials; run against unchanged product.
- [ ] Coordinator tests: long diary request sizes stay<=80 words/1200 chars while preserving prefix/words; watch existing implementation fail.
- [ ] Record baseline cleanup CPU measurements; keep output parity tests for punctuation/orthography/protected tokens.

## Task2: Correct pipeline
- [ ] Cache cleanup regexes and use one-pass protected token restore; compare output and CPU.
- [ ] Bound optional editing to recent tail, preserve previously accepted display prefix, skip unsafe giant single tokens.
- [ ] Use latency formatting; preserve interrupted partials, reset bounded retries on real progress, reject retired-cycle callbacks, keep explicit stop/clear authoritative; include error code without user content.
- [ ] Run full unit/tool suite plus real binder/long-text Android26/35 regression tests. No claim about the exact on-phone failure without its error text.

## Task3: Deliver
- [ ] Focused review; fix material findings with reproduced tests.
- [ ] Build2.3.3/code43, sign both arm64 APKs with existing private key; verify flags/identity/models/alignment.
- [ ] Save both APKs together and give direct download links with honest test scope.
