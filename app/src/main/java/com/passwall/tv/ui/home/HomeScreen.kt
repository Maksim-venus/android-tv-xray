package com.passwall.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.focusable
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.passwall.tv.ui.HomeUiState
import com.passwall.tv.ui.components.GearIcon
import com.passwall.tv.ui.components.PlayIcon
import com.passwall.tv.ui.components.StopIcon
import com.passwall.tv.ui.components.TvSurface
import com.passwall.tv.ui.theme.Accent
import com.passwall.tv.ui.theme.BorderIdle
import com.passwall.tv.ui.theme.OnlineGreen
import com.passwall.tv.ui.theme.TextMuted

@Composable
fun HomeScreen(
    state: HomeUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onSettings: () -> Unit,
    onTest: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(48.dp),
    ) {
        if (state.running) {
            RunningSettings(onSettings, Modifier.align(Alignment.TopEnd))
            StopButton(onStop, Modifier.align(Alignment.Center))
            TestCluster(
                ok = state.statusOk,
                text = state.statusText.ifBlank { "代理正常" },
                onTest = onTest,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        } else {
            StoppedSettings(onSettings, Modifier.align(Alignment.TopEnd))
            StartButton(onStart, Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun StartButton(onClick: () -> Unit, modifier: Modifier) {
    TvSurface(
        onClick = onClick,
        modifier = modifier.width(360.dp).height(108.dp),
        shape = RoundedCornerShape(22.dp),
        focusedBorder = Accent,
        idleBorder = Accent.copy(alpha = 0.85f),
        focusedFill = Color(0x223B82F6),
        idleFill = Color(0x140B1220),
        glow = true,
        borderWidth = 3.dp,
        requestInitial = true,
    ) {
        PlayIcon(40.dp)
        Spacer(Modifier.width(18.dp))
        Text("启动", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StopButton(onClick: () -> Unit, modifier: Modifier) {
    TvSurface(
        onClick = onClick,
        modifier = modifier.width(340.dp).height(100.dp),
        shape = RoundedCornerShape(18.dp),
        focusedBorder = Color.White,
        idleBorder = Color.White.copy(alpha = 0.85f),
        focusedFill = Color(0x22FFFFFF),
        idleFill = Color(0x14FFFFFF),
        glow = false,
        borderWidth = 2.dp,
        requestInitial = true,
    ) {
        StopIcon(28.dp)
        Spacer(Modifier.width(16.dp))
        Text("停止", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StoppedSettings(onClick: () -> Unit, modifier: Modifier) {
    TvSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        focusedBorder = Color.White,
        idleBorder = Color.White.copy(alpha = 0.55f),
        borderWidth = 1.5.dp,
    ) {
        GearIcon(20.dp)
        Spacer(Modifier.width(10.dp))
        Text("设置", color = Color.White, fontSize = 18.sp)
    }
}

@Composable
private fun RunningSettings(onClick: () -> Unit, modifier: Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(56.dp)
                .shadow(if (focused) 12.dp else 0.dp, CircleShape)
                .background(Color.Transparent, CircleShape)
                .border(1.5.dp, if (focused) Accent else Color.White.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GearIcon(24.dp)
        }
        Spacer(Modifier.height(6.dp))
        Text("设置", color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun TestCluster(
    ok: Boolean,
    text: String,
    onTest: () -> Unit,
    modifier: Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TvSurface(
            onClick = onTest,
            shape = RoundedCornerShape(10.dp),
            focusedBorder = Color.White,
            idleBorder = Color.White.copy(alpha = 0.7f),
            borderWidth = 1.5.dp,
        ) {
            Text("测试", color = Color.White, fontSize = 16.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(if (ok) OnlineGreen else Color(0xFFEF4444), CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(if (ok) "代理正常" else text.ifBlank { "代理异常" }, color = Color.White, fontSize = 15.sp)
        }
    }
}
