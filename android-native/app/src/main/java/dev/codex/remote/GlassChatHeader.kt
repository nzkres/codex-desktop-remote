package dev.codex.remote

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Overlays the transcript instead of resizing its viewport on every interaction. */
@Composable
internal fun GlassChatHeader(title: String, expanded: Boolean, night: Boolean, onExpand: () -> Unit,
    onBack: () -> Unit, onToggleNight: () -> Unit, backdrop: GraphicsLayer, backdropOrigin: Offset,
    modifier: Modifier = Modifier, activity: HeaderActivity = HeaderActivity("等待开始"),
    taskSubtitle: String? = null, onTaskClick: () -> Unit = {}) {
    val palette = LocalAppPalette.current
    val motion = rememberChatMotion()
    val density = LocalDensity.current
    val fontScale = density.fontScale.coerceAtLeast(1f)
    val compactHeight = (30.dp * fontScale + 16.dp).coerceAtLeast(52.dp)
    val expandedHeight = (35.dp * fontScale + 20.dp).coerceAtLeast(56.dp)
    val textStyle = androidx.compose.material3.LocalTextStyle.current.copy(platformStyle = PlatformTextStyle(includeFontPadding = false))
    val taskLink = if (taskSubtitle != null) Modifier.heightIn(min = 48.dp).testTag("task-recall-strip")
        .clickable(role = Role.Button, onClickLabel = "查看任务详情", onClick = onTaskClick) else Modifier
    val progress by animateFloatAsState(if (expanded) 1f else 0f,
        tween(if (motion) 360 else 0, easing = CubicBezierEasing(.22f, .8f, .2f, 1f)), label = "header-morph")
    var origin by remember { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 12.dp).height(expandedHeight), contentAlignment = Alignment.Center) {
        val fullWidth = maxWidth
        val width = androidx.compose.ui.unit.lerp(fullWidth.coerceAtMost(252.dp), fullWidth, progress)
        val height = androidx.compose.ui.unit.lerp(compactHeight, expandedHeight, progress)
        val shape = RoundedCornerShape(24.dp)
        // Two small lines, but still an accessible hit area and a stable overlay container.
        Box(Modifier.width(width).height(expandedHeight)
            .testTag("chat-header")
            .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
            .then(if (!expanded) Modifier.clickable(role = Role.Button, onClickLabel = "展开对话标题", onClick = onExpand) else Modifier),
            contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(height).onGloballyPositioned { origin = it.positionInRoot() - backdropOrigin }
                .testTag("chat-header-glass").shadow(5.dp, shape).clip(shape)
                .border(.5.dp, palette.muted.copy(alpha = .19f), shape)) {
                GlassMaterial(backdrop, origin, Modifier.matchParentSize())
            }
            if (progress < 1f) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                .graphicsLayer { alpha = (1f - progress * 2f).coerceIn(0f, 1f) }
                .then(if (expanded) Modifier.clearAndSetSemantics { } else Modifier),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).then(taskLink), verticalArrangement = Arrangement.Center) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = palette.ink, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, style = textStyle)
                    Text(taskSubtitle ?: activity.subtitle, Modifier.testTag("header-subtitle").processingLight(taskSubtitle == null && activity.running), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = palette.muted, fontSize = 10.sp, lineHeight = 14.sp, style = textStyle,
                        textDecoration = if (taskSubtitle != null) TextDecoration.Underline else null)
                }
                Spacer(Modifier.width(8.dp))
                HeaderLoading(activity.running)
            }
            if (progress > 0f) Row(Modifier.requiredWidth(fullWidth).height(expandedHeight)
                .graphicsLayer { alpha = ((progress - .45f) / .55f).coerceIn(0f, 1f) }
                .then(if (!expanded) Modifier.clearAndSetSemantics { } else Modifier),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, enabled = expanded, modifier = Modifier.size(48.dp).testTag("header-back").semantics { contentDescription = "返回任务列表" }) {
                    // Geometry, not a font baseline: the stroke remains centered at every font scale.
                    Canvas(Modifier.size(24.dp).testTag("header-back-glyph")) {
                        drawPath(Path().apply {
                            moveTo(size.width * .62f, size.height * .24f)
                            lineTo(size.width * .36f, size.height * .50f)
                            lineTo(size.width * .62f, size.height * .76f)
                        }, palette.ink, style = Stroke(1.9.dp.toPx(), cap = StrokeCap.Round))
                    }
                }
                Column(Modifier.weight(1f).then(taskLink), verticalArrangement = Arrangement.Center) {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = palette.ink, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, style = textStyle)
                    Text(taskSubtitle ?: activity.subtitle, Modifier.testTag("header-subtitle").processingLight(taskSubtitle == null && activity.running), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = palette.muted, fontSize = 11.sp, lineHeight = 15.sp, style = textStyle,
                        textDecoration = if (taskSubtitle != null) TextDecoration.Underline else null)
                }
                Spacer(Modifier.width(8.dp))
                HeaderLoading(activity.running)
                IconButton(onClick = onToggleNight, enabled = expanded, modifier = Modifier.size(48.dp).semantics {
                    contentDescription = if (night) "切换浅色背景" else "切换深夜背景"
                }) { Text(if (night) "☼" else "☾", fontSize = 20.sp, color = palette.muted) }
            }
        }
    }
}

@Composable
internal fun HeaderLoading(running: Boolean) {
    val moving = running && rememberChatMotion()
    val angle = if (moving) {
        val transition = rememberInfiniteTransition(label = "header-loading")
        transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "header-orbit").value
    } else 0f
    val color = LocalAppPalette.current.ink
    Canvas(Modifier.size(14.dp).testTag("header-loading").processingLight(running).semantics {
        contentDescription = if (running) "任务执行中" else "任务未在执行"
        stateDescription = if (moving) "转动" else "静止"
    }) {
        val stroke = 1.5.dp.toPx()
        drawCircle(color.copy(alpha = .24f), radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
        if (running) drawArc(color, angle, 105f, false, topLeft = Offset(stroke, stroke),
            size = androidx.compose.ui.geometry.Size(size.width - stroke * 2, size.height - stroke * 2),
            style = Stroke(stroke, cap = StrokeCap.Round))
        else drawCircle(color.copy(alpha = .5f), radius = 1.5.dp.toPx())
    }
}
