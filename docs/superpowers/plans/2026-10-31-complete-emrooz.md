# Emrooz completion implementation plan

> Execute inline using superpowers:executing-plans; review the complete change before delivery.

**Goal:** Deliver one maintainable Android project combining the existing approved UI, RNNT startup repair and mandatory opt-in Google on-device editor.

**Architecture:** Promote the reconstructed v2.0.4 project to ordinary source files. Preserve the database/package identity, apply the v2.1.1 RNNT configuration and fix text ownership at the draft/session/editor boundaries. AICore receives text only; local editing remains available.

**Tech Stack:** Kotlin, Compose, Room, DataStore, coroutines, sherpa-onnx, ML Kit Prompt beta4, Gradle 8.13.

**Spec:** ../specs/2026-09-15-google-gemini-nano-editor-design.md

## Constraints and rulings

- User explicitly selected mandatory Google editor in addition to completing the app.
- Preserve Abar/Lato, RTL, archive, biometric lock, minSdk 26 and no INTERNET permission.
- Version 2.2.0 / code 36 follows released 2.1.1 / code 35.
- Existing v2.0.4 branch already implements the approved plan; reuse and verify it, do not replay completed development.
- Older accidental-repeat cleanup conflicts with the approved emphasis-preservation requirement: preserve repeated words; transcript overlap remains the recognizer merger's responsibility.
- A fresh scratch clone supplies isolation; work on a new branch. No existing checkout is modified.
- Do not use expired graphic links or expired signing artifacts. Retain bundled artwork. Produce a separately installable preview if the original signing key is unavailable; never ask users to delete existing notes.

## Review focus

- A second recording appends to the draft without deduplicating intentional repeated words.
- Leaving Home, rotating, or reopening retains draft text; clearing occurs only after database insert succeeds.
- Old session/editor completions cannot overwrite a new session or a manually edited draft.
- AICore initialization/download/timeout/exception leaves local text and cancellation intact.
- Names, numbers, URLs, Latin tokens and emphasis survive every editing stage.

## Tasks

1. Materialize canonical source and apply known RNNT repair; establish reproducible build and baseline tests.
2. Add regression tests for unsafe name/meaning changes, protected tokens, emphasis and cancellation; fix safety boundaries and lazy AICore adapter.
3. Add regression tests for session text ownership and draft save failure; implement lifecycle-safe draft/session state, scrollable settings and visible Nano availability.
4. Run all unit tests, lint, release build, APK/manifest checks and emulator tests in GitHub Actions. Open a reviewable PR, record exact evidence and deliver the APK only with accurate validation status.

## Progress

- Canonical source restored from checksum-verified payload and v2.0.4 overlays.
- Confirmed historical v2.0.4 CI success, but it is not evidence for this new build.
- Confirmed existing code constructs AICore on the UI thread and permits edit-distance name/negation changes; regression tests follow.
