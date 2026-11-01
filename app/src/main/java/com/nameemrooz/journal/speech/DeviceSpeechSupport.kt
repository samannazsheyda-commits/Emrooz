package com.nameemrooz.journal.speech

internal enum class DeviceSpeechReadiness { READY, DOWNLOAD_REQUIRED, UNSUPPORTED, UNKNOWN }

internal object DeviceSpeechSupport {
    fun classify(languageTag: String, installed: List<String>, downloadable: List<String>, pending: List<String>): DeviceSpeechReadiness {
        val requested = languageTag.replace('_', '-').lowercase()
        val language = requested.substringBefore('-')
        fun List<String>.matches() = any {
            val available = it.replace('_', '-').lowercase()
            available == requested || available == language
        }
        return when {
            installed.matches() -> DeviceSpeechReadiness.READY
            downloadable.matches() || pending.matches() -> DeviceSpeechReadiness.DOWNLOAD_REQUIRED
            installed.isEmpty() && downloadable.isEmpty() && pending.isEmpty() -> DeviceSpeechReadiness.UNKNOWN
            else -> DeviceSpeechReadiness.UNSUPPORTED
        }
    }
}
