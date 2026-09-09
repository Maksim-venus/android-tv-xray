package com.passwall.tv.ui.components

import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.passwall.tv.ui.theme.BorderIdle
import com.passwall.tv.ui.theme.Card
import com.passwall.tv.ui.theme.FocusRing

private val activateKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** Shared debounce so D-pad Center + clickable do not fire twice. */
class ClickGuard {
    @Volatile
    private var last = 0L
    fun run(block: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        if (now - last < 350) return
        last = now
        block()
    }
}

@Composable
fun rememberTvFocus(): Pair<FocusRequester, MutableInteractionSource> {
    val requester = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    return requester to interaction
}

@Composable
fun TvSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    focusedBorder: Color = FocusRing,
    idleBorder: Color = BorderIdle,
    focusedFill: Color = Color.Transparent,
    idleFill: Color = Color.Transparent,
    glow: Boolean = false,
    borderWidth: Dp = 2.dp,
    focusedBorderWidth: Dp = 6.dp,
    requestInitial: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val requester = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val guard = remember { ClickGuard() }
    val focused by interaction.collectIsFocusedAsState()
    val elevation by animateFloatAsState(if (focused) 22f else 4f, label = "elev")
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "scale")
    if (requestInitial) {
        LaunchedEffect(Unit) {
            runCatching { requester.requestFocus() }
        }
    }
    val fire = { guard.run(onClick) }
    Row(
        modifier = modifier
            .focusRequester(requester)
            .onPreviewKeyEvent { event ->
                if (event.key in activateKeys && event.type == KeyEventType.KeyDown) {
                    fire()
                    true
                } else if (event.key in activateKeys && event.type == KeyEventType.KeyUp) {
                    true
                } else {
                    false
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(elevation.dp, shape, ambientColor = focusedBorder, spotColor = focusedBorder)
            .background(if (focused) focusedFill else idleFill, shape)
            .border(
                width = if (focused) focusedBorderWidth else borderWidth,
                color = if (focused) focusedBorder else idleBorder,
                shape = shape,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = fire)
            .padding(horizontal = 28.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content,
    )
}

@Composable
fun Modifier.tvClickable(
    interaction: MutableInteractionSource,
    requester: FocusRequester? = null,
    onClick: () -> Unit,
): Modifier {
    val guard = remember { ClickGuard() }
    val fire = { guard.run(onClick) }
    return this
        .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .onPreviewKeyEvent { event ->
            if (event.key in activateKeys && event.type == KeyEventType.KeyDown) {
                fire()
                true
            } else if (event.key in activateKeys && event.type == KeyEventType.KeyUp) {
                true
            } else {
                false
            }
        }
        .clickable(interactionSource = interaction, indication = null, onClick = fire)
}

@Composable
fun cardFill(): Color = Card
