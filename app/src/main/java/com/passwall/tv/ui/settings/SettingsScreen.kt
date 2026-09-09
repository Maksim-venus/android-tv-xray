package com.passwall.tv.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
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
import com.passwall.tv.ui.components.tvClickable
import com.passwall.tv.ui.theme.Accent
import com.passwall.tv.ui.theme.BorderIdle
import com.passwall.tv.ui.theme.Card
import com.passwall.tv.ui.theme.Card2
import com.passwall.tv.ui.theme.FocusRing
import com.passwall.tv.ui.theme.Ink
import com.passwall.tv.ui.theme.OnlineGreen
import com.passwall.tv.ui.theme.TextMuted
import com.passwall.tv.ui.theme.VlessPurple
import com.passwall.tv.ui.theme.VmessBlue

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
            FocusableIcon(onBack) { BackArrowIcon(color = Ink) }
            Spacer(Modifier.width(12.dp))
            Text("设置", color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
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
                if (state.nodes.isEmpty()) {
                    EmptyNodesHint()
                    Spacer(Modifier.height(12.dp))
                } else {
                    state.nodes.forEach { node ->
                        NodeRow(
                            node = node,
                            selected = node.id == state.selectedNodeId,
                            onClick = { onSelectNode(node.id) },
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                ToggleRow(
                    title = "允许不安全 SSL",
                    subtitle = "当前核心已取消跳过证书校验。开启后按证书名验证（vcn）；自签证书请在链接提供 pcs。",
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
private fun EmptyNodesHint() {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Card, RoundedCornerShape(14.dp))
            .border(2.dp, BorderIdle, RoundedCornerShape(14.dp))
            .padding(18.dp),
    ) {
        Text("还没有节点", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "请开启右侧「HTTP 编辑」，用手机浏览器导入 vless:// 或 vmess://。",
            color = TextMuted,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun NodeRow(node: ProxyNode, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val borderWidth = if (focused) 5.dp else if (selected) 3.dp else 1.5.dp
    val border = when {
        focused -> FocusRing
        selected -> Accent
        else -> BorderIdle
    }
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = if (focused) 1.03f else 1f
                scaleX = s
                scaleY = s
            }
            .shadow(if (focused) 14.dp else 2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(borderWidth, border, RoundedCornerShape(14.dp))
            .tvClickable(interaction, onClick = onClick)
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
        Text(node.name, color = Ink, fontSize = 18.sp, modifier = Modifier.weight(1f))
        if (selected) {
            CheckIcon(color = Accent)
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
private fun ToggleRow(title: String, checked: Boolean, onClick: () -> Unit, subtitle: String? = null) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Row(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val s = if (focused) 1.03f else 1f
                scaleX = s
                scaleY = s
            }
            .shadow(if (focused) 14.dp else 2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(if (focused) 5.dp else 1.5.dp, if (focused) FocusRing else BorderIdle, RoundedCornerShape(14.dp))
            .tvClickable(interaction, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Ink, fontSize = 17.sp)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(subtitle, color = TextMuted, fontSize = 13.sp)
            }
        }
        val track = if (checked) Accent else Color(0xFFD1D5DB)
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
            .graphicsLayer {
                val s = if (focused) 1.03f else 1f
                scaleX = s
                scaleY = s
            }
            .shadow(if (focused) 14.dp else 2.dp, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(
                if (focused) 5.dp else 1.5.dp,
                if (focused) FocusRing else if (enabled) Accent else BorderIdle,
                RoundedCornerShape(14.dp),
            )
            .tvClickable(interaction, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("开启 HTTP 编辑", color = Ink, fontSize = 17.sp, modifier = Modifier.weight(1f))
        PencilIcon(color = if (enabled) Accent else Ink)
    }
}

@Composable
private fun HttpPanel(state: SettingsUiState) {
    if (!state.httpEditEnabled || state.httpUrl.isNullOrBlank()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Card, RoundedCornerShape(18.dp))
                .border(1.5.dp, BorderIdle, RoundedCornerShape(18.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "HTTP 编辑未开启。打开后可在同一局域网用浏览器导入节点。",
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
            .border(1.5.dp, BorderIdle, RoundedCornerShape(18.dp))
            .padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlobeIcon(color = Accent)
            Spacer(Modifier.width(10.dp))
            Text("HTTP 编辑已开启", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(18.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(Card2, RoundedCornerShape(14.dp))
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(state.httpUrl, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
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
            .size(48.dp)
            .graphicsLayer {
                val s = if (focused) 1.08f else 1f
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(Card)
            .border(if (focused) 5.dp else 1.5.dp, if (focused) FocusRing else BorderIdle, CircleShape)
            .tvClickable(interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { icon() }
}
