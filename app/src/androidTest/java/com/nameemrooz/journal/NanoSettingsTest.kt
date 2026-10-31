package com.nameemrooz.journal

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.nameemrooz.journal.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NanoSettingsTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun enabling_google_editor_on_an_unsupported_device_keeps_app_and_draft_usable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("emrooz_draft", Context.MODE_PRIVATE).edit().clear().commit()
        runBlocking {
            SettingsStore(context).setLock(false)
            SettingsStore(context).setEnhancedOnDeviceEditingEnabled(false)
        }
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            compose.waitUntil(30_000) { compose.onAllNodesWithText("از امروزت بگو").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithContentDescription("منو").performClick()
            compose.onNodeWithText("ویراستاری دقیق‌تر با مدل روی دستگاه").performScrollTo().performClick()
            compose.waitUntil(20_000) {
                compose.onAllNodes(hasText("مدل گوگل", substring = true) and hasText("در حال بررسی", substring = true).not()).fetchSemanticsNodes().isNotEmpty()
            }
            assertTrue(runBlocking { SettingsStore(context).enhancedOnDeviceEditingEnabled.first() })
            compose.onNodeWithContentDescription("برگشت").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("از امروزت بگو").fetchSemanticsNodes().isNotEmpty() }
            activity.recreate()
            compose.waitUntil(30_000) { compose.onAllNodesWithText("از امروزت بگو").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("نوشتن با صفحه‌کلید").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("نوشتهٔ امن")
            compose.onNode(hasSetTextAction()).assertTextEquals("نوشتهٔ امن")
        }
    }
}
