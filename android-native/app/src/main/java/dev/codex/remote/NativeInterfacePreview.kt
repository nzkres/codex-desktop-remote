package dev.codex.remote

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.min

/** An offline, native playground. Production tasks and transports never enter this screen. */
@Composable
internal fun NativeInterfacePreview(onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        var quotaPreview by rememberSaveable { mutableStateOf(true) }
        if (quotaPreview) {
            QuotaActivityPreview(onClose = onClose, onInteractions = { quotaPreview = false })
            return@Dialog
        }
        val palette = LocalAppPalette.current
        val prefs = LocalContext.current.getSharedPreferences("interface-preview-only-v1", Context.MODE_PRIVATE)
        var device by rememberSaveable { mutableStateOf("work") }
        var chat by rememberSaveable { mutableStateOf(false) }
        var runningOnly by rememberSaveable { mutableStateOf(false) }
        var outcome by rememberSaveable { mutableStateOf(PreviewOutcome.Success) }
        var outcomeOpen by remember { mutableStateOf(false) }
        val sessions = remember { mutableMapOf<String, MutableState<InterfacePreviewState>>() }
        var demo by remember(device) { sessions.getOrPut(device) {
            val revision = prefs.getInt("$device.revision", 0)
            mutableStateOf(InterfacePreviewState(
                phase = if (revision > 0) PreviewPhase.Completed else PreviewPhase.Running,
                revision = revision, seenRevision = prefs.getInt("$device.seen", 0)))
        } }
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
        DisposableEffect(lifecycle) {
            val observer = LifecycleEventObserver { _, _ -> active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
            lifecycle.addObserver(observer)
            onDispose { lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(device, demo.revision, demo.seenRevision) {
            prefs.edit().putInt("$device.revision", demo.revision).putInt("$device.seen", demo.seenRevision).apply()
        }
        LaunchedEffect(device, demo.phase, active) {
            if (!active) return@LaunchedEffect
            if (demo.phase == PreviewPhase.Sending) { delay(1600); demo = demo.receive(outcome) }
            else if (demo.phase == PreviewPhase.Received) { delay(1000); demo = demo.copy(phase = PreviewPhase.Running) }
        }
        BackHandler { if (chat) chat = false else onClose() }
        val backdrop = rememberGraphicsLayer()
        var backdropOrigin by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(if (palette.isNight) Color(LocalChatAppearance.current.skin.palette.dark) else palette.background)
            .statusBarsPadding().navigationBarsPadding().imePadding()) {
            Box(Modifier.matchParentSize().onGloballyPositioned { backdropOrigin = it.positionInRoot() }
                .drawWithContent { backdrop.record { this@drawWithContent.drawContent() }; drawLayer(backdrop) }) {
                TerminalGrid(demo.phase == PreviewPhase.Running, Modifier.matchParentSize())
            }
            Column(Modifier.fillMaxSize().testTag("native-interface-preview")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { if (chat) chat = false else onClose() }) { Text(if (chat) "返回列表" else "关闭") }
                    Text("界面预览", Modifier.weight(1f), color = palette.ink, fontSize = 15.sp)
                    TextButton(onClick = { quotaPreview = true }) { Text("额度动效", fontSize = 11.sp) }
                    Text("离线演示", color = palette.muted, fontSize = 11.sp)
                }
                Text("仅演示 · 不发送真实消息 · 不改变正式对话", Modifier.padding(horizontal = 16.dp), color = palette.muted, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { demo = demo.complete(); chat = false; runningOnly = false },
                        modifier = Modifier.testTag("preview-complete")) { Text("演示完成 → 未读", fontSize = 12.sp) }
                    Box {
                        TextButton(onClick = { outcomeOpen = true }, enabled = demo.phase !in listOf(PreviewPhase.Sending, PreviewPhase.Received)) {
                            Text("发送结果：${outcome.label}", fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = outcomeOpen, onDismissRequest = { outcomeOpen = false }) {
                            PreviewOutcome.entries.forEach { value -> DropdownMenuItem(text = { Text(value.label) }, onClick = {
                                outcome = value; outcomeOpen = false
                                demo = demo.copy(phase = PreviewPhase.Idle, draft = demo.draft.ifBlank { "继续检查下一步。" })
                            }) }
                        }
                    }
                }
                HorizontalDivider(color = palette.divider.copy(alpha = .5f), thickness = .5.dp)
                if (!chat) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("work" to "工作电脑", "home" to "家用电脑").forEach { (id, name) ->
                            Column(Modifier.clickable { device = id; runningOnly = false }.padding(end = 20.dp, top = 14.dp, bottom = 8.dp)) {
                                Text(name, color = if (device == id) palette.ink else palette.muted, fontSize = 14.sp)
                                Spacer(Modifier.height(6.dp))
                                Box(Modifier.width(48.dp).height(1.dp).background(if (device == id) palette.green else Color.Transparent))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { runningOnly = !runningOnly }) {
                            Text("运行中 ${if (demo.phase == PreviewPhase.Running) 1 else 0}" + if (runningOnly) " · 筛选中" else "", fontSize = 12.sp)
                        }
                        Text("未读 ${if (demo.unread) 1 else 0}", color = palette.muted, fontSize = 12.sp)
                    }
                    Column(Modifier.weight(1f)) {
                        if (!runningOnly || demo.phase == PreviewPhase.Running) {
                            PreviewConversationRow("接口文档检查", "核对接口定义和调用示例", demo.phase.label,
                                demo.unread, demo.phase == PreviewPhase.Running, "preview-main-row", onClick = { chat = true })
                        } else Text("这台演示电脑没有运行中的对话", Modifier.padding(16.dp), color = palette.muted, fontSize = 13.sp)
                        if (!runningOnly) {
                            PreviewConversationRow("阅读工作台", "已整理本轮界面修改", "已读 · 18:24", false, false,
                                "preview-read-row")
                        }
                    }
                    Text("点“演示完成”观察未读；打开对话，滑到最新结果后变成已读。",
                        Modifier.padding(16.dp), color = palette.muted, fontSize = 12.sp)
                } else {
                    val list = rememberLazyListState()
                    val resultVisible by remember(list, demo.phase) { derivedStateOf {
                        list.layoutInfo.visibleItemsInfo.any { item ->
                            item.key == "preview-final" && item.size > 0 &&
                                (min(item.offset + item.size, list.layoutInfo.viewportEndOffset) -
                                    item.offset.coerceAtLeast(list.layoutInfo.viewportStartOffset)) >= item.size * .65f
                        }
                    } }
                    LaunchedEffect(resultVisible, active, demo.revision, demo.phase) { demo = demo.seeResult(resultVisible, active) }
                    LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth().testTag("preview-transcript"),
                        contentPadding = PaddingValues(vertical = 12.dp)) {
                        item { PreviewParagraph("› 帮我检查接口文档，补充缺失的调用说明。", true) }
                        item { PreviewParagraph("我会先核对接口定义，再整理需要补充的内容。") }
                        // The scrollable history deliberately keeps the latest result below the fold.
                        items(7) { index ->
                            PreviewParagraph("› 已检查第 ${index + 1} 组接口\n  └ 参数、响应和错误码一致\n  Read  api/contract-${index + 1}.md")
                        }
                        item(key = "preview-final") {
                            Column(Modifier.fillMaxWidth().testTag("preview-latest-result")) {
                                PreviewParagraph(if (demo.phase == PreviewPhase.Completed)
                                    "已完成接口一致性检查。\n补充了 3 处调用说明，示例已整理。" else "正在整理调用示例…")
                                Text(if (demo.phase == PreviewPhase.Completed) "${if (demo.unread) "未读" else "已读"} · 本轮结果 ${demo.revision}" else demo.phase.label,
                                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp), color = palette.muted, fontSize = 11.sp)
                            }
                        }
                    }
                    PreviewComposer(demo, onDraft = { demo = demo.copy(draft = it) },
                        onAction = { demo = if (demo.phase == PreviewPhase.Running) demo.complete() else demo.submit() },
                        backdrop = backdrop, backdropOrigin = backdropOrigin)
                }
            }
        }
    }
}

@Composable private fun PreviewConversationRow(title: String, subtitle: String, status: String, unread: Boolean,
    running: Boolean, tag: String, onClick: (() -> Unit)? = null) {
    val p = LocalAppPalette.current
    Row(Modifier.fillMaxWidth().testTag(tag).background(p.muted.copy(alpha = if (tag == "preview-main-row") .035f else .07f))
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (unread) Box(Modifier.padding(end = 7.dp).size(5.dp).background(p.green, CircleShape))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.ink, fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal, fontSize = 14.sp)
            Text(subtitle, color = p.muted, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(status, Modifier.processingLight(running), color = if (running) p.green else p.muted, fontSize = 11.sp)
            if (!running && tag == "preview-main-row") Text(if (unread) "未读" else "已读", color = p.muted, fontSize = 11.sp)
        }
    }
}

@Composable private fun PreviewParagraph(text: String, user: Boolean = false) {
    val p = LocalAppPalette.current
    val skin = LocalChatAppearance.current.skin
    val font = remember(skin.font) { skin.font.resource?.let { FontFamily(Font(it)) }
        ?: if (skin.font == BodyFont.NotoSerif) FontFamily.Serif else FontFamily.SansSerif }
    Text(text, Modifier.fillMaxWidth().background(if (user) p.muted.copy(alpha = .12f) else Color.Transparent)
        .padding(horizontal = 16.dp, vertical = 9.dp), color = skinInk(LocalChatAppearance.current.skin, p.isNight),
        style = TextStyle(fontFamily = font, shadow = if (skin.textShadow) Shadow(p.ink.copy(alpha = .2f), Offset(0f, 1f), 2f) else null),
        fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable private fun PreviewComposer(state: InterfacePreviewState, onDraft: (String) -> Unit, onAction: () -> Unit,
    backdrop: androidx.compose.ui.graphics.layer.GraphicsLayer, backdropOrigin: Offset) {
    val p = LocalAppPalette.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val focus = remember { FocusRequester() }
    val shape = RoundedCornerShape(28.dp)
    val startedAt = remember(state.phase) { System.currentTimeMillis() - 138000L }
    Column(Modifier.fillMaxWidth().padding(10.dp).onGloballyPositioned { origin = it.positionInRoot() }
        .clip(shape).border(.7.dp, p.muted.copy(alpha = .32f), shape)) {
        Box {
            GlassMaterial(backdrop, origin - backdropOrigin, Modifier.matchParentSize())
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                ComposerWorking(state.phase == PreviewPhase.Running, false, startedAt, Modifier.padding(start = 8.dp))
                Text(state.phase.label, Modifier.padding(start = 8.dp), color = if (state.phase == PreviewPhase.Failed) p.errorInk else p.muted, fontSize = 11.sp)
                ComposerEditor(state.draft, onDraft, state.phase !in listOf(PreviewPhase.Sending, PreviewPhase.Received),
                    true, state.phase == PreviewPhase.Running, focus, p.green)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("执行模式", color = p.ink, fontSize = 12.sp)
                    Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(14.dp).background(p.muted.copy(alpha = .45f)))
                    Text("模型 · 演示", Modifier.weight(1f), color = p.muted, fontSize = 12.sp)
                    PreviewSendButton(state.phase, state.canSend, onAction)
                }
                if (state.phase == PreviewPhase.Unknown) Text("仅演示：回执未确认，保留输入并禁止重发。", color = p.muted, fontSize = 11.sp)
            }
        }
    }
}

/** Fixed vector arrow; only the thin ring rotates. The production composer is unchanged. */
@Composable internal fun PreviewSendButton(phase: PreviewPhase, canSend: Boolean, onAction: () -> Unit) {
    val p = LocalAppPalette.current
    val motion = rememberChatMotion()
    val sending = phase == PreviewPhase.Sending
    val angle = if (sending && motion) rememberInfiniteTransition(label = "preview-send-ring").animateFloat(0f, 360f,
        infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "preview-ring-angle") else null
    val label = when(phase) {
        PreviewPhase.Running -> "停止演示任务"; PreviewPhase.Sending -> "演示正在发送"
        PreviewPhase.Received -> "演示电脑已收到"; PreviewPhase.Unknown -> "演示回执待核对"
        else -> "演示发送消息"
    }
    IconButton(onClick = onAction, enabled = canSend || phase == PreviewPhase.Running,
        modifier = Modifier.size(48.dp).testTag("preview-send-button").semantics { contentDescription = label }) {
        Box(Modifier.size(36.dp).background(p.green.copy(alpha = .12f), CircleShape)
            .border(.6.dp, p.green.copy(alpha = .32f), CircleShape), contentAlignment = Alignment.Center) {
            if (sending) Canvas(Modifier.size(34.dp).testTag("preview-send-ring")) {
                val inset = 1.5.dp.toPx()
                drawArc(p.green, angle?.value ?: -90f, 85f, false, Offset(inset,inset),
                    Size(size.width - inset * 2,size.height - inset * 2), style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round))
            }
            when(phase) {
                PreviewPhase.Received -> Canvas(Modifier.size(18.dp)) {
                    val line = 1.7.dp.toPx()
                    drawLine(p.green, Offset(size.width*.18f,size.height*.52f), Offset(size.width*.42f,size.height*.75f),line,StrokeCap.Round)
                    drawLine(p.green, Offset(size.width*.42f,size.height*.75f), Offset(size.width*.82f,size.height*.25f),line,StrokeCap.Round)
                }
                PreviewPhase.Unknown -> Canvas(Modifier.size(18.dp)) {
                    drawCircle(p.muted, radius = size.minDimension*.36f, style = Stroke(1.5.dp.toPx()))
                    drawLine(p.muted, center, Offset(center.x, size.height*.28f),1.5.dp.toPx(),StrokeCap.Round)
                    drawLine(p.muted, center, Offset(size.width*.67f, center.y),1.5.dp.toPx(),StrokeCap.Round)
                }
                else -> ComposerGlyph(if (phase == PreviewPhase.Running) "stop" else "send", p.green,
                    Modifier.size(18.dp).testTag("preview-fixed-arrow"))
            }
        }
    }
}
