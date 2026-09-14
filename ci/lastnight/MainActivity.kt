package com.nameemrooz.journal

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.nameemrooz.journal.data.AppLanguage
import com.nameemrooz.journal.data.AppTheme
import com.nameemrooz.journal.data.SettingsStore
import com.nameemrooz.journal.privacy.BiometricGate
import com.nameemrooz.journal.ui.NameEmroozApp
import com.nameemrooz.journal.ui.theme.NameEmroozTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    private var unlocked = false
    private var authInProgress = false
    private var initialSplashFinished = false
    private var currentTheme = AppTheme.DAY

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        showApprovedSplashThenUnlock()
    }

    override fun onResume() {
        super.onResume()
        if (initialSplashFinished && !unlocked && !authInProgress) showFingerprintGate(autoPrompt = true)
    }

    override fun onStop() {
        super.onStop()
        unlocked = false
        authInProgress = false
    }

    private fun showApprovedSplashThenUnlock() {
        authInProgress = true
        lifecycleScope.launch {
            currentTheme = SettingsStore(this@MainActivity).theme.first()
            setSystemBarColors(currentTheme)
            setContent { ApprovedSplash(currentTheme) }
            delay(1100)
            initialSplashFinished = true
            authInProgress = false
            showFingerprintGate(autoPrompt = true)
        }
    }

    private fun showFingerprintGate(autoPrompt: Boolean) {
        unlocked = false
        authInProgress = false
        setSystemBarColors(currentTheme)
        setContent {
            var message by remember { mutableStateOf("برای ورود، اثر انگشتت رو بزن") }
            NameEmroozTheme(currentTheme, AppLanguage.FA) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Spacer(Modifier.height(1.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("حریم خصوصی امروز", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "نامه‌ها فقط روی همین گوشی می‌مونن.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                modifier = Modifier.size(116.dp).clickable { launchBiometric { message = it } },
                                shape = androidx.compose.foundation.shape.CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Fingerprint,
                                        contentDescription = "ورود با اثر انگشت",
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(18.dp))
                            Text(message, fontSize = 18.sp, textAlign = TextAlign.Center)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "اگر حسگر آماده نباشه، قفل خود گوشی استفاده می‌شه.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
                if (autoPrompt) {
                    LaunchedEffect(Unit) {
                        delay(180)
                        launchBiometric { message = it }
                    }
                }
            }
        }
    }

    private fun launchBiometric(onMessage: (String) -> Unit) {
        if (authInProgress || unlocked) return
        authInProgress = true
        BiometricGate(this).unlock(
            onSuccess = { authInProgress = false; showApp() },
            onUnavailable = { authInProgress = false; onMessage("اثر انگشت یا قفل گوشی آماده نیست.") },
            onCancelled = { authInProgress = false; onMessage("برای ورود، آیکون اثر انگشت رو لمس کن.") },
        )
    }

    private fun showApp() {
        unlocked = true
        authInProgress = false
        setSystemBarColors(currentTheme)
        setContent { NameEmroozApp() }
    }

    @Suppress("DEPRECATION")
    private fun setSystemBarColors(theme: AppTheme) {
        val background = if (theme == AppTheme.DAY) 0xFFF7F2EA.toInt() else 0xFF0D1726.toInt()
        window.statusBarColor = background
        window.navigationBarColor = background
        window.decorView.systemUiVisibility = if (theme == AppTheme.DAY) android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
    }
}

@Composable
private fun ApprovedSplash(theme: AppTheme) {
    val splash = if (theme == AppTheme.DAY) R.drawable.splash_emrooz_light else R.drawable.splash_emrooz_dark
    Image(
        painter = painterResource(splash),
        contentDescription = null,
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
    )
}
