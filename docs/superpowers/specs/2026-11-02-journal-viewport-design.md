# Journal viewport and archive corrections

The user reports hidden live text after the page fills, an unusable editor when the keyboard opens, a poorly placed archive link, and cramped archive metadata. They have already asked to proceed without further design questions.

Keep the approved copper/cream/navy artwork, Abar/Lato typography, privacy behavior, device speech, local punctuation and optional Nano. Live transcript follows each speech update after layout; static reading remains scrollable. Editing gets an IME-aware compact layout with at least a useful text area and reachable Save/Done controls. Hide recording-only controls during editing rather than squeezing the editor. Use adjustResize for older Android versions and consumed insets without double keyboard padding.

Move archive access to an always-visible labelled top-bar button. Notes and archived notes get fixed tabs above a scrolling list. Each card separates weekday/time, date, and a three-line text preview; use logical Start alignment for RTL. Preserve existing saved notes, draft, and archive/restore behavior.

Alternatives considered: scrolling the entire home page leaves recording controls offscreen; keeping every control above the IME leaves no editor space. A compact editing state and bounded text viewport directly address the reported failures.

Deliver an update signed with the existing private key, production APK, and the previously requested icon/screenshot ZIP from the corrected actual UI. Screenshots use synthetic notes on an isolated emulator; production FLAG_SECURE remains enabled.
