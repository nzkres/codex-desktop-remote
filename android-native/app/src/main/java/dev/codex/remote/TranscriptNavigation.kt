package dev.codex.remote

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Reach the actual end, including a final message taller than one screen and the composer inset. */
internal suspend fun LazyListState.reachTranscriptEnd(animated: Boolean) {
    val last = layoutInfo.totalItemsCount - 1
    if (last < 0) return
    if (layoutInfo.visibleItemsInfo.none { it.index == last }) {
        if (animated) animateScrollToItem(last) else scrollToItem(last)
        withFrameNanos { }
    }
    val info = layoutInfo
    val item = info.visibleItemsInfo.find { it.index == last } ?: return
    val distance = (item.offset + item.size - (info.viewportEndOffset - info.afterContentPadding)).toFloat()
    if (animated) animateScrollBy(distance, tween(320)) else scrollBy(distance)
}

/** One combined status + navigation control avoids two competing floating buttons. */
@Composable
internal fun TranscriptNavigation(visible: Boolean, status: TranscriptActivity?, expanded: Boolean,
    composerHeight: Dp, backdrop: GraphicsLayer, backdropOrigin: Offset, onClick: () -> Unit,
    modifier: Modifier = Modifier) {
    val motion = rememberChatMotion()
    val palette = LocalAppPalette.current
    val progress by animateFloatAsState(if (expanded) 1f else 0f, tween(if (motion) 460 else 0), label = "navigation-dock")
    var origin by remember { mutableStateOf(Offset.Zero) }
    val label = if (status?.animated == true) "正在执行" else status?.label ?: "回到最新"
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.BottomEnd) {
        val shellHeight = composerHeight.coerceAtLeast(60.dp)
        // Fade through the composer morph, then settle above it; never cover its send/stop control.
        val alpha = when { progress < .12f -> 1f - progress / .12f; progress > .88f -> (progress - .88f) / .12f; else -> 0f }
        val bottom = 12.dp + (shellHeight + 10.dp) * progress
        AnimatedVisibility(visible, Modifier.padding(bottom = bottom, end = 18.dp),
            enter = fadeIn(tween(if (motion) 180 else 0)) + slideInVertically(tween(if (motion) 260 else 0)) { it / 4 },
            exit = fadeOut(tween(if (motion) 150 else 0)) + slideOutVertically(tween(if (motion) 180 else 0)) { it / 5 }) {
            Box(Modifier.size(48.dp)
                .graphicsLayer { this.alpha = alpha }
                .testTag("transcript-navigation")
                .shadow(8.dp, CircleShape).clip(CircleShape)
                .border(.5.dp, palette.muted.copy(alpha = .24f), CircleShape)
                .onGloballyPositioned { origin = it.positionInRoot() - backdropOrigin }
                .semantics { contentDescription = "$label，回到最新消息"; stateDescription = status?.label ?: "正在查看历史消息" }
                .clickable(enabled = alpha > .95f, role = Role.Button, onClickLabel = "回到最新消息", onClick = onClick)) {
                GlassMaterial(backdrop, origin, Modifier.matchParentSize())
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ComposerGlyph("down", palette.ink, Modifier.size(20.dp).testTag("navigation-status").processingLight(status?.animated == true))
                }
            }
        }
    }
}
