package dev.codex.remote

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal data class TaskReference(val item: MessageItem, val pendingSync: Boolean = false)

internal fun confirmedTaskReceipt(receipt: SendPresentation, conversation: Conversation?): MessageItem? {
    confirmedSentItem(receipt, conversation?.turns.orEmpty())?.let { return it }
    val latest = conversation?.latestUserMessage ?: return null
    if (receipt.confirmedItemId != null) return latest.item.takeIf { it.id == receipt.confirmedItemId }
    return latest.item.takeIf { latest.key !in receipt.previousUserIds &&
        (if (!receipt.turnId.isNullOrBlank()) latest.turnId == receipt.turnId
        else !it.truncated && it.text.trim() == receipt.text.trim()) }
}

internal fun latestTask(conversation: Conversation?, receipt: SendPresentation?): TaskReference? {
    val authoritative = conversation?.latestUserMessage
    if (receipt != null && receipt.confirmedItemId == null &&
        confirmedTaskReceipt(receipt, conversation) == null) {
        return TaskReference(MessageItem(receipt.key, "userMessage", receipt.text, "", "", "", "", null, emptyList()), true)
    }
    if (conversation?.taskReferenceSupported == true) return authoritative?.item?.let { TaskReference(it) }
    return conversation?.turns?.flatMap { it.items }?.lastOrNull { it.type == "userMessage" }?.let { TaskReference(it) }
}

internal enum class TaskSearchResult { Found, More, Missing }

/** Explicit user action only; never drain the entire history in a background loop. */
internal suspend fun findTaskPage(targetId: String, current: () -> Conversation?, loadOlder: suspend () -> Conversation?): TaskSearchResult {
    var snapshot = current()
    repeat(4) { attempt ->
        currentCoroutineContext().ensureActive()
        if (snapshot?.turns?.any { turn -> turn.items.any { it.id == targetId } } == true) return TaskSearchResult.Found
        if (snapshot?.hasOlder != true) return TaskSearchResult.Missing
        if (attempt == 3) return TaskSearchResult.More
        snapshot = loadOlder() ?: error("没有收到历史消息，请重试")
        if (snapshot?.historyReset == true) error("历史窗口已更新，请重试定位")
    }
    return TaskSearchResult.More
}

@Composable
internal fun TaskRecallStrip(reference: TaskReference?, visible: Boolean, legacy: Boolean, backdrop: GraphicsLayer,
    backdropOrigin: Offset, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val palette = LocalAppPalette.current
    val motion = rememberChatMotion()
    var origin by remember { mutableStateOf(Offset.Zero) }
    AnimatedVisibility(visible, modifier,
        enter = fadeIn(tween(if (motion) 240 else 0)) + slideInVertically(tween(if (motion) 240 else 0)) { -it / 6 },
        exit = fadeOut(tween(if (motion) 180 else 0))) {
        Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("task-recall-strip")
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "查看本次任务" }, contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().heightIn(min = 36.dp).clip(RoundedCornerShape(18.dp))
                .onGloballyPositioned { origin = it.positionInRoot() }) {
                GlassMaterial(backdrop, origin - backdropOrigin, Modifier.matchParentSize())
                Text(when {
                    reference?.pendingSync == true -> "本次任务 · ${draftPreview(reference.item.text)} · 同步中"
                    reference != null -> "${if (legacy) "已加载任务" else "本次任务"} · ${draftPreview(reference.item.text)}"
                    else -> "任务回看 · 请升级电脑 Gateway"
                }, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = palette.ink,
                    fontSize = 12.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
internal fun TaskRecallDialog(initial: TaskReference?, previous: List<MessageItem>, legacy: Boolean,
    loadingHistory: Boolean, searchMessage: String, onDismiss: () -> Unit,
    onLoad: suspend (MessageItem) -> MessageItem, onJump: (TaskReference) -> Unit, onCancel: () -> Unit,
    onLoadPage: suspend (String?) -> TaskHistoryPage? = { null }, runningRound: Int? = null,
    liveTasks: List<MessageItem> = emptyList()) {
    val palette = LocalAppPalette.current
    val secondary = palette.ink.copy(alpha = .74f)
    val readerDensity = LocalDensity.current
    val motion = rememberChatMotion()
    var selection by remember { mutableStateOf(initial) }
    var full by remember(selection?.item?.id) { mutableStateOf(selection?.item) }
    var error by remember(selection?.item?.id) { mutableStateOf("") }
    var loading by remember(selection?.item?.id) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var tasks by remember { mutableStateOf((listOfNotNull(initial?.item) + previous).distinctBy { it.id }) }
    var nextBefore by remember { mutableStateOf<String?>(null) }
    var pageBusy by remember { mutableStateOf(false) }
    var pageError by remember { mutableStateOf("") }
    var firstPage by remember { mutableStateOf(true) }
    var total by remember { mutableStateOf<Int?>(null) }
    val panelScroll = rememberScrollState()
    fun timing(item: MessageItem): MessageItem {
        val live = liveTasks.lastOrNull { it.id == item.id || item.roundNumber > 0 && it.roundNumber == item.roundNumber } ?: return item
        return item.copy(turnStartedAtMs = live.turnStartedAtMs, turnEndedAtMs = live.turnEndedAtMs,
            turnDurationMs = live.turnDurationMs, turnCompleted = live.turnCompleted)
    }
    suspend fun loadPage() {
        if (pageBusy) return
        pageBusy = true; pageError = ""
        try {
            val page = onLoadPage(if (firstPage) null else nextBefore)
            if (page != null) {
                tasks = (if (firstPage) page.items + listOfNotNull(selection?.item) else tasks + page.items).distinctBy { it.id }
                nextBefore = page.nextBefore; total = page.total; firstPage = false
            }
        } catch (exception: CancellationException) { throw exception }
        catch (_: Exception) { pageError = "任务目录加载失败；可重试，旧 Gateway 请升级。" }
        finally { pageBusy = false }
    }
    LaunchedEffect(Unit) { loadPage() }
    LaunchedEffect(selection?.item?.id, retry) {
        val item = selection?.item ?: return@LaunchedEffect
        if (!item.truncated) return@LaunchedEffect
        loading = true; error = ""
        try {
            val result = onLoad(item)
            require(result.id == item.id && !result.truncated) { "未能读取完整任务，请重试" }
            full = result
        } catch (exception: CancellationException) { throw exception }
        catch (_: Exception) { error = "完整任务读取失败，请重试" }
        finally { loading = false }
    }
    // A reading surface, not an AlertDialog: fixed chrome never overlays the scroll viewport.
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Dialog owns a separate window. Preserve the caller's accessibility font scale.
        CompositionLocalProvider(LocalDensity provides readerDensity) {
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 10.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 640.dp).fillMaxWidth().fillMaxHeight(.94f).testTag("task-detail-surface"),
                shape = RoundedCornerShape(24.dp), color = palette.card, contentColor = palette.ink,
                border = BorderStroke(1.dp, palette.divider)) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("任务详情", fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
                            Text("最新在上 · 向下查看更早任务${total?.let { " · 共 $it 轮" }.orEmpty()}",
                                color = secondary, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                        TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("关闭", color = palette.ink, fontSize = 12.sp)
                        }
                    }
                    HorizontalDivider(color = palette.divider)
                    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(panelScroll).testTag("task-recall-panel")) {
                        if (legacy) Text("旧 Gateway 无法确认窗口外的最新任务；当前仅展示已加载记录，请升级。",
                            Modifier.padding(16.dp), color = palette.ink, fontSize = 12.sp)
                        tasks.forEachIndexed { index, entry ->
                            key(entry.id) {
                                val selected = selection?.item?.id == entry.id
                                val item = if (selected) full ?: entry else entry
                                val recorded = timing(item)
                                Column(Modifier.fillMaxWidth()
                                    .background(if (selected) palette.ink.copy(alpha = .035f) else androidx.compose.ui.graphics.Color.Transparent)
                                    .animateContentSize(tween(if (motion) 240 else 0))) {
                                    Column(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .testTag("task-entry-${entry.id}")
                                        .clickable(enabled = !loadingHistory, role = Role.Button) {
                                            selection = TaskReference(entry, initial?.pendingSync == true && initial.item.id == entry.id)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 10.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.Top) {
                                            Text("${if (index == 0 && !legacy) "最新 · " else ""}${if (item.roundNumber > 0) "第 ${item.roundNumber} 轮" else "你的任务"}",
                                                Modifier.weight(1f).testTag("task-round-${item.id}"), color = secondary, fontSize = 11.sp, lineHeight = 16.sp)
                                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                                Text("发送 ${clockText(item.timestampMs)}", Modifier.fillMaxWidth().testTag("task-sent-${item.id}"),
                                                    color = secondary, fontSize = 11.sp, lineHeight = 16.sp, textAlign = TextAlign.End)
                                                if (item.timestampMs != null) Text(dateText(item.timestampMs), color = secondary,
                                                    fontSize = 10.sp, lineHeight = 15.sp, textAlign = TextAlign.End)
                                            }
                                        }
                                        if (!selected) Spacer(Modifier.height(8.dp))
                                        if (!selected) Text(taskText(item), color = palette.ink, fontSize = 14.sp,
                                            lineHeight = 20.sp, fontWeight = FontWeight.Normal, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (selected) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                                        Text(taskText(item), color = palette.ink, fontSize = 14.sp, lineHeight = 21.sp,
                                            modifier = Modifier.testTag("task-recall-original"))
                                        if (loading) Text("正在读取完整原文…", Modifier.processingLight(true), color = palette.ink, fontSize = 12.sp)
                                        if (error.isNotBlank()) TextButton(onClick = { retry++ }) { Text(error, color = palette.errorInk) }
                                        ContentCopyButton(item, onLoad, enabled = !loading && !item.truncated, color = palette.ink)
                                    }
                                    Box(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                                        TurnTime(recorded, running = runningRound != null && item.roundNumber == runningRound,
                                            detail = true, color = secondary, readerLayout = true)
                                    }
                                }
                                HorizontalDivider(color = palette.divider)
                            }
                        }
                        if (pageBusy) Text("正在加载任务目录…", Modifier.padding(16.dp).processingLight(true), color = palette.ink, fontSize = 12.sp)
                        if (pageError.isNotBlank()) TextButton(onClick = { scope.launch { loadPage() } }, enabled = !pageBusy) { Text(pageError, color = palette.errorInk) }
                        else if (nextBefore != null) TextButton(onClick = { scope.launch { loadPage() } }, enabled = !pageBusy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("加载更早任务", color = palette.ink, fontSize = 12.sp) }
                        Spacer(Modifier.height(16.dp))
                    }
                    HorizontalDivider(color = palette.divider)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("task-detail-actions")) {
                        selection?.item?.roundNumber?.takeIf { it > 0 }?.let {
                            Text("已选第 $it 轮", color = secondary, fontSize = 10.sp, lineHeight = 14.sp)
                        }
                        if (searchMessage.isNotBlank()) Text(searchMessage, color = palette.ink, fontSize = 11.sp, lineHeight = 16.sp)
                        if (loadingHistory) TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消查找", color = palette.ink) }
                        else if (selection != null) TextButton(onClick = { onJump(selection!!) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = selection?.pendingSync != true) {
                            Text(if (searchMessage.startsWith("尚未")) "继续查找" else "跳到这条消息", color = palette.ink, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        }
    }
}
