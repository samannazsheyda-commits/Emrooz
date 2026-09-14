package com.nameemrooz.journal

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.CompositionLocalProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import com.nameemrooz.journal.data.AppLanguage
import com.nameemrooz.journal.data.AppTheme
import com.nameemrooz.journal.data.SettingsStore
import com.nameemrooz.journal.data.UiScale
import com.nameemrooz.journal.privacy.BiometricGate
import com.nameemrooz.journal.ui.NameEmroozApp
import com.nameemrooz.journal.ui.theme.NameEmroozTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var unlocked = false
    private var authInProgress = false
    private var appVisible = false
    private var coverPosted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        beginSplashAndLock()
    }

    override fun onResume() {
        super.onResume()
        if (!authInProgress && !unlocked) beginSplashAndLock()
    }

    override fun onPause() {
        if (appVisible && !isChangingConfigurations) coverImmediately()
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            unlocked = false
            appVisible = false
            authInProgress = false
        }
    }

    private fun beginSplashAndLock() {
        if (authInProgress) return
        authInProgress = true
        appVisible = false
        coverPosted = false
        lifecycleScope.launch {
            val store = SettingsStore(this@MainActivity)
            val theme = store.theme.first()
            val light = theme == AppTheme.DAY
            applySystemBars(light)
            setContent { Splash(light) }
            delay(900)
            if (BuildConfig.DEBUG && intent?.getBooleanExtra("qa_bypass_lock", false) == true) {
                showApp()
                return@launch
            }
            val lockEnabled = store.lockEnabled.first()
            if (lockEnabled) unlockBiometricFirst(store.language.first())
            else showApp()
        }
    }

    private fun coverImmediately() {
        if (coverPosted) return
        coverPosted = true
        unlocked = false
        appVisible = false
        setContent {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
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

    private fun unlockBiometricFirst(language: AppLanguage) {
        BiometricGate(this).unlock(
            language = language,
            onSuccess = { showApp() },
            onUnavailable = { showFingerprintPage(language) },
            onCancelled = { showFingerprintPage(language) },
        )
    }

    private fun showApp() {
        unlocked = true
        appVisible = true
        authInProgress = false
        coverPosted = false
        setContent { NameEmroozApp() }
    }

    private fun showFingerprintPage(language: AppLanguage) {
        authInProgress = false
        val settings = SettingsStore(this)
        setContent {
            AuthTheme {
                val current by settings.language.collectAsState(initial = language)
                val fa = current == AppLanguage.FA
                CompositionLocalProvider(
                    LocalLayoutDirection provides if (fa) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Column(
                            Modifier.fillMaxSize().padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                if (fa) "اثر انگشت برای ورود" else "Fingerprint required",
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                if (fa) "قفل دستگاه یا رمز جایگزین استفاده نمی‌شود. حسگر اثر انگشت را لمس کن."
                                else "Device credentials are not used. Touch the fingerprint sensor to continue.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 12.dp),
                            )
                            TextButton(
                                onClick = { authInProgress = true; unlockBiometricFirst(current) },
                                modifier = Modifier.padding(top = 16.dp),
                            ) { Text(if (fa) "تلاش دوباره" else "Try again") }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Splash(light: Boolean) {
        val settings = SettingsStore(this)
        val language by settings.language.collectAsState(initial = AppLanguage.FA)
        val theme by settings.theme.collectAsState(initial = if (light) AppTheme.DAY else AppTheme.NIGHT)
        val scale by settings.uiScale.collectAsState(initial = UiScale.MEDIUM)
        NameEmroozTheme(theme, language, scale) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(if (light) R.drawable.splash_emrooz_light else R.drawable.splash_emrooz_dark),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }

    @Composable
    private fun AuthTheme(content: @Composable () -> Unit) {
        val settings = SettingsStore(this)
        val theme by settings.theme.collectAsState(initial = AppTheme.DAY)
        val language by settings.language.collectAsState(initial = AppLanguage.FA)
        val scale by settings.uiScale.collectAsState(initial = UiScale.MEDIUM)
        NameEmroozTheme(theme, language, scale, content)
    }
}
