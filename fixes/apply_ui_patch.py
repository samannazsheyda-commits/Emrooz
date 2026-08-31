from pathlib import Path

path = Path("app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt")
text = path.read_text(encoding="utf-8")

replacements = [
    (
        """                readOnly = listening,\n                scrollState = scrollState,""",
        """                readOnly = listening,\n                converting = listening,\n                scrollState = scrollState,""",
        "home TranscriptEditor call",
    ),
    (
        """    readOnly: Boolean,\n    scrollState: androidx.compose.foundation.ScrollState,""",
        """    readOnly: Boolean,\n    converting: Boolean = false,\n    scrollState: androidx.compose.foundation.ScrollState,""",
        "TranscriptEditor signature",
    ),
    (
        """        modifier = modifier.border(1.dp, accent.copy(alpha = 0.22f), shape),""",
        """        modifier = modifier\n            .transcriptionGlow(converting)\n            .border(1.dp, accent.copy(alpha = 0.22f), shape),""",
        "TranscriptEditor card modifier",
    ),
    (
        '                        listening -> "برای توقف لمس کن"',
        '                        listening -> "در حال تبدیل صدا به متن..."',
        "live conversion label",
    ),
]

for old, new, label in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")
print("UI_PATCH_OK")
