from pathlib import Path
import re

path = Path("app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt")
text = path.read_text(encoding="utf-8")

old_label = '"در حال نوشتن..."'
new_label = '"در حال تبدیل صدا به متن..."'
if old_label not in text:
    raise SystemExit("live transcription label not found")
label_at = text.index(old_label)
card_at = text.rfind("Card(", 0, label_at)
if card_at < 0:
    raise SystemExit("transcription card not found")

prefix = text[:card_at]
card_region = text[card_at:label_at]
pattern = re.compile(r"(Modifier\s*\.fillMaxWidth\(\)\s*\.weight\(1f\))(?!\.transcriptionGlow)")
replacement = r"\1.transcriptionGlow(transcriptionCueState(listening, ready) == TranscriptionCueState.ACTIVE)"
card_region, count = pattern.subn(replacement, card_region, count=1)
if count != 1:
    raise SystemExit("transcription card modifier was not patched exactly once")

text = prefix + card_region + text[label_at:]
text = text.replace(old_label, new_label, 1)
path.write_text(text, encoding="utf-8")
print("UI_PATCH_OK")
