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

    @Test fun privatePreferenceKeepsItsStoredValueAcrossEngineMigration() {
        assertEquals(SpeechMode.PRIVATE_OFFLINE, SpeechMode.fromStored("PRIVATE_OFFLINE"))
    }

    @Test fun formattedPhoneResultKeepsPunctuationOnTheFirstResult() {
        assertEquals("امروز، خوب بود.", SystemSpeechResultPolicy.select(listOf("امروز، خوب بود.", "امروز خوب بود")))
        assertEquals("امروز خوب بود", SystemSpeechResultPolicy.select(listOf("", "امروز خوب بود")))
        assertEquals("امروز خوب بود", SystemSpeechResultPolicy.select(listOf("امروز خوب بود")))
        assertEquals("", SystemSpeechResultPolicy.select(emptyList()))
    }

    @Test fun providersIgnoringFormattingKeepTheHighestRankedHypothesis() {
        assertEquals("خیلی خوب بود.", SystemSpeechResultPolicy.select(listOf("خیلی خوب بود.", "خیلی خیلی خوب بود")))
        assertEquals("انتخاب اول", SystemSpeechResultPolicy.select(listOf("انتخاب اول", "انتخاب دوم")))
        assertEquals("متن خام", SystemSpeechResultPolicy.select(listOf("", "متن خام")))
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
