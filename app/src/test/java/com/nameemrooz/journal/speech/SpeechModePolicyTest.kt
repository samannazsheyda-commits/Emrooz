package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechModePolicyTest {
    @Test fun missingPreferenceDefaultsToPrivateOffline() {
        assertEquals(SpeechMode.PRIVATE_OFFLINE, SpeechMode.fromStored(null))
        assertEquals(SpeechMode.PRIVATE_OFFLINE, SpeechMode.fromStored("unexpected"))
    }

    @Test fun explicitSystemPreferenceIsRestored() {
        assertEquals(SpeechMode.SYSTEM_HIGH_ACCURACY, SpeechMode.fromStored("SYSTEM_HIGH_ACCURACY"))
    }

    @Test fun recoverableSystemFailuresRetryOnceThenFallback() {
        val policy = SystemSpeechErrorPolicy(maxRecoverableRetries = 1)
        assertEquals(SystemSpeechAction.RETRY, policy.onFailure(SystemSpeechFailure.NO_MATCH))
        assertEquals(SystemSpeechAction.FALLBACK, policy.onFailure(SystemSpeechFailure.NO_MATCH))
    }

    @Test fun networkFailureFallsBackAndPermissionStops() {
        val policy = SystemSpeechErrorPolicy(maxRecoverableRetries = 1)
        assertEquals(SystemSpeechAction.FALLBACK, policy.onFailure(SystemSpeechFailure.NETWORK))
        policy.reset()
        assertEquals(SystemSpeechAction.STOP, policy.onFailure(SystemSpeechFailure.PERMISSION))
    }
}
