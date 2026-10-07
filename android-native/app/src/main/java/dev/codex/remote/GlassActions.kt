package dev.codex.remote

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared glass action language; only floating controls with an actual backdrop blur content. */
@Composable
internal fun GlassActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    icon: String = "agent", enabled: Boolean = true, selected: Boolean = false, busy: Boolean = false,
    chevron: Boolean = false, menuOpen: Boolean = false, backdrop: GraphicsLayer? = null, backdropOrigin: Offset = Offset.Zero,
    compact: Boolean = false) {
    val palette = LocalAppPalette.current
    val accent = conversationColors(LocalChatAppearance.current.style, palette.isNight).accent
    val motion = rememberChatMotion()
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled && !busy) .965f else 1f,
        tween(if (motion) if (pressed) 140 else 240 else 0, easing = TranscriptEase), label = "glass-action-press")
    val angle by animateFloatAsState(if (menuOpen) 180f else 0f, tween(if (motion) 240 else 0), label = "action-chevron")
    var origin by remember { mutableStateOf(Offset.Zero) }
    val color = if (enabled || busy) accent else palette.muted
    val shape = RoundedCornerShape(22.dp)
    Box(modifier.heightIn(min = 48.dp)
        .clickable(enabled = enabled && !busy, interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label; if (selected) this.selected = true; if (busy) stateDescription = "正在处理" },
        contentAlignment = Alignment.Center, propagateMinConstraints = !compact) {
    Box(Modifier.heightIn(min = if (compact) 36.dp else 48.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        // A native elevation shadow leaks through a translucent fill, creating a dark slab
        // and an inner highlight seam. Thin actions use tint and outline, not cast shadows.
        .clip(shape)
        .onGloballyPositioned { origin = it.positionInRoot() - backdropOrigin }
        .background(Brush.verticalGradient(listOf(
            (if (palette.isNight) Color.White else accent).copy(alpha = if (selected) .15f else .065f),
            accent.copy(alpha = if (selected) .10f else .025f))))
        .border(if (selected) 1.dp else .6.dp, (if (selected) accent else palette.muted).copy(alpha = if (selected) .48f else .24f), shape)
        .testTag(if (compact) "compact-action-surface" else "action-surface"),
        contentAlignment = Alignment.Center) {
        if (backdrop != null) GlassMaterial(backdrop, origin, Modifier.matchParentSize())
        Row(Modifier.padding(horizontal = if (compact) 10.dp else 13.dp, vertical = if (compact) 4.dp else 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 5.dp else 7.dp)) {
            ActionGlyph(icon, color, Modifier.size(17.dp).processingLight(busy))
            Text(label, Modifier.processingLight(busy), color = color, fontSize = 12.sp,
                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (chevron) ActionGlyph("down", color, Modifier.size(12.dp).graphicsLayer { rotationZ = angle })
        }
    }
    }
}

@Composable
internal fun ActionGlyph(kind: String, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = 1.6.dp.toPx()
        fun line(x: Float, y: Float, x2: Float, y2: Float) = drawLine(color, Offset(w * x, h * y), Offset(w * x2, h * y2), stroke, StrokeCap.Round)
        when (kind) {
            "plus" -> { line(.5f, .18f, .5f, .82f); line(.18f, .5f, .82f, .5f) }
            "down" -> { line(.22f, .36f, .5f, .64f); line(.5f, .64f, .78f, .36f) }
            "draft" -> { line(.24f, .74f, .76f, .22f); line(.24f, .74f, .2f, .82f); line(.2f, .82f, .8f, .82f) }
            "plan" -> { for (y in listOf(.25f, .5f, .75f)) { line(.12f, y, .2f, y); line(.38f, y, .86f, y) } }
            else -> {
                val path = Path().apply { moveTo(w * .5f, h * .12f); lineTo(w * .66f, h * .38f); lineTo(w * .88f, h * .5f)
                    lineTo(w * .66f, h * .64f); lineTo(w * .5f, h * .88f); lineTo(w * .35f, h * .64f)
                    lineTo(w * .12f, h * .5f); lineTo(w * .35f, h * .38f); close() }
                drawPath(path, color, style = Stroke(stroke, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
internal fun DraftLine(text: String, modifier: Modifier = Modifier) {
    if (text.isBlank()) return
    val palette = LocalAppPalette.current
    val accent = conversationColors(LocalChatAppearance.current.style, palette.isNight).accent
    Row(modifier.testTag("draft-preview"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("草稿", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(draftPreview(text), color = palette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun DraftResumeBar(title: String, text: String, onClick: () -> Unit) {
    val palette = LocalAppPalette.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
        .testTag("draft-resume").heightIn(min = 48.dp).clip(RoundedCornerShape(18.dp))
        .background(palette.muted.copy(alpha = .065f)).border(.5.dp, palette.muted.copy(alpha = .20f), RoundedCornerShape(18.dp))
        .clickable(role = Role.Button, onClickLabel = "继续编辑草稿", onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionGlyph("draft", palette.ink, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("草稿 · $title：${draftPreview(text)}", Modifier.weight(1f), color = palette.ink,
            fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(8.dp))
        Text("继续写", color = palette.muted, fontSize = 11.sp)
    }
}
