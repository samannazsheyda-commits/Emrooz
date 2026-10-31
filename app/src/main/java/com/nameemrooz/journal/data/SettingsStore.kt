package com.nameemrooz.journal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nameemrooz.journal.speech.SpeechMode
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore("settings")

enum class AppTheme { NIGHT, DAY }
enum class AppLanguage { FA, EN }
enum class UiScale { SMALL, MEDIUM, LARGE }

class SettingsStore internal constructor(private val preferences: DataStore<Preferences>) {
    constructor(context: Context) : this(context.dataStore)

    private val themeKey = stringPreferencesKey("theme_v2")
    private val legacyThemeKey = stringPreferencesKey("theme")
    private val languageKey = stringPreferencesKey("language")
    private val lockKey = booleanPreferencesKey("lock")
    private val reminderKey = booleanPreferencesKey("reminder")
    private val soundKey = booleanPreferencesKey("sound_enabled")
    private val hapticsKey = booleanPreferencesKey("haptics_enabled")
    private val clearClipboardKey = booleanPreferencesKey("clear_clipboard_enabled")
    private val uiScaleKey = stringPreferencesKey("ui_scale")
    private val speechModeKey = stringPreferencesKey("speech_mode")
    private val highAccuracyDisclosureKey = booleanPreferencesKey("high_accuracy_disclosure_accepted")
    private val enhancedOnDeviceEditingKey = booleanPreferencesKey("enhanced_on_device_editing")

    val theme = preferences.data.map { preferences ->
        preferences[themeKey]?.let { stored -> runCatching { AppTheme.valueOf(stored) }.getOrNull() }
            ?: preferences[legacyThemeKey]?.let { legacy -> if (legacy == "LIGHT") AppTheme.DAY else AppTheme.NIGHT }
            ?: AppTheme.DAY
    }
    val language = preferences.data.map { preferences ->
        runCatching { AppLanguage.valueOf(preferences[languageKey] ?: AppLanguage.FA.name) }
            .getOrDefault(AppLanguage.FA)
    }
    val lockEnabled = preferences.data.map { it[lockKey] ?: true }
    val reminderEnabled = preferences.data.map { it[reminderKey] ?: true }
    val soundEnabled = preferences.data.map { it[soundKey] ?: true }
    val hapticsEnabled = preferences.data.map { it[hapticsKey] ?: true }
    val clearClipboardEnabled = preferences.data.map { it[clearClipboardKey] ?: true }
    val uiScale = preferences.data.map { preferences ->
        runCatching { UiScale.valueOf(preferences[uiScaleKey] ?: UiScale.MEDIUM.name) }
            .getOrDefault(UiScale.MEDIUM)
    }
    val speechMode = preferences.data.map { preferences ->
        SpeechMode.fromStored(preferences[speechModeKey])
    }
    val highAccuracyDisclosureAccepted = preferences.data.map { it[highAccuracyDisclosureKey] ?: false }
    val enhancedOnDeviceEditingEnabled = preferences.data.map { it[enhancedOnDeviceEditingKey] ?: true }

    suspend fun setTheme(value: AppTheme) = preferences.edit { it[themeKey] = value.name }
    suspend fun setLanguage(value: AppLanguage) = preferences.edit { it[languageKey] = value.name }
    suspend fun setLock(value: Boolean) = preferences.edit { it[lockKey] = value }
    suspend fun setReminder(value: Boolean) = preferences.edit { it[reminderKey] = value }
    suspend fun setSound(value: Boolean) = preferences.edit { it[soundKey] = value }
    suspend fun setHaptics(value: Boolean) = preferences.edit { it[hapticsKey] = value }
    suspend fun setClearClipboard(value: Boolean) = preferences.edit { it[clearClipboardKey] = value }
    suspend fun setUiScale(value: UiScale) = preferences.edit { it[uiScaleKey] = value.name }
    suspend fun setSpeechMode(value: SpeechMode) = preferences.edit { it[speechModeKey] = value.name }
    suspend fun setHighAccuracyDisclosureAccepted(value: Boolean) = preferences.edit { it[highAccuracyDisclosureKey] = value }
    suspend fun setEnhancedOnDeviceEditingEnabled(value: Boolean) = preferences.edit { it[enhancedOnDeviceEditingKey] = value }
}
