# Emrooz v1.1.5 Branding and Archive Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the working v1.1.4 speech engine while replacing splash/app icon branding with the approved high-resolution icon, removing name collection, and making archive date/time fully Persian with Doran typography.

**Architecture:** Build from the exact verified v1.1.4 reconstructed source and overlay only UI/resources/data-presentation changes. Do not modify speech engine/model code. Add small pure formatting helpers for Persian date/time and archive labels so behavior is unit-testable before wiring Compose UI.

**Tech Stack:** Android/Kotlin, Jetpack Compose, Room, GitHub Actions, existing sherpa-onnx Persian ASR.

**Spec:** Current user-approved v1.1.4 behavior plus the 2026-08-29 branding/archive requirements in this conversation.

## Global Constraints

- Keep v1.1.4 speech recognition implementation and model assets byte-for-byte unchanged.
- Splash artwork must use the user-approved icon exactly, at high resolution, without decorative leaves.
- Launcher icon must use the same approved artwork.
- Remove the user-name collection flow entirely.
- Archive cards must show exact Persian calendar date and Persian time instead of a person name.
- All displayed date/time digits must be Persian.
- Date/time text and the «نامه امروزت» card typography must use Doran.
- Preserve offline-only privacy behavior; do not add INTERNET permission and do not save audio.

---

### Task 1: Inspect exact v1.1.4 source

**Files:**
- Create: `.github/workflows/inspect-v115-base.yml`

- [ ] Reconstruct v1.1.4 exactly, including `payload/v114.part*`.
- [ ] Upload `app/src`, Gradle config, and manifest as a temporary artifact.
- [ ] Inspect actual name, archive, splash, icon, date/time and typography code paths before patching.

### Task 2: Add RED tests for Persian archive formatting and no-name flow

**Files:**
- Test: `app/src/test/java/com/nameemrooz/journal/...`

- [ ] Add tests asserting Persian digits for hours/minutes and calendar date.
- [ ] Add tests asserting archive display label contains date + time and no profile/name value.
- [ ] Run tests and confirm expected RED failures before production changes.

### Task 3: Implement Persian date/time formatting and remove name UI

**Files:**
- Modify: exact UI/helper files found in Task 1.

- [ ] Implement minimal Persian digit/date/time helpers to satisfy Task 2.
- [ ] Remove first-name onboarding/input and all name display dependencies from Compose navigation.
- [ ] Wire archive cards to persisted entry timestamps with exact Persian date + time.
- [ ] Run focused tests GREEN, then full unit suite.

### Task 4: Apply approved branding and Doran typography

**Files:**
- Modify/create Android drawable/mipmap/font resources and Compose UI references.

- [ ] Bundle the approved high-resolution icon resource and use it on splash unchanged.
- [ ] Generate launcher mipmaps/adaptive icon layers from the same source artwork without redesigning it.
- [ ] Apply Doran to all app date/time text and the «نامه امروزت» card.
- [ ] Verify splash contains no substitute mark or decorative leaves.

### Task 5: Regression and release verification

**Files:**
- Create/update v1.1.5 build workflow.

- [ ] Assert no speech source/model files differ from verified v1.1.4.
- [ ] Run full unit suite repeatedly, lint, and release build.
- [ ] Verify APK structure, signature, package/version, offline models, icon assets, Doran resource references, and absence of INTERNET permission/audio-file writing.
- [ ] Upload only the verified release APK artifact.
