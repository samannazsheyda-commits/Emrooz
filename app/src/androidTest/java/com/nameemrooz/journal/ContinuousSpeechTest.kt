package com.nameemrooz.journal

import android.content.*
import android.os.*
import android.speech.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.nameemrooz.journal.speech.SystemSpeechEngine
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/** Actual SpeechRecognizer -> system manager -> separate-process test RecognitionService. No audio/network. */
@RunWith(AndroidJUnit4::class)
class ContinuousSpeechTest {
    @get:Rule val permission = GrantPermissionRule.grant(android.Manifest.permission.RECORD_AUDIO)
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private var previous = ""
    private var engine: SystemSpeechEngine? = null

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    @Before fun setup() {
        Assume.assumeTrue(Build.VERSION.SDK_INT >= 31)
        shell("pm grant com.nameemrooz.journal.preview.test android.permission.RECORD_AUDIO")
        previous = shell("settings get secure voice_recognition_service")
        shell("settings put secure voice_recognition_service com.nameemrooz.journal.preview.test/com.nameemrooz.journal.StreamingRecognitionService")
    }
    @After fun cleanup() {
        engine?.close(); instrumentation.waitForIdleSync()
        if (Build.VERSION.SDK_INT >= 31) {
            if (previous.isBlank() || previous=="null") shell("settings delete secure voice_recognition_service")
            else shell("settings put secure voice_recognition_service $previous")
        }
    }
    private fun command(mode: String, action: String = "configure"): Bundle {
        val latch=CountDownLatch(1); var result=Bundle()
        val intent=Intent().setComponent(ComponentName("com.nameemrooz.journal.preview.test","com.nameemrooz.journal.StreamingScriptReceiver"))
            .putExtra("action",action).putExtra("mode",mode)
        context.sendOrderedBroadcast(intent,null,object:BroadcastReceiver(){override fun onReceive(c:Context?,i:Intent?){result=getResultExtras(true);latch.countDown()}},Handler(Looper.getMainLooper()),0,null,null)
        assertTrue("Test provider did not acknowledge configuration",latch.await(5,TimeUnit.SECONDS));return result
    }
    private fun start(mode:String, text: MutableList<String>, errors:MutableList<String>, listening:AtomicBoolean):SystemSpeechEngine {
        command(mode)
        val ready=CountDownLatch(1)
        return SystemSpeechEngine(context,"fa-IR",{text+=it},{listening.set(it)},{if(it)ready.countDown()},{errors+=it},{errors+=it},onDeviceOnly=false).also {
            engine=it;it.prepare();assertTrue("Actual system recognition service not ready",ready.await(5,TimeUnit.SECONDS));it.start()
        }
    }
    private fun waitFor(timeout:Long=10_000,predicate:()->Boolean) {
        val until=SystemClock.uptimeMillis()+timeout
        while(!predicate() && SystemClock.uptimeMillis()<until)SystemClock.sleep(20)
        assertTrue("Speech pipeline did not meet its condition",predicate())
    }
    @Test fun live_requests_favor_low_latency_formatting() {
        val texts=CopyOnWriteArrayList<String>();val errors=CopyOnWriteArrayList<String>();val listening=AtomicBoolean()
        start("normal",texts,errors,listening)
        waitFor { texts.isNotEmpty() }
        val stats=command("","stats")
        assertEquals(RecognizerIntent.FORMATTING_OPTIMIZE_LATENCY,stats.getString("format"))
    }
    @Test fun interrupted_partial_words_survive_multiple_recoverable_provider_cycles() {
        val texts=CopyOnWriteArrayList<String>();val errors=CopyOnWriteArrayList<String>();val listening=AtomicBoolean()
        start("interrupted",texts,errors,listening)
        waitFor { texts.any { it.contains("نتیجه نهایی") } || errors.isNotEmpty() }
        assertTrue("Recoverable speech cycles should not stop dictation: $errors",errors.isEmpty())
        val last=texts.lastOrNull().orEmpty()
        for(word in listOf("بخش نخست","بخش دوم","بخش سوم","نتیجه نهایی"))assertTrue("Lost interrupted text: $last",last.contains(word))
        assertTrue(listening.get())
    }
    @Test fun a_long_binder_callback_stream_keeps_delivering_text_and_stop_rejects_late_results() {
        val texts=CopyOnWriteArrayList<String>();val errors=CopyOnWriteArrayList<String>();val listening=AtomicBoolean()
        val engine=start("long",texts,errors,listening)
        waitFor(25_000) { texts.lastOrNull()?.contains("پایان بخش ۴۰") == true || errors.isNotEmpty() }
        assertTrue(errors.toString(),errors.isEmpty())
        assertTrue("Expected several pages of live text",texts.last().length>6_000)
        engine.stop(); waitFor { !listening.get() }
        val settled=texts.last();SystemClock.sleep(600)
        assertEquals("Late callbacks after Stop must be ignored",settled,texts.last())
    }
}
