from pathlib import Path

path = Path("app/src/main/java/com/nameemrooz/journal/ui/NameEmroozApp.kt")
text = path.read_text(encoding="utf-8")

replacements = [
    (
        "import androidx.compose.foundation.Canvas\n",
        "import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.Canvas\n",
        "BorderStroke import",
    ),
    (
        "import androidx.compose.material3.Button\n",
        "import androidx.compose.material3.Button\nimport androidx.compose.material3.ButtonDefaults\n",
        "ButtonDefaults import",
    ),
    (
        "import androidx.compose.ui.text.font.FontFamily\nimport androidx.compose.ui.text.font.FontWeight\n",
        "import androidx.compose.ui.text.font.Font\nimport androidx.compose.ui.text.font.FontFamily\nimport androidx.compose.ui.text.font.FontWeight\n",
        "Font import",
    ),
    (
        "private val TranscriptFont = FontFamily.Serif",
        """private val TranscriptFont = FontFamily(Font(R.font.noto_naskh_arabic))
private val ActionFont = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_semibold, FontWeight.SemiBold)
)""",
        "embedded Persian fonts",
    ),
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
    (
        """                    DraftActionButton(
                        label = "پاک کردن",
                        icon = { Icon(Icons.Default.DeleteOutline, null) },
                        modifier = Modifier.weight(1f),
                        destructive = true,
                        onClick = { text = ""; everListened = false; error = null }
                    )
                    DraftActionButton(
                        label = "کپی متن",
                        icon = { Icon(Icons.Default.ContentCopy, null) },
                        modifier = Modifier.weight(1f),
                        onClick = {
                            clipboard.setText(AnnotatedString(text))
                            Toast.makeText(context, "متن کپی شد", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DraftActionButton(
                        label = "ذخیره",
                        icon = { Icon(Icons.Default.Save, null) },
                        modifier = Modifier.weight(1f),
                        emphasized = true,
                        onClick = {
                            val exactEditedText = text.trim()
                            if (exactEditedText.isNotBlank()) {
                                onSave(exactEditedText)
                                text = ""
                                everListened = false
                                Toast.makeText(context, "ذخیره شد", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )""",
        """                    DraftActionButton(
                        label = "ذخیره",
                        icon = { Icon(Icons.Default.Save, null) },
                        modifier = Modifier.weight(1.12f),
                        emphasized = true,
                        onClick = {
                            val exactEditedText = text.trim()
                            if (exactEditedText.isNotBlank()) {
                                onSave(exactEditedText)
                                text = ""
                                everListened = false
                                Toast.makeText(context, "ذخیره شد", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    DraftActionButton(
                        label = "کپی",
                        icon = { Icon(Icons.Default.ContentCopy, null) },
                        modifier = Modifier.weight(0.94f),
                        onClick = {
                            clipboard.setText(AnnotatedString(text))
                            Toast.makeText(context, "متن کپی شد", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DraftActionButton(
                        label = "پاک کردن",
                        icon = { Icon(Icons.Default.DeleteOutline, null) },
                        modifier = Modifier.weight(0.94f),
                        destructive = true,
                        onClick = { text = ""; everListened = false; error = null }
                    )""",
        "action order and hierarchy",
    ),
    (
        """@Composable
private fun DraftActionButton(
    label: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    when {
        emphasized -> Button(onClick = onClick, modifier = modifier.height(56.dp), shape = RoundedCornerShape(17.dp)) {
            icon(); Spacer(Modifier.width(5.dp)); Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        else -> OutlinedButton(onClick = onClick, modifier = modifier.height(56.dp), shape = RoundedCornerShape(17.dp)) {
            val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides color) {
                icon(); Spacer(Modifier.width(5.dp)); Text(label, fontSize = 16.sp)
            }
        }
    }
}""",
        """@Composable
private fun DraftActionButton(
    label: String,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val saveGreen = Color(0xFF879B5A)
    val container = when {
        emphasized -> saveGreen
        destructive -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    }
    val content = when {
        emphasized -> Color(0xFF11150C)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
    }
    val outline = when {
        emphasized -> null
        destructive -> BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.46f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.42f))
    }

    Button(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        border = outline,
        contentPadding = PaddingValues(horizontal = 9.dp, vertical = 0.dp)
    ) {
        icon()
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            fontFamily = ActionFont,
            fontSize = 16.sp,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1
        )
    }
}""",
        "redesigned DraftActionButton",
    ),
]

for old, new, label in replacements:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, got {count}")
    text = text.replace(old, new, 1)

path.write_text(text, encoding="utf-8")

# Final install build uses a fresh application ID so old test builds with lost/ephemeral
# signing keys cannot block installation with INSTALL_FAILED_UPDATE_INCOMPATIBLE.
gradle_path = Path("app/build.gradle.kts")
gradle_text = gradle_path.read_text(encoding="utf-8")
old_app_id = 'applicationId = "com.nameemrooz.journal"'
new_app_id = 'applicationId = "com.nameemrooz.journal.final"'
count = gradle_text.count(old_app_id)
if count != 1:
    raise SystemExit(f"applicationId patch: expected exactly one match, got {count}")
gradle_path.write_text(gradle_text.replace(old_app_id, new_app_id, 1), encoding="utf-8")

print("UI_PATCH_OK")
print("PACKAGE_PATCH_OK")
