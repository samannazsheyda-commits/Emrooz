from pathlib import Path
import re

path = Path("app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt")
text = path.read_text(encoding="utf-8")

# The approved UI has changed copy between builds, so anchor on the listening-state
# expression rather than one exact Persian sentence.
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
modifier_pattern = re.compile(
    r"(Modifier\s*\.fillMaxWidth\(\)\s*\.weight\(1f\))(?!\.transcriptionGlow)"
)
card_region, count = modifier_pattern.subn(
    r"\1.transcriptionGlow(transcriptionCueState(listening, ready) == TranscriptionCueState.ACTIVE)",
    card_region,
    count=1,
)
if count != 1:
    raise SystemExit("transcription card modifier was not patched exactly once")

text = prefix + card_region + text[label_at:]
# Replace only the listening-state label, leaving ready/idle copy untouched.
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
