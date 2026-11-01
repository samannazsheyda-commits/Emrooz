package com.nameemrooz.journal.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSpeechSupportTest {
    @Test fun installedPersianIsReadyAcrossLocaleVariants() {
        assertEquals(DeviceSpeechReadiness.READY, DeviceSpeechSupport.classify("fa-IR", listOf("FA"), emptyList(), emptyList()))
        assertEquals(DeviceSpeechReadiness.UNSUPPORTED, DeviceSpeechSupport.classify("en-US", listOf("en-GB"), emptyList(), emptyList()))
    }
    @Test fun downloadableOrPendingPersianMustNotBeReportedReady() {
        assertEquals(DeviceSpeechReadiness.DOWNLOAD_REQUIRED, DeviceSpeechSupport.classify("fa-IR", listOf("en-US"), listOf("fa-IR"), emptyList()))
        assertEquals(DeviceSpeechReadiness.DOWNLOAD_REQUIRED, DeviceSpeechSupport.classify("fa-IR", emptyList(), emptyList(), listOf("fa")))
    }
    @Test fun otherLanguagesDoNotProvePersianSupport() {
        assertEquals(DeviceSpeechReadiness.UNSUPPORTED, DeviceSpeechSupport.classify("fa-IR", listOf("en-US"), listOf("de-DE"), emptyList()))
    }
    @Test fun emptySupportResponseRemainsUnknown() {
        assertEquals(DeviceSpeechReadiness.UNKNOWN, DeviceSpeechSupport.classify("fa-IR", emptyList(), emptyList(), emptyList()))
    }
}
