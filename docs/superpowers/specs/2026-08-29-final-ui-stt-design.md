# Emrooz Final UI + Speech Design

## Goal
Deliver the next Android build of «امروز» with a professional cream/olive visual system, distinct splash/home copy, an editable content-aware letter title, lower perceived live-transcription latency, and safer offline correction of Persian names and common colloquial verb forms.

## Product copy

### Splash
- Brand: «امروز»
- Under the brand, use the approved olive mark: solid dot → short wave → two horizontal strokes.
- Tagline: «هر چیزی که بگی مهمه»
- Do not repeat the home instruction on splash.

### Home before recording
- Primary heading: «از امروزت بگو»
- Supporting copy: «حتی چیزهای کوچیک هم مهم هستن. فقط بگو، من برات می‌نویسم و نگه می‌دارم.»
- Current Persian time is prominent and Persian-digit formatted.
- Date remains visible but visually secondary to the time.

### While recording
- Replace long helper copy with the short state: «دارم می‌نویسم…»
- No waveform/equalizer. A subtle light/scan cue inside the transcript card indicates active conversion.

### After recording
- Show section label «نامه امروزت».
- Generate a short title from that day’s transcript, preferably 3–6 words.
- The generated title is editable before saving.
- A manual title edit always wins; saving must not regenerate over the user’s title.

## Visual system
- Warm cream background, muted olive primary color, high legibility and restrained shadows.
- No decorative leaves in the launcher icon.
- Splash uses the approved «امروز» wordmark + approved line mark.
- Launcher icon uses the same brand language without leaf decoration.
- Doran is the desired heading/brand typography where the app already has a licensable embedded Doran asset available; otherwise preserve the currently embedded readable Persian body font and do not redistribute private font files through the public repository.
- The primary call to action is «ذخیره نامه» and is visually dominant.
- «کپی» and «پاک کردن» are secondary compact actions, aligned to the same baseline and height; delete uses a restrained error treatment.
- Avoid raw default Material appearance: consistent radii, spacing, icon sizing, and typography are required.

## Title generation
- Title generation is fully offline.
- It reads the final cleaned transcript.
- It uses detected first names when available and combines them with contextual cues (e.g. دیدار/صحبت/خانواده/خرید/خانه/کار/پیاده‌روی) to produce natural short titles.
- Example: a transcript about meeting Soheil can become «دیدار امروز با سهیل».
- Fallback title must still be derived from meaningful transcript words rather than always returning a fixed «قصه امروز من».
- Title input is editable before save and editable again in entry detail.

## Speech responsiveness
- Keep the existing on-device Shenava streaming CTC recognizer.
- Reduce perceived latency by feeding smaller microphone chunks than the current 4096-sample block while keeping decoding stable.
- Do not block recognizer readiness on loading the large name lexicon; name lexicon warm-up happens independently after the recognizer becomes ready.
- Reduce avoidable final flush delay without sacrificing the last spoken word.
- Audio remains RAM-only; no audio file is written.

## Persian post-processing
- Keep correction conservative: never rewrite arbitrary ordinary words merely because they are edit-distance-close to a dictionary item.
- Name correction remains context-gated by a recognized first name and the offline surname lexicon.
- Add a focused colloquial-verb normalization/correction layer for common spoken forms and recurrent ASR variants (e.g. میخوام/می خوام → می‌خوام, میرم/می رم → می‌رم, میگم/می گم → می‌گم, نمیدونم → نمی‌دونم), while preserving the user’s conversational register.
- Continue removing obvious false-start stutters and immediate duplicated phrases only on the final pass.
- Preserve intentional emphatic repeats such as «نه نه».

## Persistence
- Existing JournalEntry title field is reused; no database migration is required.
- Saving accepts both final text and the currently edited title.
- Detail view allows editing both title and body.

## Platform/privacy constraints
- Android minSdk remains 26.
- target/compile SDK remain 35.
- Runtime remains arm64-v8a unless the existing native runtime gains other verified ABIs.
- RECORD_AUDIO remains required.
- INTERNET permission must remain absent.
- Speech-to-text and title generation are fully offline.
- FLAG_SECURE/private journal behavior remains unchanged.

## Verification
- Unit tests cover content-aware titles, user-edited title preservation, colloquial verb normalization, stutter preservation/removal cases, and speech latency configuration constants.
- Release CI must run unit tests, assemble release, unzip-test the APK, verify model/name assets/native library, verify v2+ signing, verify no INTERNET permission, and verify the expected version/package metadata.
- Do not claim physical microphone inference has been device-tested unless an actual Android device is available.