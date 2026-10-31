package com.nameemrooz.journal

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nameemrooz.journal.data.SettingsStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real Activity/Compose/DataStore/Room boundaries on an Android emulator. */
@RunWith(AndroidJUnit4::class)
class JournalFlowTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()

    @Before fun prepare() = runBlocking {
        SettingsStore(context).setLock(false)
        context.getSharedPreferences("emrooz_draft", Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }

    private fun waitForHome() {
        compose.waitUntil(timeoutMillis=30_000) {
            compose.onAllNodesWithText("از امروزت بگو").fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun draft_survives_settings_and_activity_recreation_then_saves_to_room() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            waitForHome()
            compose.onNodeWithText("نوشتن با صفحه‌کلید").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("خیلی خیلی خوشحالم ۱۲٫۵")
            compose.onNodeWithContentDescription("منو").performClick()
            compose.onNodeWithText("ویراستاری دقیق‌تر با مدل روی دستگاه").performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("برگشت").performClick()
            compose.onNodeWithText("خیلی خیلی خوشحالم ۱۲٫۵").assertIsDisplayed()
            activity.recreate()
            waitForHome()
            compose.onNodeWithText("خیلی خیلی خوشحالم ۱۲٫۵").assertIsDisplayed()
            compose.onNodeWithText("ذخیره").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("نوشتن با صفحه‌کلید").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("آرشیو نوشته‌ها").performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("نوشته‌های قبلی").fetchSemanticsNodes().isNotEmpty()
            }
            // The calendar row opens the entry saved through the real Room DAO.
            val db = com.nameemrooz.journal.data.AppDatabase.get(context)
            val text = runBlocking { db.journalDao().active().first().first().text }
            org.junit.Assert.assertEquals("خیلی خیلی خوشحالم ۱۲٫۵", text)
        }
    }
}
