package dev.codex.remote

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pure gesture gate: one release can request at most one page. No automatic cascading loads. */
internal class HistoryPullGate {
    var distance by mutableFloatStateOf(0f); private set
    var dragging by mutableStateOf(false); private set
    fun pull(delta: Float, threshold: Float): Float {
        if (delta <= 0) return 0f
        dragging = true
        distance = (distance + delta * .5f).coerceAtMost(threshold * 1.65f)
        return delta
    }
    fun retract(delta: Float): Float {
        if (delta >= 0 || distance <= 0) return 0f
        val consumed = delta.coerceAtLeast(-distance * 2f)
        distance = (distance + consumed * .5f).coerceAtLeast(0f)
        return consumed
    }
    fun release(threshold: Float): Boolean {
        val armed = dragging && distance >= threshold
        cancel()
        return armed
    }
    fun cancel() { distance = 0f; dragging = false }
}

internal fun olderArrivalIds(previous: List<MessageItem>, next: List<MessageItem>): Set<String> {
    val first = previous.firstOrNull()?.id ?: return emptySet()
    val index = next.indexOfFirst { it.id == first }
    if (index < 0) return emptySet() // A reset is not a historical prepend.
    val known = previous.map { it.id }.toSet()
    return next.take(index).map { it.id }.filterNot { it in known }.toSet()
}

@Composable
internal fun HistoryArrival(reveal: Boolean, content: @Composable () -> Unit) {
    val motion = rememberChatMotion()
    val phase = remember { Animatable(if (reveal && motion) 0f else 1f) }
    LaunchedEffect(motion) {
        if (motion) phase.animateTo(1f, tween(380)) else phase.snapTo(1f)
    }
    Box(Modifier.graphicsLayer {
        alpha = phase.value
        translationY = -6.dp.toPx() * (1f - phase.value)
    }) { content() }
}

@Composable
internal fun HistoryPullIndicator(distance: Float, threshold: Float, dragging: Boolean, loading: Boolean,
    canLoad: Boolean, error: String, reset: Boolean, backdrop: GraphicsLayer, backdropOrigin: Offset,
    modifier: Modifier = Modifier) {
    val motion = rememberChatMotion()
    val visible = loading || distance > 0f || error.isNotBlank() || reset
    val alpha by animateFloatAsState(if (visible) 1f else 0f, tween(if (motion) 180 else 0), label = "history-indicator")
    val label = when {
        loading -> "正在加载更早消息"
        error.isNotBlank() && !dragging -> "加载失败，下拉重试"
        reset && !dragging -> "历史窗口已更新，可下拉继续"
        !canLoad -> "已到最早消息"
        distance >= threshold -> "松开加载更早消息"
        else -> "继续下拉加载历史"
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    if (alpha > 0f) Box(modifier.graphicsLayer { this.alpha = alpha }
        .testTag("history-pull-indicator")
        .semantics { contentDescription = label; stateDescription = if (reset) "同步窗口已更新，按需获取历史" else label; liveRegion = LiveRegionMode.Polite }
        .clip(CircleShape).border(.5.dp, HapiPalette.muted.copy(alpha = .2f), CircleShape)
        .onGloballyPositioned { origin = it.positionInRoot() - backdropOrigin }) {
        GlassMaterial(backdrop, origin, Modifier.matchParentSize())
        Row(Modifier.height(36.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading && motion) CircularProgressIndicator(Modifier.size(14.dp).processingLight(true), strokeWidth = 1.5.dp, color = HapiPalette.ink)
            else Text(if (distance >= threshold) "↑" else "↓", fontSize = 14.sp, color = HapiPalette.muted)
            Text(label, Modifier.testTag("history-loading-label").processingLight(loading), fontSize = 11.sp, color = HapiPalette.ink)
        }
    }
}
