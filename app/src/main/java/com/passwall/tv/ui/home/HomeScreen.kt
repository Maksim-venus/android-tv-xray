package com.passwall.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.passwall.tv.ui.HomeUiState
import com.passwall.tv.ui.components.GearIcon
import com.passwall.tv.ui.components.PlayIcon
import com.passwall.tv.ui.components.StopIcon
import com.passwall.tv.ui.components.TvSurface
import com.passwall.tv.ui.theme.Accent
import com.passwall.tv.ui.theme.AccentSoft
import com.passwall.tv.ui.theme.BorderIdle
import com.passwall.tv.ui.theme.Card
import com.passwall.tv.ui.theme.Danger
import com.passwall.tv.ui.theme.FocusRing
import com.passwall.tv.ui.theme.Ink
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
            .padding(40.dp),
    ) {
        OutlinedAction(
            onClick = onSettings,
            modifier = Modifier.align(Alignment.TopEnd),
            label = "设置",
            leading = { GearIcon(20.dp, Ink) },
        )
        if (state.running || state.stopping) {
            StopButton(onStop, Modifier.align(Alignment.Center), stopping = state.stopping)
            TestCluster(
                ok = state.statusOk,
                text = state.statusText,
                onTest = onTest,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        } else {
            Column(
                Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StartButton(onStart)
            }
        }
        StatusBanner(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (state.running) 8.dp else 0.dp),
        )
    }
}

@Composable
private fun StartButton(onClick: () -> Unit) {
    TvSurface(
        onClick = onClick,
        modifier = Modifier.width(380.dp).height(112.dp),
        shape = RoundedCornerShape(20.dp),
        focusedBorder = Color(0xFF0B4FA2),
        idleBorder = AccentSoft,
        focusedFill = Color(0xFF3D8BFF),
        idleFill = Accent,
        glow = true,
        borderWidth = 2.dp,
        focusedBorderWidth = 6.dp,
        requestInitial = true,
    ) {
        PlayIcon(40.dp, Color.White)
        Spacer(Modifier.width(18.dp))
        Text("启动", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StopButton(onClick: () -> Unit, modifier: Modifier, stopping: Boolean = false) {
    TvSurface(
        onClick = onClick,
        modifier = modifier.width(360.dp).height(104.dp),
        shape = RoundedCornerShape(18.dp),
        focusedBorder = Color(0xFF9F1239),
        idleBorder = Danger,
        focusedFill = Color(0xFFEF5A5F),
        idleFill = Danger,
        glow = true,
        borderWidth = 2.dp,
        focusedBorderWidth = 6.dp,
        requestInitial = true,
    ) {
        StopIcon(28.dp, Color.White)
        Spacer(Modifier.width(16.dp))
        Text(
            if (stopping) "正在停止…" else "停止",
            color = Color.White,
            fontSize = if (stopping) 28.sp else 34.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun OutlinedAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    leading: (@Composable () -> Unit)? = null,
) {
    TvSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        focusedBorder = FocusRing,
        idleBorder = BorderIdle,
        focusedFill = Card,
        idleFill = Card,
        borderWidth = 2.dp,
        focusedBorderWidth = 5.dp,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(10.dp))
        }
        Text(label, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
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
        OutlinedAction(onClick = onTest, label = "测试")
        Row(verticalAlignment = Alignment.CenterVertically) {
            val pending = text.isBlank() || text.contains("正在") || text == "尚未测试外网"
            val dot = when {
                pending -> TextMuted
                ok -> OnlineGreen
                else -> Danger
            }
            Box(
                Modifier
                    .size(14.dp)
                    .background(dot, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    text.isNotBlank() -> text
                    ok -> "外网可达"
                    else -> "尚未测试外网"
                },
                color = Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(max = 560.dp),
            )
        }
    }
}

@Composable
private fun StatusBanner(state: HomeUiState, modifier: Modifier = Modifier) {
    val error = state.error
    val text = when {
        !error.isNullOrBlank() -> error
        state.statusText.isNotBlank() -> state.statusText
        !state.hasNode -> "请先在设置或网页导入节点"
        else -> null
    }
    if (text.isNullOrBlank()) return
    val color = if (!error.isNullOrBlank() || !state.hasNode) Danger else TextMuted
    Text(
        text = text ?: "",
        color = color,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
        textAlign = TextAlign.Center,
        modifier = modifier
            .widthIn(max = 720.dp)
            .fillMaxWidth()
            .background(Card.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
            .border(1.dp, if (!error.isNullOrBlank()) Danger.copy(alpha = 0.35f) else BorderIdle, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}
