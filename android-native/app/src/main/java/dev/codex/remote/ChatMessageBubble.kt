package dev.codex.remote

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ChatMessageBubble(item: MessageItem, receiptKey: String? = null, awaitingSync: Boolean = false,
    onLoadFull: suspend (MessageItem) -> MessageItem = { it }, loadingFull: Boolean = false,
    claimReveal: () -> String? = { null }, reveal: ReplyReveal? = null, copyItem: MessageItem = item, stripe: Int = 0,
    highlighted: Boolean = false, processing: Boolean = false) {
    val mine = item.type == "userMessage"
    val palette = LocalAppPalette.current
    val colors = conversationColors(LocalChatAppearance.current.style, palette.isNight)
    val muted = zebraSecondary(LocalChatAppearance.current.zebra, palette.isNight, palette.muted)
    val motion = rememberChatMotion()
    val fill = remember(receiptKey) { Animatable(1f) }
    LaunchedEffect(receiptKey, motion) {
        if (receiptKey != null && motion) {
            fill.snapTo(0f)
            fill.animateTo(1f, tween(1200, delayMillis = 160, easing = CubicBezierEasing(.2f, .65f, .3f, 1f)))
        } else fill.snapTo(1f)
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        Column(Modifier.fillMaxWidth()
            .testTag(if (receiptKey != null) "sent-message" else "message-${item.id}")
            .semantics { stateDescription = if (mine) if (awaitingSync) "已发送，等待同步" else "你的消息" else "Codex 的回复" }
            .background(messageSurface(LocalChatAppearance.current, mine, stripe, palette.isNight))
            .drawWithContent {
                // A single, restrained left-to-right glass wash. Not a looping fake progress bar.
                val phase = fill.value
                if (receiptKey != null && phase < 1f) {
                    val edge = size.width * phase
                    val alpha = if (phase < .72f) 1f else ((1f - phase) / .28f).coerceIn(0f, 1f)
                    drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = .05f * alpha), Color.White.copy(alpha = .28f * alpha)),
                        Offset.Zero, Offset(edge.coerceAtLeast(1f), 0f)), size = Size(edge, size.height))
                }
                drawContent()
                if (mine) drawRect(colors.accent, size = Size(2.dp.toPx(), size.height))
                if (highlighted) drawRect(colors.accent.copy(alpha = .12f))
            }.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth().testTag("message-meta-${item.id}"), verticalAlignment = Alignment.CenterVertically) {
                Text(if (mine) "› 你的任务" else if (item.type == "plan") "✦ Codex · Plan" else "✦ Codex",
                    fontSize = 10.sp, fontWeight = FontWeight.Medium, color = if (mine) colors.accent else muted)
                ContentTime(item, processing && !item.turnCompleted)
                Spacer(Modifier.weight(1f))
                if (receiptKey != null) {
                    Text(if (awaitingSync) "已发送 · 同步中" else "已发送 ✓", Modifier.processingLight(awaitingSync), color = muted, fontSize = 10.sp)
                }
                ContentCopyButton(copyItem, onLoadFull, enabled = !loadingFull, color = muted, compact = true)
            }
            Spacer(Modifier.height(8.dp)) // Space for the copy control's expanded touch target.
            if (mine) UserMessageText(taskText(item), item.id) else NativeMarkdown(item.text, claimReveal, reveal)
        }
    }
}
