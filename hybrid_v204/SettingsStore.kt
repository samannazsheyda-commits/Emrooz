package com.nameemrooz.journal.data

import android.content.Context
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

class SettingsStore(private val context: Context) {
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

    val theme = context.dataStore.data.map { preferences ->
        preferences[themeKey]?.let { stored -> runCatching { AppTheme.valueOf(stored) }.getOrNull() }
            ?: preferences[legacyThemeKey]?.let { legacy -> if (legacy == "LIGHT") AppTheme.DAY else AppTheme.NIGHT }
            ?: AppTheme.DAY
    }
    val language = context.dataStore.data.map { preferences ->
        runCatching { AppLanguage.valueOf(preferences[languageKey] ?: AppLanguage.FA.name) }
            .getOrDefault(AppLanguage.FA)
    }
    val lockEnabled = context.dataStore.data.map { it[lockKey] ?: true }
    val reminderEnabled = context.dataStore.data.map { it[reminderKey] ?: true }
    val soundEnabled = context.dataStore.data.map { it[soundKey] ?: true }
    val hapticsEnabled = context.dataStore.data.map { it[hapticsKey] ?: true }
    val clearClipboardEnabled = context.dataStore.data.map { it[clearClipboardKey] ?: true }
    val uiScale = context.dataStore.data.map { preferences ->
        runCatching { UiScale.valueOf(preferences[uiScaleKey] ?: UiScale.MEDIUM.name) }
            .getOrDefault(UiScale.MEDIUM)
    }
    val speechMode = context.dataStore.data.map { preferences ->
        SpeechMode.fromStored(preferences[speechModeKey])
    }
    val highAccuracyDisclosureAccepted = context.dataStore.data.map { it[highAccuracyDisclosureKey] ?: false }
    val enhancedOnDeviceEditingEnabled = context.dataStore.data.map { it[enhancedOnDeviceEditingKey] ?: false }

    suspend fun setTheme(value: AppTheme) = context.dataStore.edit { it[themeKey] = value.name }
    suspend fun setLanguage(value: AppLanguage) = context.dataStore.edit { it[languageKey] = value.name }
    suspend fun setLock(value: Boolean) = context.dataStore.edit { it[lockKey] = value }
    suspend fun setReminder(value: Boolean) = context.dataStore.edit { it[reminderKey] = value }
    suspend fun setSound(value: Boolean) = context.dataStore.edit { it[soundKey] = value }
    suspend fun setHaptics(value: Boolean) = context.dataStore.edit { it[hapticsKey] = value }
    suspend fun setClearClipboard(value: Boolean) = context.dataStore.edit { it[clearClipboardKey] = value }
    suspend fun setUiScale(value: UiScale) = context.dataStore.edit { it[uiScaleKey] = value.name }
    suspend fun setSpeechMode(value: SpeechMode) = context.dataStore.edit { it[speechModeKey] = value.name }
    suspend fun setHighAccuracyDisclosureAccepted(value: Boolean) = context.dataStore.edit { it[highAccuracyDisclosureKey] = value }
    suspend fun setEnhancedOnDeviceEditingEnabled(value: Boolean) = context.dataStore.edit { it[enhancedOnDeviceEditingKey] = value }
}
