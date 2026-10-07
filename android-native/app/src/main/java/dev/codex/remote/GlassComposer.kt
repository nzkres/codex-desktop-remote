package dev.codex.remote

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** One continuous silhouette. The editor keeps its full layout width while the shell morphs. */
@Composable
fun GlassComposer(
    draft: String, onDraft: (String) -> Unit,
    mode: String, busy: Boolean, running: Boolean, sending: Boolean,
    expanded: Boolean, onExpanded: (Boolean) -> Unit,
    dockRight: Boolean, onDockRight: (Boolean) -> Unit,
    hasAttention: Boolean, error: String,
    focusRequester: FocusRequester,
    backdrop: GraphicsLayer, backdropOrigin: Offset,
    onHeight: (Int) -> Unit,
    onMode: (String) -> Unit, onSend: () -> Unit,
    onStop: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val palette = LocalAppPalette.current
    val density = LocalDensity.current
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val appearance = LocalChatAppearance.current
    val accent = conversationColors(appearance.style, palette.isNight).accent
    val motion = rememberChatMotion()
    var editorHeight by remember { mutableIntStateOf(0) }
    var glassOrigin by remember { mutableStateOf(Offset.Zero) }
    var modeOpen by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (expanded) 1f else 0f,
        tween(if (motion) 460 else 0, easing = CubicBezierEasing(.22f, .8f, .2f, 1f)), label = "composerMorph")
    val side by animateFloatAsState(if (dockRight) 1f else 0f,
        tween(if (motion) 420 else 0, easing = FastOutSlowInEasing), label = "composerDock")
    fun collapse(right: Boolean) {
        focus.clearFocus(); keyboard?.hide(); modeOpen = false
        onDockRight(right); onExpanded(false)
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fullWidth = maxWidth
        val editorDp = with(density) { editorHeight.toDp() }.coerceAtLeast(132.dp)
        val shellWidth = androidx.compose.ui.unit.lerp(60.dp, fullWidth, progress)
        val shellHeight = androidx.compose.ui.unit.lerp(60.dp, editorDp, progress)
        val shape = RoundedCornerShape(androidx.compose.ui.unit.lerp(30.dp, 28.dp, progress))
        Box(Modifier.fillMaxWidth().height(shellHeight)) {
            Box(Modifier.offset { IntOffset(((fullWidth - shellWidth).toPx() * side).roundToInt(), 0) }
                .width(shellWidth).height(shellHeight)
                .testTag("composer-shell").onSizeChanged { onHeight(it.height) }
                .onGloballyPositioned { glassOrigin = it.positionInRoot() }
                .shadow(16.dp, shape, clip = false, ambientColor = Color(0xFF354F78).copy(alpha = .12f), spotColor = Color(0xFF183654).copy(alpha = .16f))
                .clip(shape)
                .border(.8.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = if (palette.isNight) .38f else .80f), Color.White.copy(alpha = .05f), Color.White.copy(alpha = .40f))), shape)
            ) {
                GlassMaterial(backdrop, glassOrigin - backdropOrigin, Modifier.matchParentSize())
                Column(Modifier.requiredWidth(fullWidth).wrapContentHeight(Alignment.Top, unbounded = true)
                    .onSizeChanged { editorHeight = it.height }
                    .graphicsLayer { alpha = ((progress - .45f) / .55f).coerceIn(0f, 1f) }
                    .then(if (!expanded) Modifier.clearAndSetSemantics { } else Modifier)
                    .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
                ) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 32.dp) {
                    Row(Modifier.fillMaxWidth().testTag("composer-meta"), verticalAlignment = Alignment.CenterVertically) {
                        Text("消息", color = palette.muted, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        // Keep a real 48dp clipped/clickable IconButton, centered in a 32dp
                        // layout slot. Automatic hit expansion cannot escape its small clip.
                        IconButton(onClick = { collapse(false) }, enabled = expanded, modifier = Modifier.padding(horizontal = 8.dp).size(32.dp).wrapContentSize(unbounded = true).requiredSize(48.dp).semantics { contentDescription = "收起到左侧" }) {
                            ComposerGlyph("left", palette.muted, Modifier.size(19.dp))
                        }
                        IconButton(onClick = { collapse(true) }, enabled = expanded, modifier = Modifier.padding(horizontal = 8.dp).size(32.dp).wrapContentSize(unbounded = true).requiredSize(48.dp).semantics { contentDescription = "收起到右侧" }) {
                            ComposerGlyph("right", palette.muted, Modifier.size(19.dp).testTag("composer-collapse-right-glyph"))
                        }
                    }
                    }
                    ComposerEditor(draft, onDraft, enabled = expanded && !sending, expanded = expanded,
                        running = running, focusRequester = focusRequester, accent = accent)
                    if (error.isNotBlank()) Text(error, color = palette.errorInk, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            GlassActionButton(if (mode == "plan") "Plan · 规划" else "Agent · 执行", { modeOpen = true },
                                Modifier.testTag("composer-mode"), icon = if (mode == "plan") "plan" else "agent",
                                enabled = expanded && !busy && !running && !sending, selected = mode == "plan", chevron = true,
                                menuOpen = modeOpen, backdrop = backdrop, backdropOrigin = backdropOrigin, compact = true)
                            DropdownMenu(expanded = modeOpen && expanded && !busy && !running && !sending, onDismissRequest = { modeOpen = false },
                                shape = RoundedCornerShape(20.dp), containerColor = palette.card, tonalElevation = 0.dp) {
                                DropdownMenuItem(text = { Text("Agent · 直接分析和执行") }, onClick = { modeOpen = false; onMode("default") })
                                DropdownMenuItem(text = { Text("Plan · 先规划再实施") }, onClick = { modeOpen = false; onMode("plan") })
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        FilledIconButton(onClick = if (running) onStop else onSend,
                            enabled = expanded && !busy && !sending && (running || draft.isNotBlank()),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = accent, contentColor = if (palette.isNight) Color(0xFF0E1928) else Color.White,
                                disabledContainerColor = if (sending) accent else accent.copy(alpha = .16f)),
                            modifier = Modifier.size(48.dp).semantics { contentDescription = if (sending) "正在发送" else if (running) "停止任务" else "发送消息" }) {
                            if (sending) SendingOrbit(if (palette.isNight) Color(0xFF0E1928) else Color.White, motion)
                            else ComposerGlyph(if (running) "stop" else "send", if (palette.isNight) Color(0xFF0E1928) else Color.White, Modifier.size(22.dp))
                        }
                    }
                }
                if (progress < .55f) Box(Modifier.matchParentSize()
                    .graphicsLayer { alpha = (1f - progress / .55f).coerceIn(0f, 1f) }
                    .clickable(enabled = !expanded, role = Role.Button) { onExpanded(true) }
                    .pointerInput(expanded) {
                        if (!expanded) {
                            var drag = 0f
                            detectHorizontalDragGestures(onDragStart = { drag = 0f }, onDragEnd = {
                                if (kotlin.math.abs(drag) > 24.dp.toPx()) onDockRight(drag > 0)
                            }) { change, amount -> change.consume(); drag += amount }
                        }
                    }
                    .semantics {
                        contentDescription = if (draft.isNotBlank()) "展开输入框，草稿已保留" else "展开输入框"
                        stateDescription = if (dockRight) "停靠右侧" else "停靠左侧"
                        customActions = listOf(CustomAccessibilityAction("移到左侧") { onDockRight(false); true }, CustomAccessibilityAction("移到右侧") { onDockRight(true); true })
                    }, contentAlignment = Alignment.Center) {
                    ComposerGlyph("chat", accent, Modifier.size(25.dp))
                    if (draft.isNotBlank() || hasAttention) Box(Modifier.align(Alignment.TopEnd).padding(10.dp).size(7.dp)
                        .background(if (hasAttention) Color(0xFFFF8D45) else accent, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun SendingOrbit(color: Color, motion: Boolean) {
    val orbit = if (motion) rememberInfiniteTransition(label = "sendingOrbit").animateFloat(0f, 360f,
        infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "sendRotation") else null
    Canvas(Modifier.size(27.dp).testTag("send-orbit").processingLight(true).semantics { stateDescription = if (motion) "旋转中" else "正在发送" }) {
        val line = 1.8.dp.toPx()
        drawCircle(color.copy(alpha = .22f), radius = size.minDimension / 2 - line, style = Stroke(line))
        drawArc(color, orbit?.value ?: -90f, 105f, false, Offset(line, line), Size(size.width - line * 2, size.height - line * 2), style = Stroke(line, cap = StrokeCap.Round))
        rotate(orbit?.value ?: 0f) {
            val w = size.width; val h = size.height
            drawLine(color, Offset(w * .5f, h * .7f), Offset(w * .5f, h * .3f), line, StrokeCap.Round)
            drawLine(color, Offset(w * .35f, h * .46f), Offset(w * .5f, h * .3f), line, StrokeCap.Round)
            drawLine(color, Offset(w * .65f, h * .46f), Offset(w * .5f, h * .3f), line, StrokeCap.Round)
        }
    }
}

@Composable
private fun ComposerGlyph(kind: String, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val line = 1.7.dp.toPx()
        fun segment(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(w * x1, h * y1), Offset(w * x2, h * y2), line, StrokeCap.Round)
        when (kind) {
            "stop" -> drawRoundRect(color, Offset(w * .23f, h * .23f), Size(w * .54f, h * .54f), CornerRadius(2.dp.toPx()))
            "send" -> { segment(.5f, .82f, .5f, .18f); segment(.22f, .46f, .5f, .18f); segment(.78f, .46f, .5f, .18f) }
            "left", "right" -> {
                val direction = if (kind == "left") -1 else 1
                segment(.5f - direction * .25f, .25f, .5f + direction * .25f, .75f)
                segment(.5f - direction * .05f, .75f, .5f + direction * .25f, .75f)
                segment(.5f + direction * .25f, .45f, .5f + direction * .25f, .75f)
            }
            else -> {
                val path = Path().apply {
                    moveTo(w * .24f, h * .16f); lineTo(w * .76f, h * .16f)
                    quadraticTo(w * .90f, h * .16f, w * .90f, h * .30f); lineTo(w * .90f, h * .66f)
                    quadraticTo(w * .90f, h * .80f, w * .76f, h * .80f); lineTo(w * .44f, h * .80f)
                    lineTo(w * .22f, h * .94f); lineTo(w * .24f, h * .80f)
                    quadraticTo(w * .10f, h * .80f, w * .10f, h * .66f); lineTo(w * .10f, h * .30f)
                    quadraticTo(w * .10f, h * .16f, w * .24f, h * .16f); close()
                }
                drawPath(path, color, style = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round))
                segment(.30f, .41f, .70f, .41f); segment(.30f, .58f, .58f, .58f)
            }
        }
    }
}
