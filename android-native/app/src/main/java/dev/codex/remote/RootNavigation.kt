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

enum class RootTab(val label: String) { Terminals("远程终端"), Tasks("任务"), Activity("动态"), Me("我的") }

@Composable
fun RootBottomBar(selected: RootTab, onSelect: (RootTab) -> Unit) {
    HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
    Row(Modifier.fillMaxWidth().height(66.dp).background(HapiPalette.background).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
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
            RootTab.Terminals -> {
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
            RootTab.Activity -> {
                drawLine(color, Offset(w * .10f, h * .53f), Offset(w * .30f, h * .53f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .30f, h * .53f), Offset(w * .43f, h * .19f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .43f, h * .19f), Offset(w * .58f, h * .82f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .58f, h * .82f), Offset(w * .73f, h * .46f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(w * .73f, h * .46f), Offset(w * .90f, h * .46f), stroke, cap = StrokeCap.Round)
            }
            RootTab.Me -> {
                drawCircle(color, w * .16f, Offset(w * .5f, h * .32f), style = Stroke(stroke))
                drawArc(color, 198f, 144f, false, topLeft = Offset(w * .15f, h * .39f), size = androidx.compose.ui.geometry.Size(w * .7f, h * .68f), style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
    }
}

@Composable
fun ActivityScreen(entries: List<ActivityEntry>, draftStore: DraftStore? = null, terminalName: String = "", onOpen: (String) -> Unit) {
    val sorted = entries.sortedWith(compareBy<ActivityEntry> {
        when (it.observation.state) { "running" -> 0; "quiet" -> 1; else -> 2 }
    }.thenByDescending { it.observation.lastActivityAt })
    Column(Modifier.fillMaxSize().background(HapiPalette.background)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("动态", color = HapiPalette.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text((if (terminalName.isBlank()) "" else "$terminalName · ") + "任务运行与最近活动", color = HapiPalette.muted, fontSize = 12.sp)
        }
        HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无任务动态", color = HapiPalette.muted)
            }
        } else LazyColumn(Modifier.fillMaxSize()) {
            items(sorted, key = { it.session.id }) { entry ->
                val state = when (entry.observation.state) {
                    "running" -> "执行中"
                    "quiet" -> "未看到结束记录"
                    "completed" -> "已完成"
                    "interrupted" -> "已中断"
                    else -> "等待开始"
                }
                val active = sessionProcessing(entry.session, entry.observation)
                Row(Modifier.fillMaxWidth().clickable { onOpen(entry.session.id) }.padding(horizontal = 18.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(if (active) HapiPalette.green else HapiPalette.faint, RoundedCornerShape(50)))
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.session.title, maxLines = 1, overflow = TextOverflow.Ellipsis, color = HapiPalette.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(4.dp))
                        Text(state, Modifier.processingLight(active), color = if (active) HapiPalette.green else HapiPalette.muted, fontSize = 12.sp)
                        DraftLine(draftStore?.read(entry.session.id)?.text.orEmpty(), Modifier.padding(top = 4.dp))
                    }
                    Text("›", color = HapiPalette.faint, fontSize = 23.sp)
                }
                HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
fun MeScreen(
    gatewayUrl: String,
    night: Boolean,
    onToggleNight: () -> Unit,
    onChangeGateway: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(HapiPalette.background).verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("我的", color = HapiPalette.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(3.dp))
            Text("统一账号已登录；在远程终端中选择执行电脑", color = HapiPalette.muted, fontSize = 12.sp)
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
        if (BuildConfig.DEBUG) Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp)) {
            Text("测试版更新", color = HapiPalette.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text("当前 ${BuildConfig.VERSION_NAME} · 新测试包请手动安装", color = HapiPalette.muted, fontSize = 12.sp)
        }
        if (BuildConfig.DEBUG) HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
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
