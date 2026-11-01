package com.nameemrooz.journal

import android.content.Context
import android.graphics.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nameemrooz.journal.data.*
import com.nameemrooz.journal.model.JournalEntry
import com.nameemrooz.journal.ui.AppViewModel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regression tests exercise the real Compose viewport, actual IME, and Room navigation. */
@RunWith(AndroidJUnit4::class)
class JournalViewportTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val longText = (1..45).joinToString("\n") { "خط $it: امروز برای نوشتن و آرامش وقت گذاشتم." }

    @Before fun prepare() = runBlocking {
        SettingsStore(context).apply {
            setLock(false); setReminder(false); setTheme(AppTheme.DAY)
            setLanguage(AppLanguage.FA); setUiScale(UiScale.MEDIUM)
        }
        AppDatabase.get(context).clearAllTables()
        context.getSharedPreferences("emrooz_draft", Context.MODE_PRIVATE).edit().clear().commit()
        Unit
    }

    private fun home() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("از امروزت بگو").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun speech_updates_keep_the_last_line_in_the_visible_transcript_viewport() {
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            home()
            var token = 0L
            activity.onActivity { token = ViewModelProvider(it)[AppViewModel::class.java].draft.beginRecording() }
            for (length in listOf(20, 45)) {
                val next = longText.lines().take(length).joinToString("\n")
                activity.onActivity { ViewModelProvider(it)[AppViewModel::class.java].draft.acceptSpeech(token, next) }
                compose.waitUntil(5_000) { compose.onAllNodesWithText(next).fetchSemanticsNodes().isNotEmpty() }
                compose.waitForIdle()
                val range = compose.onNodeWithText(next).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                assertTrue("The fixture must overflow the transcript viewport", range.maxValue() > 0f)
                compose.waitUntil(5_000) { kotlin.math.abs(range.value() - range.maxValue()) < 2f }
            }
        }
    }

    private fun assertUsableEditorAboveKeyboard(activity: ActivityScenario<MainActivity>, saveLabel: String) {
        compose.onNode(hasSetTextAction()).performClick()
        compose.waitUntil(10_000) {
            var visible = false
            activity.onActivity { visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
            visible
        }
        compose.waitForIdle()
        val editor = compose.onNode(hasSetTextAction()).fetchSemanticsNode().boundsInWindow
        val save = compose.onNodeWithText(saveLabel).fetchSemanticsNode().boundsInWindow
        val visible = Rect()
        activity.onActivity { it.window.decorView.getWindowVisibleDisplayFrame(visible) }
        assertTrue("Editing must retain at least 140dp of visible text, got ${editor.height}", editor.height >= 140 * context.resources.displayMetrics.density)
        assertTrue("Editor is covered by keyboard: $editor / $visible", editor.bottom <= visible.bottom + 2)
        assertTrue("Save must stay above the keyboard: $save / $visible", save.bottom <= visible.bottom + 2)
        compose.onNodeWithText(saveLabel).assertIsDisplayed()
    }

    @Test fun home_editor_remains_usable_with_real_keyboard_and_long_text() {
        context.getSharedPreferences("emrooz_draft", Context.MODE_PRIVATE).edit().putString("text", longText).commit()
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            home()
            compose.onNodeWithText("ویرایش").performClick()
            assertUsableEditorAboveKeyboard(activity, "ذخیره")
            compose.onNode(hasSetTextAction()).performTextInput(" پایان ویرایش")
            compose.onNodeWithText("ذخیره").performClick()
            compose.waitUntil(10_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
            assertTrue(runBlocking { AppDatabase.get(context).journalDao().active().first().first().text.contains("پایان ویرایش") })
        }
    }

    @Test fun archive_is_reachable_without_scrolling_past_saved_notes_and_rows_show_text_preview() {
        val timestamp = 1_793_534_400_000L
        runBlocking {
            val dao = AppDatabase.get(context).journalDao()
            repeat(25) { index -> dao.insert(JournalEntry(title="", text="نوشته شماره $index\nادامهٔ یادداشت", createdAt=timestamp-index*86_400_000)) }
            dao.insert(JournalEntry(title="", text="یادداشت آرشیوشده", createdAt=timestamp, archived=true))
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            home()
            compose.onNodeWithText("آرشیو نوشته‌ها").assertIsDisplayed().performClick()
            compose.waitUntil(5_000) { compose.onAllNodesWithText("نوشته‌های قبلی").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("نوشته شماره 0", substring=true).assertIsDisplayed()
            compose.onNodeWithText("آرشیوشده‌ها").assertIsDisplayed().performClick()
            compose.onNodeWithText("یادداشت آرشیوشده", substring=true).assertIsDisplayed().performClick()
            compose.onNode(hasSetTextAction()).assertTextEquals("یادداشت آرشیوشده")
            compose.onNodeWithText("برگرداندن").performClick()
            compose.onNodeWithText("تغییرات ذخیره شد").assertIsDisplayed()
        }
    }

    @Test fun saved_note_editor_keeps_save_above_keyboard() {
        runBlocking { AppDatabase.get(context).journalDao().insert(JournalEntry(title="", text=longText, createdAt=System.currentTimeMillis())) }
        ActivityScenario.launch(MainActivity::class.java).use { activity ->
            home()
            compose.onNodeWithText("آرشیو نوشته‌ها").performClick()
            compose.onNodeWithText(com.nameemrooz.journal.util.JalaliDate.time(System.currentTimeMillis())).performClick()
            assertUsableEditorAboveKeyboard(activity, "ذخیره تغییرات")
        }
    }
}

