from pathlib import Path
import re

path = Path("app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt")
text = path.read_text(encoding="utf-8")

# Anchor on the listening-state expression, not one exact Persian sentence.
label_match = re.search(r'Text\s*\(\s*if\s*\(\s*listening\s*\)\s*"([^"]+)"', text)
if not label_match:
    label_match = re.search(r'if\s*\(\s*listening\s*\)\s*"([^"]+)"', text)
if not label_match:
    raise SystemExit("live transcription listening label not found")

label_at = label_match.start()
card_at = text.rfind("Card(", 0, label_at)
if card_at < 0:
    raise SystemExit("transcription card not found")

prefix = text[:card_at]
card_region = text[card_at:label_at]
# Layout chains differ between app revisions. Patch the first Modifier belonging to
# this exact Card instead of assuming a particular fill/weight chain.
card_region, count = re.subn(
    r'\bModifier\b(?!\.transcriptionGlow)',
    'Modifier.transcriptionGlow(transcriptionCueState(listening, ready) == TranscriptionCueState.ACTIVE)',
    card_region,
    count=1,
)
if count != 1:
    raise SystemExit("transcription card modifier was not patched exactly once")

text = prefix + card_region + text[label_at:]
text, count = re.subn(
    r'(if\s*\(\s*listening\s*\)\s*)"[^"]+"',
    r'\1"در حال تبدیل صدا به متن..."',
    text,
    count=1,
)
if count != 1:
    raise SystemExit("listening label replacement failed")

path.write_text(text, encoding="utf-8")
print("UI_PATCH_OK")
