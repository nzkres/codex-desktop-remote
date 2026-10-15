package dev.codex.remote

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive

/** Synthetic account identifiers only: no account matching or signals read from live devices. */
@Composable internal fun QuotaActivityPreview(onClose: () -> Unit, onInteractions: () -> Unit) {
    val p = LocalAppPalette.current
    val appearance = LocalChatAppearance.current
    var selected by rememberSaveable { mutableStateOf("work") }
    var work by rememberSaveable { mutableStateOf(false) }
    var home by rememberSaveable { mutableStateOf(false) }
    var studio by rememberSaveable { mutableStateOf(false) }
    var style by rememberSaveable { mutableStateOf(appearance.skin.quotaStyle) }
    var stylesOpen by remember { mutableStateOf(false) }
    var lightEnabled by remember { mutableStateOf(false) }
    val devices = listOf(QuotaActivityDevice("work", "demo-plus", work),
        QuotaActivityDevice("home", "demo-plus", home), QuotaActivityDevice("studio", "demo-pro", studio))
    val account = devices.first { it.id == selected }.account!!
    val running = accountQuotaRunning(account, devices)
    val latestDevices by rememberUpdatedState(devices)
    val models = remember { mapOf("demo-plus" to QuotaActivityMotion(), "demo-pro" to QuotaActivityMotion()) }
    val ticks = remember { mutableFloatStateOf(0f) }
    val motion = rememberChatMotion()
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val dt = (now - previous) / 1_000_000_000f
                previous = now
                models.forEach { (id, model) -> model.advance(dt, accountQuotaRunning(id, latestDevices)) }
                ticks.floatValue += dt.coerceIn(0f, .1f)
            }
        }
    }
    val stage by remember(account, running, motion) { derivedStateOf {
        ticks.floatValue
        val energy = models.getValue(account).frame.energy
        when {
            !motion -> if (running) "减少动画 · 静态高亮" else "静止"
            running && energy < .95f -> "启动过渡 · 逐渐提亮与加速"
            running -> "运行中 · 呼吸与旋转"
            energy > .01f -> "停止过渡 · 逐渐减速与淡出"
            else -> "空闲 · 静止"
        }
    } }
    val now = remember { System.currentTimeMillis() }
    val quota = remember(account) {
        val windows = if (account == "demo-plus") listOf(UsageWindow(32.0, 300, now / 1000 + 18000),
            UsageWindow(57.0, 10080, now / 1000 + 604800)) else listOf(UsageWindow(24.0, 10080, now / 1000 + 604800))
        DeviceQuota("preview-only", UsageSnapshot(listOf(UsageLimit("codex", now, windows,
            if (account == "demo-plus") "plus" else "pro"))))
    }
    val previewSkin = appearance.skin.copy(quotaStyle = style,
        flowBrightness = if (lightEnabled) 1f else appearance.skin.flowBrightness)
    val backdrop = rememberGraphicsLayer()
    var backdropOrigin by remember { mutableStateOf(Offset.Zero) }
    Box(Modifier.fillMaxSize().background(if (p.isNight) Color(appearance.skin.palette.dark) else p.background)
        .statusBarsPadding().navigationBarsPadding().testTag("quota-activity-preview")) {
        Box(Modifier.matchParentSize().onGloballyPositioned { backdropOrigin = it.positionInRoot() }
            .drawWithContent { backdrop.record { this@drawWithContent.drawContent() }; drawLayer(backdrop) }) {
            TerminalGrid(running, Modifier.matchParentSize(), showQuota = false)
            Canvas(Modifier.matchParentSize().testTag("quota-activity-canvas")) {
                ticks.floatValue // Invalidate only drawing; do not re-layout text every frame.
                val frame = if (motion) models.getValue(account).frame else QuotaActivityFrame(if (running) .65f else 0f, 0f, .5f)
                // The shared envelope breathes; keep each style's internal pattern fixed.
                drawQuotaCore(quota, now, 0f, previewSkin, p.isNight, activity = frame)
            }
        }
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("关闭") }
                Text("额度动效预览", Modifier.weight(1f), color = p.ink, fontSize = 15.sp)
                TextButton(onClick = onInteractions) { Text("未读与发送", fontSize = 11.sp) }
            }
            Text("离线演示 · 同账号联动 · 数字固定，不模拟扣减", Modifier.padding(horizontal = 16.dp), color = p.muted, fontSize = 11.sp)
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf("work" to "工作电脑", "home" to "家用电脑", "studio" to "另一账号").forEach { (id, title) ->
                    Column(Modifier.weight(1f).testTag("quota-device-$id").clickable { selected = id }.padding(top = 12.dp, bottom = 8.dp)) {
                        Text(title, color = if (selected == id) p.ink else p.muted, fontSize = 13.sp)
                        Text(if (id == "studio") "账号 B · Pro" else "账号 A · Plus", color = p.muted, fontSize = 10.sp)
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.width(40.dp).height(1.dp).background(if (selected == id) p.green else Color.Transparent))
                    }
                }
            }
            Text(stage, Modifier.padding(horizontal = 16.dp).testTag("quota-motion-stage"), color = p.muted, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            // A compact transcript behind the glass keeps legibility visible while the core breathes.
            Column(Modifier.fillMaxWidth().background(p.background.copy(alpha = .14f)).padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text("› 检查接口文档与项目计划", color = p.ink, fontSize = 13.sp)
                Text(if (running) "● 正在执行 · 同账号至少一台电脑运行" else "所有同账号电脑已空闲", color = p.muted, fontSize = 11.sp)
            }
            var origin by remember { mutableStateOf(Offset.Zero) }
            val shape = RoundedCornerShape(24.dp)
            Box(Modifier.fillMaxWidth().padding(10.dp).onGloballyPositioned { origin = it.positionInRoot() }
                .clip(shape).border(.6.dp, p.muted.copy(alpha = .3f), shape)) {
                GlassMaterial(backdrop, origin - backdropOrigin, Modifier.matchParentSize())
                Column(Modifier.heightIn(max = 270.dp).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("任务演示", Modifier.weight(1f), color = p.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Box {
                            TextButton(onClick = { stylesOpen = true }) { Text(style.label, fontSize = 11.sp) }
                            DropdownMenu(expanded = stylesOpen, onDismissRequest = { stylesOpen = false }) {
                                QuotaStyle.entries.forEach { candidate -> DropdownMenuItem(text = { Text(candidate.label) }, onClick = {
                                    style = candidate; stylesOpen = false
                                }) }
                            }
                        }
                    }
                    PreviewTaskToggle("工作电脑 · 账号 A", work, "quota-run-work") { work = it }
                    PreviewTaskToggle("家用电脑 · 账号 A", home, "quota-run-home") { home = it }
                    PreviewTaskToggle("另一账号电脑 · 账号 B", studio, "quota-run-studio") { studio = it }
                    Text("启动约 5 秒渐强；全部停止后渐弱。同账号换电脑不断相，不同账号互不影响。",
                        color = p.muted, fontSize = 11.sp)
                    if (previewSkin.flowBrightness == 0f) TextButton(onClick = { lightEnabled = true }) {
                        Text("当前核心亮度为 0，开启本次预览光效", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun PreviewTaskToggle(label: String, running: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    val p = LocalAppPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), color = p.ink, fontSize = 12.sp)
        Text(if (running) "运行中" else "停止", color = p.muted, fontSize = 10.sp)
        Switch(checked = running, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
