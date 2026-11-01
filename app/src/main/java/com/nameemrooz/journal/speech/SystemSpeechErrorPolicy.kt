package com.nameemrooz.journal.speech

/**
 * Preserve the provider's highest-ranked nonempty result. Requesting formatting is
 * only a hint: some providers still return ranked alternatives, so a second result
 * with different words must never replace the first one.
 */
internal object SystemSpeechResultPolicy {
    fun select(results: List<String>): String = results.firstOrNull { it.isNotBlank() }.orEmpty()
}

enum class SystemSpeechFailure {
    NO_MATCH,
    SPEECH_TIMEOUT,
    BUSY,
    NETWORK,
    NETWORK_TIMEOUT,
    SERVER,
    CLIENT,
    PERMISSION,
    LANGUAGE,
    OTHER,
}

enum class SystemSpeechAction { RETRY, FALLBACK, STOP }

class SystemSpeechErrorPolicy(
    private val maxRecoverableRetries: Int = 1,
) {
    private var recoverableRetries = 0

    fun onFailure(failure: SystemSpeechFailure): SystemSpeechAction = when (failure) {
        SystemSpeechFailure.PERMISSION -> SystemSpeechAction.STOP
        SystemSpeechFailure.NO_MATCH,
        SystemSpeechFailure.SPEECH_TIMEOUT,
        SystemSpeechFailure.BUSY -> {
            if (recoverableRetries < maxRecoverableRetries) {
                recoverableRetries += 1
                SystemSpeechAction.RETRY
            } else {
                SystemSpeechAction.FALLBACK
            }
        }
        SystemSpeechFailure.NETWORK,
        SystemSpeechFailure.NETWORK_TIMEOUT,
        SystemSpeechFailure.SERVER,
        SystemSpeechFailure.CLIENT,
        SystemSpeechFailure.LANGUAGE,
        SystemSpeechFailure.OTHER -> SystemSpeechAction.FALLBACK
    }

    fun onSuccess() {
        recoverableRetries = 0
    }

    fun reset() {
        recoverableRetries = 0
    }
}
