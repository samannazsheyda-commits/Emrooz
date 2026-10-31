package com.nameemrooz.journal.speech

enum class SpeechMode {
    PRIVATE_OFFLINE,
    SYSTEM_HIGH_ACCURACY;

    companion object {
        fun fromStored(value: String?): SpeechMode =
            entries.firstOrNull { it.name == value } ?: PRIVATE_OFFLINE
    }
}
