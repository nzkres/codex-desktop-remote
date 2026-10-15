package dev.codex.remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class RootTab(val label: String) { Tasks("对话"), Projects("任务仓库"), Me("我的") }

@Composable
fun RootBottomBar(selected: RootTab, onSelect: (RootTab) -> Unit) {
    HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
    Row(Modifier.fillMaxWidth().height(66.dp).background(HapiPalette.background.copy(alpha = .12f)).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        RootTab.entries.forEach { tab ->
            val active = selected == tab
            val tint = if (active) HapiPalette.green else HapiPalette.muted
            Column(
                Modifier.weight(1f).fillMaxHeight().clickable { onSelect(tab) }
                    .semantics { contentDescription = "${tab.label}页面" },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                NavGlyph(tab, active, Modifier.size(22.dp))
                Spacer(Modifier.height(3.dp))
                Text(tab.label, color = tint, fontSize = 11.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun NavGlyph(tab: RootTab, active: Boolean, modifier: Modifier = Modifier) {
    val color = if (active) HapiPalette.green else HapiPalette.muted
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.8.dp.toPx()
        when (tab) {
            RootTab.Projects -> {
                drawRect(color, topLeft = Offset(w * .12f, h * .15f), size = androidx.compose.ui.geometry.Size(w * .76f, h * .53f), style = Stroke(stroke))
                drawLine(color, Offset(w * .5f, h * .68f), Offset(w * .5f, h * .86f), stroke)
                drawLine(color, Offset(w * .3f, h * .86f), Offset(w * .7f, h * .86f), stroke, cap = StrokeCap.Round)
            }
            RootTab.Tasks -> {
                for (y in listOf(.24f, .50f, .76f)) {
                    drawCircle(color, 1.5.dp.toPx(), Offset(w * .18f, h * y))
                    drawLine(color, Offset(w * .34f, h * y), Offset(w * .85f, h * y), stroke, cap = StrokeCap.Round)
                }
            }
            RootTab.Me -> {
                drawCircle(color, w * .16f, Offset(w * .5f, h * .32f), style = Stroke(stroke))
                drawArc(color, 198f, 144f, false, topLeft = Offset(w * .15f, h * .39f), size = androidx.compose.ui.geometry.Size(w * .7f, h * .68f), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun MeScreen(
    gatewayUrl: String,
    onManageDevices: () -> Unit = {},
    night: Boolean,
    onToggleNight: () -> Unit,
    updateStatus: String,
    updateProgress: String,
    updateBusy: Boolean,
    updateAvailable: Boolean,
    onCheckUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    onChangeGateway: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(HapiPalette.background.copy(alpha = .12f)).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("我的", color = HapiPalette.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text("统一账号 · 在对话顶部选择电脑", color = HapiPalette.muted, fontSize = 12.sp)
        }
        HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 22.dp)) {
            Text("中继地址", color = HapiPalette.muted, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text(gatewayUrl, color = HapiPalette.ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onChangeGateway, contentPadding = PaddingValues(0.dp)) { Text("更改地址", fontSize = 12.sp) }
        }
        HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
        TextButton(onClick = onManageDevices, modifier = Modifier.padding(horizontal=10.dp)) { Text("设备与配对管理") }
        ReleaseNotesEntry(updateStatus, updateProgress, updateBusy, updateAvailable, onCheckUpdate, onInstallUpdate)
        HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
        NotificationSettings()
        HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth().clickable(onClick = onToggleNight).padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("外观", Modifier.weight(1f), color = HapiPalette.ink, fontSize = 15.sp)
            Text(if (night) "深夜" else "浅色", color = HapiPalette.muted, fontSize = 13.sp)
            Spacer(Modifier.width(8.dp))
            Text("›", color = HapiPalette.faint, fontSize = 20.sp)
        }
        HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
        ChatAppearanceSettings()
        HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
        TextButton(onClick = onDisconnect, modifier = Modifier.padding(start = 10.dp, top = 10.dp)) {
            Text("断开连接", color = HapiPalette.muted)
        }
        FontLicenseInfo()
        Spacer(Modifier.height(24.dp))
        Text("Desktop Remote Lite · 测试版 ${BuildConfig.VERSION_NAME}", modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp), color = HapiPalette.faint, fontSize = 11.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
