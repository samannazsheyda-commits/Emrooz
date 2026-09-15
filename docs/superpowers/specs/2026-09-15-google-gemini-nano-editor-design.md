# Emrooz — On-device Google/Gemini Nano Editor Design

Date: 2026-09-15
Status: Approved design, implementation not started

## Goal

Add an optional Google on-device writing assistant to Emrooz without weakening the existing offline/private path. The feature is not a direct Gboard editor integration. It is an on-device Google/Gemini Nano editing layer when the device exposes a compatible ML Kit/AICore path.

The app must remain fully usable when the Google on-device model is unavailable. Shenava remains the guaranteed offline ASR path and the current local deterministic editor + punctuation model remain the permanent fallback.

## Locked product constraints

- Default app behavior remains privacy-first and offline-capable.
- Shenava v1.5 RNNT stays available as the guaranteed offline speech engine.
- Android/System speech remains an optional higher-accuracy speech path.
- No audio is persisted to disk.
- Existing Abar/Lato typography, approved UI, archive behavior, biometric-first lock, and live transcript glow are unchanged.
- The removed voice command «اصلاح کن» must not return.
- The editor must not paraphrase diary content, change names, alter numbers, remove emphasis, or invent facts.
- If the Google editor is unavailable, slow, fails, or produces unsafe output, Emrooz keeps the existing local result with no interruption.

## Architecture

Introduce a new writing-provider boundary:

`WritingEnhancer`

Implementations:

1. `GeminiNanoWritingEnhancer`
   - Optional on-device Google/Gemini Nano path.
   - Uses the supported Android/ML Kit/AICore on-device interface available on the target device.
   - Receives text only, never raw audio.
   - Runs only on stable/final text segments, never every partial ASR token.

2. `LocalWritingEnhancer`
   - Existing deterministic Persian cleanup + local punctuation model.
   - Always available.
   - Acts as fallback and baseline.

Coordinator:

`WritingEnhancementCoordinator`

The coordinator selects Google on-device enhancement only when the runtime reports the feature/model as available. Otherwise it uses the local path immediately.

## Data flow

Offline Shenava path:

`Mic → Shenava RNNT → conservative cleanup → stable segment → optional Gemini Nano editor → lexical safety gate → local punctuation model → final text`

System/Google speech path:

`Mic → Android/System SpeechRecognizer → conservative cleanup → stable segment → optional Gemini Nano editor → lexical safety gate → local punctuation model → final text`

If Gemini Nano is unavailable or rejected:

`stable segment → LocalWritingEnhancer → final text`

No accepted transcript is deleted during provider switching or fallback.

## Editing contract

The Google on-device prompt must be deliberately narrow. It may:

- fix obvious Persian spelling errors,
- fix Arabic/Persian character variants,
- add or correct half-spaces,
- add punctuation,
- normalize obvious whitespace,
- preserve mixed Persian-English tokens.

It must not:

- rewrite style,
- summarize,
- make prose “nicer”,
- change sentence meaning,
- replace personal names,
- alter dates, times, amounts, phone numbers, or other numeric values,
- remove repeated emphasis such as «خیلی خیلی»,
- translate Persian/English content,
- add information that was not spoken.

## Safety gate

Google output is advisory, never authoritative.

Before replacing the current text, the result passes a local deterministic validator. At minimum it must verify:

- normalized numeric tokens are identical,
- protected mixed-language tokens are preserved,
- personal-vocabulary replacements are not reversed,
- no suspicious lexical additions or deletions occurred,
- emphasis repetitions are preserved unless the existing deterministic editor already classifies them as filler,
- output is non-empty and structurally plausible.

If the candidate fails any check, the candidate is discarded and the original/local text remains unchanged.

The existing punctuation-only `LexicalSafety` rule remains in force for the local punctuation model. The Gemini Nano path may make high-confidence orthographic edits, so it gets a separate stricter lexical-diff validator rather than reusing punctuation-only acceptance.

## Availability and fallback

Gemini Nano is optional and device-dependent.

At runtime the app must distinguish:

- supported and ready,
- supported but model not ready/downloaded,
- temporarily unavailable,
- permanently unsupported on this device,
- inference failure/timeout.

Behavior:

- Unsupported: hide/disable the Google editor option and use local editing.
- Model not ready: show a short non-blocking state; local editing continues immediately.
- Timeout/error: keep the local result and mark Google enhancement unavailable for the current session after repeated failures.
- Recovery on a later session is allowed.

No user text may be lost while switching providers.

## Privacy contract

- Gemini Nano receives text only after speech recognition; it never receives the app's audio buffer.
- Inference must be on-device.
- The existing no-audio-file rule remains unchanged.
- The current `android.permission.INTERNET` absence is treated as a hard privacy requirement. If the chosen Google on-device SDK unexpectedly requires an app-level INTERNET permission for inference, the Google editor integration must not be enabled until that conflict is explicitly reviewed. The feature must not silently weaken the privacy contract.
- If model provisioning is managed by Android/AICore outside the app, the UI may explain that device services can download the model separately.

## UI

Add one setting under writing/transcription preferences:

Persian:
- `ویراستاری دقیق‌تر با مدل روی دستگاه`
- Helper text: `اگر مدل گوگل روی گوشی پشتیبانی شود، املا و نیم‌فاصله با حفظ معنی بهتر می‌شود. در غیر این صورت ویراستار آفلاین امروز استفاده می‌شود.`

English:
- `Enhanced on-device editing`
- Helper text: `Uses the device's supported Google on-device model when available; otherwise Emrooz keeps its local editor.`

The option should never block recording or saving. If unavailable, display a simple disabled/unavailable state rather than an error dialog.

## Performance

- Do not load the Google model during app startup.
- Lazy-init only after the feature is enabled and the first stable text segment is available.
- Never run the Google editor on every partial ASR callback.
- Use stable-pause/final boundaries only.
- Run inference off the main thread.
- Apply a bounded timeout; on timeout, keep local text immediately.
- Do not preload both heavy writing models unnecessarily.

## Error handling

Every enhancement step is fail-open toward preserving user text:

- enhancer exception → return input/local text,
- unsafe candidate → reject candidate,
- provider unavailable → use local path,
- model initialization failure → disable provider for the current session,
- process recreation → no dependency on unfinished enhancement; saved text remains local-safe text.

## Testing

Unit tests:

- provider-selection policy,
- unsupported-device fallback,
- timeout/error fallback,
- no transcript loss during fallback,
- numeric preservation,
- proper-name preservation,
- mixed Persian-English preservation,
- repeated-emphasis preservation,
- unsafe rewrite rejection,
- safe orthographic edit acceptance,
- no regression of current punctuation-only safety tests.

Integration/contract tests:

- no `android.permission.INTERNET` in manifest/APK,
- no audio-file write path,
- feature compiles when Google on-device API dependency is enabled,
- app compiles/runs when the on-device model is unavailable,
- app saves local text even if enhancement fails,
- release APK still includes Shenava and local punctuation assets with pinned hashes,
- emulator/physical-device runtime smoke where possible.

Because Gemini Nano availability is hardware/service dependent, CI must not require successful Nano inference to pass the release. CI must require correct capability detection and fallback. A supported physical-device test should be used for actual Nano inference validation before calling the feature production-verified.

## Out of scope

- Cloud Gemini API editing.
- Server-side grammar correction.
- Direct embedding or automation of Gboard's private editor.
- Semantic rewriting or stylistic polishing.
- Reintroducing voice editing commands.
- Replacing Shenava with Gemini.

## Acceptance criteria

The feature is complete only when:

1. Emrooz still works fully offline without Google on-device support.
2. On a supported device, enabling enhanced editing can improve spelling/half-space/punctuation without changing meaning.
3. Unsafe edits are deterministically rejected.
4. Any Google model failure leaves the existing text intact.
5. No audio is persisted.
6. The app privacy contract is not weakened silently.
7. Existing UI, fonts, archive, biometric, and speech-engine behavior remain intact.
8. Unit tests, lint, release build, APK integrity checks, and runtime smoke pass.
9. A supported real device verifies at least one successful on-device Gemini Nano enhancement before the feature is labeled production-verified.
