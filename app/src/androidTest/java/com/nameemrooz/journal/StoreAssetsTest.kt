package com.nameemrooz.journal

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nameemrooz.journal.data.*
import com.nameemrooz.journal.model.JournalEntry
import com.nameemrooz.journal.speech.SpeechMode
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real UI captures with synthetic entries. No audio, network, user data or production security changes. */
@RunWith(AndroidJUnit4::class)
class StoreAssetsTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val today = "امروز برای خودم وقت گذاشتم.\n\nکمی پیاده‌روی کردم و به کارهایی فکر کردم که حالم را بهتر می‌کنند.\n\nگاهی همین لحظه‌های ساده، بهترین بخش روز هستند."
    private val entries = listOf(
        "یک شروع آرام\nصبح را با چند دقیقه قدم‌زدن شروع کردم. هوای تازه و نور آفتاب، انرژی خوبی برای ادامهٔ روز به من داد.",
        "قدم‌های کوچک\nامروز یکی از کارهایی را که مدت‌ها عقب انداخته بودم، انجام دادم. از همین پیشرفت کوچک خوشحالم.",
        "چیزهایی که قدردانشان هستم\nیک گفت‌وگوی خوب، یک فنجان چای و فرصتی برای استراحت. می‌خواهم بیشتر به این لحظه‌ها توجه کنم.",
    )

    private fun home() {
        compose.waitUntil(30_000) { compose.onAllNodesWithText("از امروزت بگو").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    private fun capture(theme: String, name: String) {
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        // Navigation semantics can settle before RenderThread submits the new display frame.
        android.os.SystemClock.sleep(300)
        val expected = if (theme == "light") android.graphics.Color.rgb(247, 242, 234) else android.graphics.Color.rgb(13, 23, 38)
        var captured: Bitmap? = null
        // FLAG_SECURE updates reach SurfaceFlinger on the next real display frame,
        // independently of Compose's test clock. Wait for an actual unprotected app frame.
        repeat(40) {
            if (captured == null) {
                val candidate = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                if (candidate != null && candidate.getPixel(20, 200) == expected) captured = candidate
                else { candidate?.recycle(); android.os.SystemClock.sleep(100) }
            }
        }
        val bitmap = checkNotNull(captured) { "Emulator did not expose the requested non-secure sample-data frame" }
        val file = File(context.getExternalFilesDir("store-shots"), "$theme/$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        bitmap.recycle()
        // connectedAndroidTest uninstalls its target and removes app-specific files.
        // Preserve only these requested synthetic screenshots in the emulator's public folder.
        val destination = "/sdcard/Download/Emrooz-Store-Screenshots/$theme"
        fun shell(command: String) {
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            ).use { it.readBytes() }
        }
        shell("mkdir -p $destination")
        shell("cp ${file.absolutePath} $destination/$name.png")
    }

    @Test fun capture_real_light_and_dark_journal_screens() {
        for ((folder, theme) in listOf("light" to AppTheme.DAY, "dark" to AppTheme.NIGHT)) {
            runBlocking {
                SettingsStore(context).apply {
                    setLock(false); setReminder(false); setSound(false); setHaptics(false)
                    setTheme(theme); setLanguage(AppLanguage.FA); setUiScale(UiScale.MEDIUM)
                    setHighAccuracyDisclosureAccepted(true); setSpeechMode(SpeechMode.SYSTEM_HIGH_ACCURACY)
                }
                val db = AppDatabase.get(context)
                db.clearAllTables()
                entries.forEachIndexed { index, text ->
                    db.journalDao().insert(JournalEntry(title="", text=text, createdAt=1_793_534_400_000L-index*86_400_000))
                }
                db.journalDao().insert(JournalEntry(title="", text="یک روز به‌یادماندنی\nامروز فرصتی پیدا کردم که دوباره سراغ کار موردعلاقه‌ام بروم. این لحظه را برای خودم نگه می‌دارم.", createdAt=1_793_188_800_000L, archived=true))
            }
            context.getSharedPreferences("emrooz_draft", Context.MODE_PRIVATE).edit().putString("text", today).commit()
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                home()
                // Only this emulator test window permits the user-requested store screenshots.
                activity.onActivity {
                    it.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    it.window.decorView.invalidate()
                }
                capture(folder, "01-home")
                compose.onNodeWithText("ویرایش").performClick()
                compose.onNode(hasSetTextAction()).performClick()
                compose.waitUntil(10_000) {
                    var visible = false
                    activity.onActivity { visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
                    visible
                }
                capture(folder, "qa-home-keyboard")
                compose.onNodeWithText("پایان ویرایش").performClick()
                compose.waitUntil(10_000) {
                    var visible = false
                    activity.onActivity { visible = ViewCompat.getRootWindowInsets(it.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == true }
                    !visible
                }
                compose.onNodeWithText("آرشیو نوشته‌ها").performClick()
                compose.onNodeWithText(entries[0].substringBefore('\n'), substring=true).assertIsDisplayed()
                capture(folder, "02-notes")
                compose.onNodeWithText(entries[0].substringBefore('\n'), substring=true).performClick()
                compose.onNode(hasSetTextAction()).assertTextEquals(entries[0])
                capture(folder, "03-note")
                compose.onNodeWithContentDescription("برگشت").performClick()
                compose.onNodeWithText("آرشیوشده‌ها").performClick()
                compose.onNodeWithText("یک روز به‌یادماندنی", substring=true).assertIsDisplayed()
                capture(folder, "04-archive")
                compose.onNodeWithContentDescription("برگشت").performClick()
                // The archive screen returns to active notes; the next Back reaches Home.
                if (compose.onAllNodesWithText("از امروزت بگو").fetchSemanticsNodes().isEmpty()) {
                    compose.onNodeWithContentDescription("برگشت").performClick()
                }
                home()
                compose.onNodeWithContentDescription("منو").performClick()
                compose.onNodeWithText("تنظیمات").assertIsDisplayed()
                capture(folder, "05-settings")
            }
        }
    }
}

/** Makes the idle device-service choice available on AOSP. It cannot recognize or save audio. */
class StoreAvailabilityRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, callback: Callback?) { callback?.error(SpeechRecognizer.ERROR_CLIENT) }
    override fun onStopListening(callback: Callback?) = Unit
    override fun onCancel(callback: Callback?) = Unit
}
