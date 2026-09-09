package com.passwall.tv.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

/** Clash for Windows / Clash Verge light palette. */
val Canvas = Color(0xFFF5F5F7)
val Canvas2 = Color(0xFFEEEEF1)
val Card = Color(0xFFFFFFFF)
val Card2 = Color(0xFFFAFAFC)
val Accent = Color(0xFF3478F6)
val AccentSoft = Color(0xFF2B7CD3)
val FocusRing = Color(0xFF3478F6)
val VlessPurple = Color(0xFF5B6CFF)
val VmessBlue = Color(0xFF2B7CD3)
val OnlineGreen = Color(0xFF17A65A)
val Danger = Color(0xFFE5484D)
val Ink = Color(0xFF1F2328)
val TextPrimary = Ink
val TextMuted = Color(0xFF6B7280)
val BorderIdle = Color(0xFFD8DCE3)
val FocusGlow = FocusRing

private val scheme = lightColorScheme(
    primary = Accent,
    background = Canvas,
    surface = Card,
    onPrimary = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    error = Danger,
    onError = Color.White,
)

@Composable
fun PasswallTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography.copy(
            displayLarge = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp),
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind { drawRect(Canvas) },
        ) {
            content()
        }
    }
}
