package com.nameemrooz.journal

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.nameemrooz.journal.data.AppLanguage
import com.nameemrooz.journal.data.AppTheme
import com.nameemrooz.journal.data.SettingsStore
import com.nameemrooz.journal.data.UiScale
import com.nameemrooz.journal.privacy.BiometricGate
import com.nameemrooz.journal.privacy.PasscodeStore
import com.nameemrooz.journal.ui.NameEmroozApp
import com.nameemrooz.journal.ui.theme.NameEmroozTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var unlocked = false
    private var authInProgress = false
    private var startupSplashVisible = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Journal text must never appear in screenshots or Recent Apps thumbnails.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        showStartupSplash()
    }

    override fun onResume() {
        super.onResume()
        if (!startupSplashVisible && !unlocked && !authInProgress) checkLockAndShow()
    }

    override fun onStop() {
        super.onStop()
        unlocked = false
        authInProgress = false
    }

    private fun showStartupSplash() {
        startupSplashVisible = true
        authInProgress = true
        lifecycleScope.launch {
            val theme = SettingsStore(this@MainActivity).theme.first()
            val light = theme == AppTheme.DAY
            applySystemBars(light)
            setContent {
                Image(
                    painter = painterResource(if (light) R.drawable.splash_emrooz_light else R.drawable.splash_emrooz_dark),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )
            }
            delay(1250)
            startupSplashVisible = false
            authInProgress = false
            checkLockAndShow()
        }
    }

    private fun applySystemBars(light: Boolean) {
        val color = Color.parseColor(if (light) "#F7F2EA" else "#0D1726")
        window.statusBarColor = color
        window.navigationBarColor = color
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    private fun checkLockAndShow() {
        authInProgress = true
        lifecycleScope.launch {
            val settings = SettingsStore(this@MainActivity)
            val enabled = settings.lockEnabled.first()
            if (!enabled) {
                showApp()
                return@launch
            }
            unlockBiometricFirst(PasscodeStore(this@MainActivity))
        }
    }

    private fun unlockBiometricFirst(store: PasscodeStore) {
        authInProgress = true
        lifecycleScope.launch {
            val language = SettingsStore(this@MainActivity).language.first()
            BiometricGate(this@MainActivity).unlock(
                language = language,
                onSuccess = { showApp() },
                onUnavailable = {
                    authInProgress = false
                    if (store.hasPasscode()) showUnlock(store) else showPasscodeSetup(store)
                },
                onCancelled = {
                    authInProgress = false
                    if (store.hasPasscode()) showUnlock(store) else showPasscodeSetup(store)
                },
            )
        }
    }

    @Composable
    private fun AuthTheme(content: @Composable (AppLanguage) -> Unit) {
        val settings = remember { SettingsStore(this@MainActivity) }
        val theme by settings.theme.collectAsState(initial = AppTheme.DAY)
        val language by settings.language.collectAsState(initial = AppLanguage.FA)
        val uiScale by settings.uiScale.collectAsState(initial = UiScale.MEDIUM)
        NameEmroozTheme(theme, language, uiScale) {
            val direction = if (language == AppLanguage.FA) LayoutDirection.Rtl else LayoutDirection.Ltr
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    content(language)
                }
            }
        }
    }

    private fun showPasscodeSetup(store: PasscodeStore) {
        unlocked = false
        authInProgress = false
        setContent {
            AuthTheme { language ->
                var passcode by remember { mutableStateOf("") }
                var confirmation by remember { mutableStateOf("") }
                var error by remember { mutableStateOf("") }
                val fa = language == AppLanguage.FA
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = 380.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (fa) "قفل خصوصی امروز" else "Private lock", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                        Text(
                            if (fa) "برای محافظت از نوشته‌ها، یک رمز جایگزین حداقل چهارحرفی یا چهاررقمی بساز."
                            else "Create a four-character or longer fallback passcode for your notes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                        OutlinedTextField(
                            passcode,
                            { passcode = it },
                            label = { Text(if (fa) "رمز" else "Passcode") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.padding(top = 20.dp),
                        )
                        OutlinedTextField(
                            confirmation,
                            { confirmation = it },
                            label = { Text(if (fa) "تکرار رمز" else "Repeat passcode") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.padding(top = 10.dp),
                        )
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = {
                                when {
                                    passcode.trim().length < 4 -> error = if (fa) "رمز باید حداقل چهار نویسه باشد." else "Use at least four characters."
                                    passcode != confirmation -> error = if (fa) "دو رمز یکسان نیستند." else "The passcodes do not match."
                                    else -> { store.set(passcode); passcode = ""; confirmation = ""; showApp() }
                                }
                            },
                            modifier = Modifier.padding(top = 20.dp),
                        ) { Text(if (fa) "ساخت رمز و ورود" else "Create passcode") }
                    }
                }
            }
        }
    }

    private fun showUnlock(store: PasscodeStore) {
        unlocked = false
        authInProgress = false
        setContent {
            AuthTheme { language ->
                var passcode by remember { mutableStateOf("") }
                var error by remember { mutableStateOf("") }
                val fa = language == AppLanguage.FA
                Column(
                    Modifier.fillMaxSize().padding(28.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.widthIn(max = 380.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (fa) "امروز" else "Emrooz", style = MaterialTheme.typography.headlineMedium)
                        Text(if (fa) "ورود با رمز جایگزین" else "Use fallback passcode", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        OutlinedTextField(
                            passcode,
                            { passcode = it; error = "" },
                            label = { Text(if (fa) "رمز" else "Passcode") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.padding(top = 18.dp),
                        )
                        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall)
                        Button(
                            onClick = { if (store.verify(passcode)) { passcode = ""; showApp() } else error = if (fa) "رمز درست نیست." else "Incorrect passcode." },
                            modifier = Modifier.padding(top = 16.dp),
                        ) { Text(if (fa) "باز کردن" else "Unlock") }
                        OutlinedButton(onClick = { unlockBiometricFirst(store) }, modifier = Modifier.padding(top = 10.dp)) {
                            Text(if (fa) "تلاش دوباره با اثر انگشت" else "Try fingerprint again")
                        }
                    }
                }
            }
        }
    }

    private fun showApp() {
        unlocked = true
        authInProgress = false
        setContent { NameEmroozApp() }
    }
}
