package dev.codex.remote

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun taskText(item: MessageItem): String = listOf(item.text, if (item.imageCount > 0) "[图片]" else "").filter { it.isNotBlank() }.joinToString("\n").ifBlank { "[无文字内容]" }
internal fun taskHeaderSubtitle(count: Int?, task: TaskReference?): String =
    "${count?.let { "共 $it 轮" } ?: "轮数待升级"} · ${task?.let { draftPreview(taskText(it.item)) } ?: "暂无任务"}" +
        if (task?.pendingSync == true) " · 同步中" else ""
internal fun clockText(at: Long?, full: Boolean = false): String = at?.takeIf { it > 0 }?.let {
    SimpleDateFormat(if (full) "yyyy-MM-dd HH:mm:ss" else "HH:mm:ss", Locale.getDefault()).format(Date(it))
} ?: "未记录"
internal fun elapsedText(ms: Long?): String {
    if (ms == null || ms < 0) return "未记录"
    val seconds = ms / 1000
    return when { seconds >= 3600 -> "${seconds / 3600}时${seconds % 3600 / 60}分${seconds % 60}秒"
        seconds >= 60 -> "${seconds / 60}分${seconds % 60}秒"; else -> "${seconds}秒" }
}

internal fun dateText(at: Long?): String = clockText(at, true).substringBefore(' ')

@Composable internal fun ContentTime(item: MessageItem, running: Boolean = false) {
    val p = LocalAppPalette.current
    Text(if (item.timestampMs == null) "时间未记录" else clockText(item.timestampMs),
        Modifier.padding(start = 6.dp).testTag("content-time-${item.id}").processingLight(running && !item.turnCompleted),
        color = zebraSecondary(LocalChatAppearance.current.zebra, p.isNight, p.muted), fontSize = 10.sp, maxLines = 1)
}

@Composable internal fun TurnTime(item: MessageItem, running: Boolean = false, detail: Boolean = false,
    color: androidx.compose.ui.graphics.Color? = null, readerLayout: Boolean = false) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> foreground = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
    }
    var now by remember(item.id) { mutableLongStateOf(System.currentTimeMillis()) }
    val active = running && !item.turnCompleted
    LaunchedEffect(active, foreground) { while (active && foreground) { now = System.currentTimeMillis(); delay(1000) } }
    val duration = if (active && item.turnStartedAtMs != null) (now - item.turnStartedAtMs).coerceAtLeast(0) else item.turnDurationMs
    val label = if (detail) (if (active) "执行中" else if (!item.turnCompleted) "尚未结束" else "结束 ${clockText(item.turnEndedAtMs, true)}") + " · 用时 ${elapsedText(duration)}"
        else (if (item.roundNumber > 0) "第 ${item.roundNumber} 轮 · " else "本轮 · ") + "用时 ${elapsedText(duration)}" + if (active) " · 执行中" else ""
    val p = LocalAppPalette.current
    val ink = color ?: zebraSecondary(LocalChatAppearance.current.zebra, p.isNight, p.muted)
    if (readerLayout) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).testTag("task-timing-${item.id}"),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Text("用时 ${elapsedText(duration)}", Modifier.weight(1f).testTag("task-duration-${item.id}").processingLight(active),
                color = ink, fontSize = 11.sp, lineHeight = 16.sp)
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(if (active) "执行中" else if (!item.turnCompleted) "尚未结束" else "结束 ${clockText(item.turnEndedAtMs)}",
                    Modifier.fillMaxWidth().testTag("task-ended-${item.id}").processingLight(active),
                    color = ink, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.End)
                if (item.turnCompleted && item.turnEndedAtMs != null && dateText(item.turnEndedAtMs) != dateText(item.timestampMs)) {
                    Text(dateText(item.turnEndedAtMs), Modifier.testTag("task-end-date-${item.id}"), color = ink,
                        fontSize = 10.sp, lineHeight = 15.sp, textAlign = TextAlign.End)
                }
            }
        }
        return
    }
    Text(label, Modifier.fillMaxWidth().padding(horizontal = if (detail) 0.dp else 16.dp, vertical = 6.dp)
        .testTag("turn-time-${item.id}").processingLight(active), color = color ?: zebraSecondary(LocalChatAppearance.current.zebra, p.isNight, p.muted), fontSize = 11.sp)
}
