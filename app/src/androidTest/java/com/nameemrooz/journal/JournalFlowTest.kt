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
        com.nameemrooz.journal.data.AppDatabase.get(context).clearAllTables()
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

    @Test fun a_locked_return_hides_the_journal_until_authentication() {
        val secret = "یادداشت خصوصی برای آزمون قفل"
        context.getSharedPreferences("emrooz_draft", Context.MODE_PRIVATE).edit().putString("text", secret).commit()
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            waitForHome()
            compose.onNodeWithText(secret).assertIsDisplayed()
            runBlocking { SettingsStore(context).setLock(true) }
            activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            activity.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("از امروزت بگو").fetchSemanticsNodes().isEmpty()
            }
            compose.onNodeWithText(secret).assertDoesNotExist()
            org.junit.Assert.assertEquals(secret, context.getSharedPreferences("emrooz_draft", Context.MODE_PRIVATE).getString("text", null))
        }
    }

    @Test fun backgrounding_keeps_unsaved_detail_edits_and_navigation() {
        val timestamp = System.currentTimeMillis()
        runBlocking {
            com.nameemrooz.journal.data.AppDatabase.get(context).journalDao().insert(
                com.nameemrooz.journal.model.JournalEntry(title="", text="متن قبلی", createdAt=timestamp)
            )
        }
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            waitForHome()
            compose.onNodeWithText("آرشیو نوشته‌ها").performClick()
            compose.onNodeWithText(com.nameemrooz.journal.util.JalaliDate.time(timestamp)).performClick()
            compose.onNode(hasSetTextAction()).performTextReplacement("ویرایش ذخیره‌نشده")
            activity.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
            activity.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED)
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText("ویرایش ذخیره‌نشده").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNode(hasSetTextAction()).assertTextEquals("ویرایش ذخیره‌نشده")
        }
    }
}
