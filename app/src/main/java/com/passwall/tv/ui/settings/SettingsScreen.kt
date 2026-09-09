package com.passwall.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.passwall.data.model.Protocol
import com.passwall.data.model.ProxyNode
import com.passwall.tv.ui.SettingsUiState
import com.passwall.tv.ui.components.BackArrowIcon
import com.passwall.tv.ui.components.CheckIcon
import com.passwall.tv.ui.components.GlobeIcon
import com.passwall.tv.ui.components.PencilIcon
import com.passwall.tv.ui.components.qrImage
import com.passwall.tv.ui.theme.Accent
import com.passwall.tv.ui.theme.Card
import com.passwall.tv.ui.theme.Card2
import com.passwall.tv.ui.theme.OnlineGreen
import com.passwall.tv.ui.theme.TextMuted
import com.passwall.tv.ui.theme.VlessPurple
import com.passwall.tv.ui.theme.VmessBlue
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onSelectNode: (Long) -> Unit,
    onToggleInsecure: (Boolean) -> Unit,
    onToggleHttp: (Boolean) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusableIcon(onBack) { BackArrowIcon() }
            Spacer(Modifier.width(12.dp))
            Text("设置", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Column(
                Modifier
                    .weight(1.05f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text("节点列表", color = TextMuted, fontSize = 14.sp)
                Spacer(Modifier.height(10.dp))
                state.nodes.forEach { node ->
                    NodeRow(
                        node = node,
                        selected = node.id == state.selectedNodeId,
                        onClick = { onSelectNode(node.id) },
                    )
                    Spacer(Modifier.height(10.dp))
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = "允许不安全 SSL",
                    checked = state.allowInsecure,
                    onClick = { onToggleInsecure(!state.allowInsecure) },
                )
                Spacer(Modifier.height(10.dp))
                HttpEditRow(
                    enabled = state.httpEditEnabled,
                    onClick = { onToggleHttp(!state.httpEditEnabled) },
                )
            }
            Box(Modifier.weight(0.95f).fillMaxHeight()) {
                HttpPanel(state)
            }
        }
    }
}

@Composable
private fun NodeRow(node: ProxyNode, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border = when {
        selected -> Accent
        focused -> Accent.copy(alpha = 0.8f)
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(2.dp, border, RoundedCornerShape(14.dp))
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val badgeColor = if (node.protocol == Protocol.VMESS) VmessBlue else VlessPurple
        Box(
            Modifier
                .background(badgeColor, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(node.protocol.badge, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(14.dp))
        Text(node.name, color = Color.White, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (selected) {
            CheckIcon()
        } else {
            Box(
                Modifier
                    .size(8.dp)
                    .background(if (node.online) OnlineGreen else TextMuted, CircleShape),
            )
            Spacer(Modifier.width(6.dp))
            Text("在线", color = TextMuted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(2.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(14.dp))
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
        val track = if (checked) Accent else Color(0xFF3A4154)
        Box(
            Modifier
                .width(48.dp)
                .height(28.dp)
                .background(track, RoundedCornerShape(20.dp))
                .padding(3.dp),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(Modifier.size(22.dp).background(Color.White, CircleShape))
        }
    }
}

@Composable
private fun HttpEditRow(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(2.dp, if (focused || enabled) Accent else Color.Transparent, RoundedCornerShape(14.dp))
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("开启 HTTP 编辑", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
        PencilIcon()
    }
}

@Composable
private fun HttpPanel(state: SettingsUiState) {
    if (!state.httpEditEnabled || state.httpUrl.isNullOrBlank()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Card.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "HTTP 编辑未开启。打开后可在同一局域网用浏览器管理节点。",
                color = TextMuted,
                fontSize = 16.sp,
            )
        }
        return
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Card, RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(18.dp))
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlobeIcon()
            Spacer(Modifier.width(10.dp))
            Text("HTTP 编辑已开启", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(Card2, RoundedCornerShape(14.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.httpUrl, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(16.dp))
            val qr = remember(state.httpUrl) { qrImage(state.httpUrl) }
            Image(
                bitmap = qr,
                contentDescription = "HTTP 编辑二维码",
                modifier = Modifier
                    .size(220.dp)
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun FocusableIcon(onClick: () -> Unit, icon: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .border(if (focused) 2.dp else 0.dp, Accent, CircleShape)
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}
