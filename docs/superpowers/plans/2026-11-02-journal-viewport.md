# Journal viewport and archive implementation plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep live speech and edited text visible and make saved notes easy to reach and read.
**Architecture:** Retain Activity/Compose/ViewModel/Room and existing styling. Introduce a bounded transcript composable with a retained ScrollState and layout-aware follow effect. Compact Home while editing/IME is visible. Keep notes tabs outside the LazyColumn.
**Tech Stack:** Android Kotlin, Compose Material3, Room, AndroidJUnit4.
**Spec:** docs/superpowers/specs/2026-11-02-journal-viewport-design.md

## Global Constraints
- Label remains امروز. Preserve exact existing logo and cream/navy themes.
- Preserve private signing identity, app IDs, notes, FLAG_SECURE, and no INTERNET permission.
- No speech/AI model changes.

## Review Focus
- Long Persian transcripts, including partial results replacing prior suffixes.
- Real soft keyboard on small screens and Android 8/15.
- Large font setting and RTL/LTR metadata.
- Many notes must not hide the archive navigation.
- Archive/restore must retain note text and timestamps.

## Task 1: Viewport and navigation regression coverage
- [x] Add emulator tests for live transcript reaching its tail after speech callbacks, useful editor height and reachable save under a real IME, fixed archive access with many notes, separated preview/date metadata, detail save under IME.
- [x] Run the tests against the old product and record the relevant failures.

## Task 2: Correct viewport and archive layout
- [x] Add retained, layout-aware live-text scrolling without moving manual editor selection.
- [x] Add adjustResize and compact editor layout; keyboard dismissal and save remain accessible.
- [x] Move archive access to Home header; fixed active/archive tabs; readable note preview cards.
- [x] Run all unit tests, lint, build and Android flows on API 26 and 35; inspect real screenshots in both themes.

## Task 3: Release and store assets
- [x] Bump patch version, sign with the existing private key locally, verify cert, name, alignment and package.
- [x] Export original icons at 48/72/96 and 128/256/512; capture actual UI in both themes with synthetic content.
- [x] Inspect and save APKs and asset ZIP as persistent download files.
- [x] Obtain focused code review, address material findings, and report actual verification limitations.
