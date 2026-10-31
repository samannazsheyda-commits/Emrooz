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
            compose.onNodeWithText("ویراستاری با جمینای نانو").performScrollTo().performClick()
            val terminalStatuses = listOf(
                "مدل گوگل آماده است",
                "مدل گوگل فعلاً در دسترس نیست؛ ویراستار محلی فعال است",
                "مدل گوگل فعلاً روی این گوشی در دسترس نیست؛ ویراستار محلی فعال است",
                "مدل در حال دانلود است؛ ویراستار محلی فعال است",
                "مدل به دانلود نیاز دارد؛ با اولین نوشته آماده‌سازی آغاز می‌شود",
            )
            compose.waitUntil(20_000) {
                terminalStatuses.any { compose.onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }
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
