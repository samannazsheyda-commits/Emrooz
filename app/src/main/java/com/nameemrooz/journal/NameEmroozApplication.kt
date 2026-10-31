package com.nameemrooz.journal

import android.app.Application
import androidx.work.Configuration
import com.nameemrooz.journal.data.SettingsStore
import com.nameemrooz.journal.reminder.ReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class NameEmroozApplication : Application(), Configuration.Provider {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(android.util.Log.WARN).build()

    override fun onCreate() {
        super.onCreate()
        // Reminder contains no journal content and is scheduled locally only.
        appScope.launch {
            if (SettingsStore(this@NameEmroozApplication).reminderEnabled.first()) {
                ReminderWorker.schedule(this@NameEmroozApplication)
            }
        }
    }
}
