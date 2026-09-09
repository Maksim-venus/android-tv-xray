package com.passwall.tv.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.passwall.tv.ui.theme.Accent
import com.passwall.tv.ui.theme.BorderIdle
import com.passwall.tv.ui.theme.Card

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
    focusedBorder: Color = Accent,
    idleBorder: Color = BorderIdle,
    focusedFill: Color = Color.Transparent,
    idleFill: Color = Color.Transparent,
    glow: Boolean = false,
    borderWidth: Dp = 2.dp,
    requestInitial: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val requester = remember { FocusRequester() }
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val elevation by animateFloatAsState(if (focused && glow) 28f else 0f, label = "glow")
    if (requestInitial) {
        androidx.compose.runtime.LaunchedEffect(Unit) {
            runCatching { requester.requestFocus() }
        }
    }
    Row(
        modifier = modifier
            .focusRequester(requester)
            .focusable(interactionSource = interaction)
            .shadow(elevation.dp, shape, ambientColor = focusedBorder, spotColor = focusedBorder)
            .background(if (focused) focusedFill else idleFill, shape)
            .border(if (focused) borderWidth + 1.dp else borderWidth, if (focused) focusedBorder else idleBorder, shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
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
    return this
        .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .focusable(interactionSource = interaction)
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}

@Composable
fun cardFill(): Color = Card
