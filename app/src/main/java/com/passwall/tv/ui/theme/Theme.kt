package com.passwall.tv.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

val Navy = Color(0xFF070B14)
val Navy2 = Color(0xFF0C1424)
val Card = Color(0xFF141B2B)
val Card2 = Color(0xFF1A2234)
val Accent = Color(0xFF2F7BFF)
val AccentSoft = Color(0xFF3B82F6)
val VlessPurple = Color(0xFF7C5CFF)
val VmessBlue = Color(0xFF2F6BFF)
val OnlineGreen = Color(0xFF22C55E)
val TextPrimary = Color(0xFFFFFFFF)
val TextMuted = Color(0xFF8B93A7)
val BorderIdle = Color(0x33FFFFFF)
val FocusGlow = Color(0xFF3B82F6)

private val scheme = darkColorScheme(
    primary = Accent,
    background = Navy,
    surface = Card,
    onPrimary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
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
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(
                            listOf(Navy, Navy2, Color(0xFF080D18)),
                            startY = 0f,
                            endY = size.height,
                        ),
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0x332F7BFF), Color.Transparent),
                            center = Offset(size.width * 0.5f, size.height * 0.55f),
                            radius = size.minDimension * 0.55f,
                        ),
                    )
                },
        ) {
            content()
        }
    }
}
