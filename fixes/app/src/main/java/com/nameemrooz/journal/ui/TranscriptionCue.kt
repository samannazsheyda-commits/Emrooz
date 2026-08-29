package com.nameemrooz.journal.ui

enum class TranscriptionCueState {
    PREPARING,
    ACTIVE,
    IDLE
}

fun transcriptionCueState(listening: Boolean, ready: Boolean): TranscriptionCueState = when {
    listening -> TranscriptionCueState.ACTIVE
    !ready -> TranscriptionCueState.PREPARING
    else -> TranscriptionCueState.IDLE
}
