package com.nameemrooz.journal.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Follow new transcript revisions after layout; a manual scroll alone does not move the reader. */
@Composable
internal fun TranscriptText(text: String, placeholder: String, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    LaunchedEffect(text, scroll.maxValue) {
        scroll.scrollTo(scroll.maxValue)
    }
    Text(
        text = text.ifBlank { placeholder },
        modifier = modifier.verticalScroll(scroll),
        textAlign = TextAlign.Start,
        style = MaterialTheme.typography.bodyLarge,
    )
}

/** Local selection belongs to the editor, while the persistent draft remains the source of text. */
@Composable
internal fun DraftEditor(text: String, onEdit: (String) -> Unit, modifier: Modifier = Modifier) {
    var field by remember { mutableStateOf(TextFieldValue(text, TextRange(text.length))) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(text) {
        if (field.text != text) field = TextFieldValue(text, TextRange(text.length))
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    OutlinedTextField(
        value = field,
        onValueChange = { next ->
            field = next
            if (next.text != text) onEdit(next.text)
        },
        modifier = modifier.fillMaxSize().focusRequester(focus),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Start),
        shape = RoundedCornerShape(12.dp),
    )
}
