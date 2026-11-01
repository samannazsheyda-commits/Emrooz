package com.nameemrooz.journal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nameemrooz.journal.R
import com.nameemrooz.journal.data.AppLanguage
import com.nameemrooz.journal.data.AppTheme
import com.nameemrooz.journal.data.UiScale
import androidx.core.view.WindowCompat

val CreamBackground = Color(0xFFF7F2EA)
val NavyBackground = Color(0xFF0D1726)
val CopperAccent = Color(0xFFD68149)

private val abarFamily = FontFamily(
    Font(R.font.abar_mid_fa_num_regular, FontWeight.Normal),
    Font(R.font.abar_mid_fa_num_semibold, FontWeight.Medium),
    Font(R.font.abar_mid_fa_num_semibold, FontWeight.SemiBold),
    Font(R.font.abar_mid_fa_num_semibold, FontWeight.Bold),
)
private val latoFamily = FontFamily(
    Font(R.font.lato_regular, FontWeight.Normal),
    Font(R.font.lato_bold, FontWeight.Medium),
    Font(R.font.lato_bold, FontWeight.SemiBold),
    Font(R.font.lato_bold, FontWeight.Bold),
)

private val nightScheme = darkColorScheme(
    primary = CopperAccent, onPrimary = Color.White,
    background = NavyBackground, onBackground = Color(0xFFF7F2EA),
    surface = Color(0xFF1B2C46), onSurface = Color(0xFFF7F2EA),
    surfaceVariant = Color(0xFF223652), onSurfaceVariant = Color(0xFFA8B9CA),
    outline = Color(0xFF385271),
)
private val dayScheme = lightColorScheme(
    primary = CopperAccent, onPrimary = Color.White,
    background = CreamBackground, onBackground = Color(0xFF153236),
    surface = Color(0xFFFFFCF7), onSurface = Color(0xFF153236),
    surfaceVariant = Color(0xFFF1E7DA), onSurfaceVariant = Color(0xFF747B78),
    outline = Color(0xFFDFC5A9),
)

private fun appTypography(language: AppLanguage, uiScale: UiScale): Typography {
    val family = if (language == AppLanguage.FA) abarFamily else latoFamily
    val factor = when (uiScale) {
        UiScale.SMALL -> 0.92f
        UiScale.MEDIUM -> 1.00f
        UiScale.LARGE -> 1.10f
    }
    fun style(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (size * factor).sp,
        lineHeight = (line * factor).sp,
    )
    // Deliberately restrained: the approved UI is formal/minimal, not oversized.
    return Typography(
        headlineLarge = style(26, 36, FontWeight.SemiBold),
        headlineMedium = style(22, 31, FontWeight.SemiBold),
        titleLarge = style(18, 27, FontWeight.SemiBold),
        titleMedium = style(16, 24, FontWeight.SemiBold),
        bodyLarge = style(16, 29),
        bodyMedium = style(14, 23),
        bodySmall = style(11, 18),
        labelLarge = style(14, 20, FontWeight.SemiBold),
        labelMedium = style(11, 17, FontWeight.SemiBold),
    )
}

@Composable
fun NameEmroozTheme(
    theme: AppTheme,
    language: AppLanguage = AppLanguage.FA,
    uiScale: UiScale = UiScale.MEDIUM,
    content: @Composable () -> Unit,
) {
    val scheme = if (theme == AppTheme.DAY) dayScheme else nightScheme
    val view = LocalView.current
    SideEffect {
        if (!view.isInEditMode) {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = scheme.background.toArgb()
                window.navigationBarColor = scheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = theme == AppTheme.DAY
                    isAppearanceLightNavigationBars = theme == AppTheme.DAY
                }
            }
        }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = appTypography(language, uiScale),
        content = content,
    )
}
