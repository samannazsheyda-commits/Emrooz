package com.nameemrooz.journal.speech

/**
 * Compatibility name retained for source/tests that reference the old class.
 * v1.2.6 production uses LiveTranscriptStabilizer directly.
 */
class StableLiveTranscript(
    minStableMs: Long = 280,
    @Suppress("UNUSED_PARAMETER") windowMs: Long = 600,
) {
    private val delegate = LiveTranscriptStabilizer(minAgreementMs = minStableMs)

    fun offer(value: String, nowMs: Long): String? = delegate.offer(value, nowMs)
    fun reset() = delegate.reset()
}
