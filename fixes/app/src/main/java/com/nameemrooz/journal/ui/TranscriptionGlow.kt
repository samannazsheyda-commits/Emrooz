package com.nameemrooz.journal.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * A calm moving light that makes the live voice-to-text state visible without obscuring text.
 */
fun Modifier.transcriptionGlow(active: Boolean): Modifier = composed {
    if (!active) return@composed this

    val transition = rememberInfiniteTransition(label = "voice-to-text")
    val scan by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Restart
        ),
        label = "conversion-scan"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.025f,
        targetValue = 0.07f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "conversion-pulse"
    )
    val accent = MaterialTheme.colorScheme.primary

    drawWithContent {
        drawContent()
        drawRect(accent.copy(alpha = pulse))

        val bandWidth = size.width * 0.62f
        val start = -bandWidth + (size.width + bandWidth) * scan
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    accent.copy(alpha = 0.04f),
                    accent.copy(alpha = 0.20f),
                    accent.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                startX = start,
                endX = start + bandWidth
            )
        )
    }
}
