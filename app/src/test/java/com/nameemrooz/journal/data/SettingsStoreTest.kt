package com.nameemrooz.journal.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun google_editor_defaults_on_and_preserves_an_explicit_opt_out() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val data = PreferenceDataStoreFactory.create(scope = scope) {
                folder.root.resolve("settings.preferences_pb")
            }
            val settings = SettingsStore(data)
            assertTrue("Fresh installs should enable Google editing", settings.enhancedOnDeviceEditingEnabled.first())
            settings.setEnhancedOnDeviceEditingEnabled(false)
            assertFalse(SettingsStore(data).enhancedOnDeviceEditingEnabled.first())
            settings.setEnhancedOnDeviceEditingEnabled(true)
            assertTrue(SettingsStore(data).enhancedOnDeviceEditingEnabled.first())
        } finally { scope.cancel() }
    }
}
