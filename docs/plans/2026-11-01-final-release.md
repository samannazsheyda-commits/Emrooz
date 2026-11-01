# Emrooz final release

The user requested completion and installable deliverables without further design questions.

1. Remove the Shenava model, runtime dependency and active source. Bundle pinned Persian-finetuned Whisper Base Q8_0 (81,768,602 bytes) and English Whisper Tiny.en Q5_1 (32,166,155 bytes). The generic multilingual Base candidate was rejected after real Persian smoke results with a CPU-only, cancelable Android JNI runtime. Offline speech remains the default; device speech requires explicit privacy disclosure acceptance.
2. Quantize the original punctuation model's MatMul and Gather weights to INT8. Verify original and quantized I/O and punctuation agreement on a fixed Persian/English sample set. Render punctuation by insertion into original text, retaining all words, whitespace and long text. Keep optional on-device Gemini Nano enabled by default on compatible devices.
3. Preserve approved bitmap branding and the exact launcher name «امروز». Finish clear/record-again actions with cancellation, stale-result rejection, persisted-draft removal, microphone readiness and lock/background guards.
4. Build and check unit tests, lint, native/model smoke tests and emulator journal flows on Android 8 and 15. Verify actual model bytes, native ABI/alignment, signatures, release flags, launcher name and absence of Shenava in each APK.
5. Sign the publication package with the existing persistent private certificate; build a separate update package with the same application ID/certificate as the last delivered corrected app so existing notes can be retained. Keep all keys outside Git. Save the measured APKs and an honest QA report as downloadable deliverables.

No guarantee of perfect Persian recognition, universal Nano hardware support, store approval or absence of Google's unknown-app scan prompt is implied by a successful build. Human speech quality and device performance must be reported separately from build and lifecycle checks.
