package dev.codex.remote

import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

private val TextInk: Color @Composable get() = HapiPalette.ink
private val TextMuted: Color @Composable get() = HapiPalette.muted
private val CardBg: Color @Composable get() = HapiPalette.card
private val Green: Color @Composable get() = HapiPalette.green

@Composable
fun ChatScreen(
    threadId: String,
    session: Session?,
    projectName: String,
    conversation: Conversation?,
    loadError: String,
    busy: Boolean,
    night: Boolean,
    onToggleNight: () -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSend: suspend (String) -> String?,
    onStop: () -> Unit,
    onMode: (String) -> Unit,
    onImplement: () -> Unit,
    onApproval: (Approval, Boolean) -> Unit,
    onAnswer: (QuestionRequest, Map<String, String>) -> Unit,
    onRefreshQuestions: () -> Unit = {},
    questionDraftStore: QuestionDraftStore? = null,
    questionStorageAvailable: Boolean = true,
    onLoadOlder: suspend () -> Conversation? = { null },
    onLoadFull: suspend (MessageItem) -> MessageItem = { it },
    draftStore: DraftStore? = null,
    onLoadTask: suspend (MessageItem) -> MessageItem = onLoadFull,
    onLoadTaskPage: suspend (String?) -> TaskHistoryPage? = { null },
    terminalName: String = "",
    desktopOpening: Boolean = false,
) {
    val activity = LocalActivity.current as ComponentActivity
    val scope = remember(activity) { activity.lifecycleScope }
    val listState = remember(threadId) { LazyListState() }
    val planFocus = remember { FocusRequester() }
    val composerFocus = remember { FocusRequester() }
    val messages = remember(conversation) { conversation?.turns?.flatMap { turn ->
        turn.items.filter { it.type in setOf("userMessage", "agentMessage", "plan", "commandExecution", "fileChange") }
    } ?: emptyList() }
    val activityStatus = transcriptActivity(session, conversation)
    val showPlanChoice = session != null && session.mode == "plan" && session.status == "COMPLETED" &&
        (if (conversation?.session != null) conversation.latestHasPlan else messages.any { it.type == "plan" })
    var loadingHistory by remember(threadId) { mutableStateOf(false) }
    var historyError by remember(threadId) { mutableStateOf("") }
    var loadingFull by remember(threadId) { mutableStateOf(emptySet<String>()) }
    val historyPull = remember(threadId) { HistoryPullGate() }
    val historyArrivals = remember(threadId) { mutableSetOf<String>() }
    var headerExpanded by remember(threadId) { mutableStateOf(false) }
    var headerTouching by remember(threadId) { mutableStateOf(false) }
    var headerInteraction by remember(threadId) { mutableIntStateOf(0) }
    var transientDraft by rememberSaveable(threadId) { mutableStateOf("") }
    val draft = if (draftStore != null) draftStore.read(threadId)?.text.orEmpty() else transientDraft
    fun editDraft(text: String) { if (draftStore != null) draftStore.write(threadId, text) else transientDraft = text }
    var sending by remember(threadId) { mutableStateOf(false) }
    var sendError by remember(threadId) { mutableStateOf("") }
    var receipt by remember(threadId) { mutableStateOf<SendPresentation?>(null) }
    val confirmedItem = receipt?.let { confirmedTaskReceipt(it, conversation) }
    val confirmedId = confirmedItem?.id ?: receipt?.confirmedItemId
    val echo = receipt?.takeIf { confirmedId == null }
    var initialPositioned by remember(threadId) { mutableStateOf(false) }
    var followTail by remember(threadId) { mutableStateOf(true) }
    var returningToTail by remember(threadId) { mutableStateOf(false) }
    val navigationScope = rememberCoroutineScope()
    val toolProjector = remember(threadId) { ToolRunProjector() }
    val stripeLedger = remember(threadId) { StripeLedger() }
    var expandedTools by rememberSaveable(threadId) { mutableStateOf<String?>(null) }
    var composerExpanded by rememberSaveable(threadId) { mutableStateOf(true) }
    val dockPrefs = remember { activity.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE) }
    var dockRight by rememberSaveable { mutableStateOf(dockPrefs.getBoolean("composer_dock_right", true)) }
    var composerHeight by remember { mutableIntStateOf(0) }
    var backdropOrigin by remember { mutableStateOf(Offset.Zero) }
    val backdrop = rememberGraphicsLayer()
    val density = LocalDensity.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val motion = rememberChatMotion()
    val presentation = rememberSequentialTranscript(threadId, conversation, followTail && motion && !loadingHistory, loadingFull)
    val rows = remember(presentation.items, conversation?.turns) {
        toolProjector.project(presentation.items, conversation?.turns.orEmpty())
    }
    val rowKeys = rows.map { if (!it.tools && it.key == confirmedId) receipt?.key ?: it.key else it.key } + listOfNotNull(echo?.key)
    val stripes = remember(rowKeys) { stripeLedger.assign(rowKeys) }
    val copySources = remember(messages) { messages.associateBy { it.id } }
    val appearance = LocalChatAppearance.current
    val tail = rows.lastOrNull()
    val tailMine = echo != null || tail?.items?.lastOrNull()?.type == "userMessage"
    val pageSurface = transcriptPageSurface(appearance, tailMine, stripes[rowKeys.lastOrNull()] ?: 0, night)
    val activeToolIds = if (activityStatus?.animated == true) {
        val activeTurn = if (session?.activeTurnId != null) conversation?.turns?.find { it.id == session.activeTurnId }
            else conversation?.turns?.lastOrNull()
        activeTurn?.items?.filter { it.status == "inProgress" }?.map { it.id }?.toSet().orEmpty()
    } else emptySet()
    val latestConversation by rememberUpdatedState(conversation)
    val latestMessages by rememberUpdatedState(messages)
    val latestLoadOlder by rememberUpdatedState(onLoadOlder)
    val threshold = with(density) { 64.dp.toPx() }
    val headerInset = (35.dp * density.fontScale.coerceAtLeast(1f) + 28.dp).coerceAtLeast(64.dp)
    val taskReference = latestTask(conversation, receipt)
    val legacyTask = conversation != null && !conversation.taskReferenceSupported && conversation.hasOlder
    val recallInset = if (messages.count { it.type == "userMessage" } > 1) 48.dp else 0.dp
    val stickyTask by remember(rows, listState, headerInset, recallInset) { derivedStateOf {
        val info = listState.layoutInfo
        val boundary = info.viewportStartOffset + with(density) { (headerInset + recallInset).roundToPx() }
        val indices = rows.indices.filter { rows[it].items.singleOrNull()?.type == "userMessage" }
        stickyTaskIndex(indices, listState.firstVisibleItemIndex,
            info.visibleItemsInfo.associate { it.index to (it.offset + it.size) }, boundary)
            ?.let { rows[it].items.single() }
    } }
    var recallOpen by remember(threadId) { mutableStateOf(false) }
    var recallSelection by remember(threadId) { mutableStateOf<TaskReference?>(null) }
    var recallPrevious by remember(threadId) { mutableStateOf(emptyList<MessageItem>()) }
    var taskSearchMessage by remember(threadId) { mutableStateOf("") }
    var taskSearchJob by remember(threadId) { mutableStateOf<Job?>(null) }
    var highlightedTask by remember(threadId) { mutableStateOf<String?>(null) }
    val latestRows by rememberUpdatedState(rows)
    val latestConfirmedId by rememberUpdatedState(confirmedId)
    val latestReceipt by rememberUpdatedState(receipt)
    DisposableEffect(threadId) { onDispose { taskSearchJob?.cancel() } }
    fun dismissRecall() { taskSearchJob?.cancel(); recallOpen = false }
    fun jumpToTask(target: TaskReference) {
        if (loadingHistory || taskSearchJob?.isActive == true) return
        followTail = false; taskSearchMessage = "正在查找原消息…"; loadingHistory = true
        taskSearchJob = navigationScope.launch {
            try {
                when (findTaskPage(target.item.id, { latestConversation }, { latestLoadOlder() })) {
                    TaskSearchResult.More -> taskSearchMessage = "尚未找到，已加载 3 页；可继续查找或取消。"
                    TaskSearchResult.Missing -> taskSearchMessage = "原消息已不可用，请刷新后再试。"
                    TaskSearchResult.Found -> {
                        val index = withTimeout(5000) {
                            snapshotFlow { latestRows.indexOfFirst { row -> row.items.any { it.id == target.item.id } } }.first { it >= 0 }
                        }
                        recallOpen = false
                        if (motion) listState.animateScrollToItem(index) else listState.scrollToItem(index)
                        withFrameNanos { }
                        val key = if (target.item.id == latestConfirmedId) latestReceipt?.key ?: target.item.id else target.item.id
                        val located = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }
                        if (located != null) {
                            val top = listState.layoutInfo.viewportStartOffset + with(density) { (headerInset + recallInset + 4.dp).toPx() }
                            listState.scrollBy(located.offset - top)
                        }
                        loadingHistory = false
                        highlightedTask = target.item.id; taskSearchMessage = ""
                        delay(if (motion) 1000 else 0); highlightedTask = null
                    }
                }
            } catch (exception: CancellationException) { throw exception }
            catch (_: Exception) { taskSearchMessage = "查找失败，请重试；当前阅读位置和草稿已保留。" }
            finally { loadingHistory = false; highlightedTask = null }
        }
    }
    val pullOffset by animateFloatAsState(if (loadingHistory) with(density) { 44.dp.toPx() } else historyPull.distance,
        tween(if (motion && !historyPull.dragging) 260 else 0), label = "history-pull-return")
    fun revealHeader() { headerExpanded = true; headerInteraction++ }
    val requestHistory: () -> Unit = {
        if (!loadingHistory && !recallOpen && latestConversation?.hasOlder == true) {
            loadingHistory = true; historyError = ""; followTail = false
            navigationScope.launch {
                try {
                    val before = latestMessages
                    val fresh = latestLoadOlder() ?: throw IllegalStateException("没有收到历史消息，请下拉重试")
                    val after = fresh.turns.flatMap { it.items }.filter {
                        it.type in setOf("userMessage", "agentMessage", "plan", "commandExecution", "fileChange")
                    }
                    historyArrivals.addAll(olderArrivalIds(before, after))
                    // Stable message keys let LazyColumn retain the current reading item and offset,
                    // including scrolling while the request is pending. Do not restore by index here:
                    // the callback has updated data, but LazyColumn can still have the OLD provider.
                    // A scroll request clears its remembered key and moves twice across the prepend.
                    withFrameNanos { }
                } catch (exception: CancellationException) { throw exception }
                catch (exception: Exception) { historyError = networkError(exception) }
                finally { loadingHistory = false }
            }
        }
    }
    val loadHistory by rememberUpdatedState(requestHistory)
    val historyScroll = remember(threadId, threshold) { object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput) {
                if (!headerExpanded) revealHeader()
                if (available.y > 0f) followTail = false
                return Offset(0f, historyPull.retract(available.y))
            }
            return Offset.Zero
        }
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y > 0 && !listState.canScrollBackward &&
                latestConversation != null && !loadingHistory) {
                followTail = false
                return Offset(0f, historyPull.pull(available.y, threshold))
            }
            return Offset.Zero
        }
        override suspend fun onPreFling(available: Velocity): Velocity {
            val pulled = historyPull.dragging
            if (historyPull.release(threshold)) loadHistory()
            return if (pulled) Velocity(0f, available.y) else Velocity.Zero
        }
    } }
    val showNavigation by remember(threadId, listState) { derivedStateOf { initialPositioned && !followTail && listState.canScrollForward } }
    BackHandler { onBack() }

    LaunchedEffect(headerInteraction, headerTouching) {
        if (headerInteraction == 0 || headerTouching) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.first { !it }
        delay(2400)
        headerExpanded = false
    }

    LaunchedEffect(confirmedItem?.id) {
        if (confirmedItem != null) receipt = receipt?.copy(confirmedItemId = confirmedItem.id)
    }

    // Wait for the shell and keyboard to settle, then reveal the accepted user message at the bottom.
    // Do not keep pulling the user down while they deliberately read older history.
    LaunchedEffect(receipt?.key) {
        if (receipt == null) return@LaunchedEffect
        followTail = true
        delay(if (motion) 500 else 50)
        if (!followTail) return@LaunchedEffect
        // Keep a fast-arriving reply / activity footer visible too, rather than leaving it below the viewport.
        val index = listState.layoutInfo.totalItemsCount - 1
        if (index >= 0) {
            listState.scrollToItem(index)
            withFrameNanos { }
            val positioned = listState.layoutInfo.visibleItemsInfo.find { it.index == index }
            if (positioned != null) {
                val bottom = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.afterContentPadding
                val distance = (positioned.offset + positioned.size - bottom).toFloat()
                listState.animateScrollBy(distance, tween(if (motion) 280 else 0))
            }
        }
    }

    LaunchedEffect(threadId, conversation, showPlanChoice) {
        if (conversation == null) return@LaunchedEffect
        withFrameNanos { }
        if (listState.layoutInfo.totalItemsCount == 0) return@LaunchedEffect
        if (loadingHistory) return@LaunchedEffect
        if (!initialPositioned) {
            listState.reachTranscriptEnd(animated = false)
            initialPositioned = true
            if (showPlanChoice) try { planFocus.requestFocus() } catch (_: IllegalStateException) { }
        } else if (followTail && !listState.isScrollInProgress && !returningToTail) {
            listState.reachTranscriptEnd(animated = false)
        }
    }

    // Follow newly revealed lines only while the reader has chosen to remain at the tail.
    // Offsets are deliberately excluded: our own scrolling must not create a feedback loop.
    LaunchedEffect(threadId) {
        snapshotFlow {
            val info = listState.layoutInfo
            listOf(info.totalItemsCount, info.visibleItemsInfo.lastOrNull()?.index ?: -1,
                info.visibleItemsInfo.sumOf { it.size }, info.afterContentPadding, info.viewportEndOffset)
        }.collectLatest {
            // layoutInfo may emit from a subcomposition's measure pass. Scrolling there forces a
            // recursive remeasure. Coalesce changes and wait for the next frame before mutating it.
            withFrameNanos { }
            if (initialPositioned && followTail && !listState.isScrollInProgress && !returningToTail && !loadingHistory)
                listState.reachTranscriptEnd(animated = false)
        }
    }
    LaunchedEffect(threadId) {
        snapshotFlow { initialPositioned && expandedTools == null && !loadingHistory && !historyPull.dragging && !listState.isScrollInProgress && !listState.canScrollForward }
            .collect { if (it) followTail = true }
    }

    Column(Modifier.fillMaxSize().background(pageSurface)) {
        if (conversation != null && loadError.isNotBlank()) {
            Text(loadError, color = HapiPalette.muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(12.dp))
        }
        if (historyError.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text(historyError, Modifier.weight(1f), color = HapiPalette.muted, fontSize = 12.sp)
                TextButton(onClick = { historyError = "" }) { Text("关闭") }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { backdropOrigin = it.positionInRoot() }
            .pointerInput(threadId, headerInset) {
                try {
                    awaitPointerEventScope {
                        var startedInHeader = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressed = event.changes.any { it.pressed }
                            if (pressed != headerTouching) {
                                if (pressed) startedInHeader = event.changes.any { it.position.y <= with(density) { headerInset.toPx() } }
                                headerTouching = pressed
                                // Do not replace the compact link between pointer down/up:
                                // that would cancel its click before the task dialog can open.
                                if (!startedInHeader) revealHeader()
                                if (!pressed) startedInHeader = false
                            }
                        }
                    }
                } finally { headerTouching = false; historyPull.cancel() }
            }) {
        Box(Modifier.fillMaxSize().drawWithContent {
            backdrop.record { this@drawWithContent.drawContent() }
            drawLayer(backdrop)
        }.background(pageSurface)) {
        TerminalGrid(headerActivity(session, conversation).running, Modifier.matchParentSize())
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("transcript").nestedScroll(historyScroll)
            .graphicsLayer { translationY = pullOffset * .5f }
            .semantics { customActions = if (conversation?.hasOlder == true && !loadingHistory)
                listOf(CustomAccessibilityAction("加载更早消息") { loadHistory(); true }) else emptyList() },
            contentPadding = PaddingValues(top = headerInset + recallInset,
            bottom = with(density) { composerHeight.toDp() }.coerceAtLeast(60.dp) + 24.dp), verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.Bottom)) {
            items(rows, key = { if (!it.tools && it.key == confirmedId) receipt?.key ?: it.key else it.key }) { row ->
                val arriving = remember(row.key) { row.items.map { historyArrivals.remove(it.id) }.any { it } }
                val loadFull: (MessageItem) -> Unit = { item ->
                    loadingFull = loadingFull + item.id; historyError = ""
                    scope.launch {
                        try { onLoadFull(item) }
                        catch (exception: CancellationException) { throw exception }
                        catch (exception: Exception) { historyError = networkError(exception) }
                        finally { loadingFull = loadingFull - item.id }
                    }
                }
                HistoryArrival(arriving) { if (row.tools) {
                    ToolRunRow(row, expandedTools == row.key, onToggle = {
                        followTail = false
                        expandedTools = if (expandedTools == row.key) null else row.key
                    }, loadingFull, loadFull, onLoadFull, stripe = stripes[row.key] ?: 0, activeToolIds = activeToolIds)
                } else {
                    val item = row.items.single()
                    Column {
                        ChatMessageBubble(item, receiptKey = receipt?.key?.takeIf { item.id == confirmedId },
                            onLoadFull = onLoadFull, loadingFull = item.id in loadingFull,
                            reveal = presentation.reveals[item.id], copyItem = copySources[item.id] ?: item,
                            stripe = stripes[if (item.id == confirmedId) receipt?.key ?: item.id else item.id] ?: 0,
                            highlighted = item.id == highlightedTask,
                            processing = headerActivity(session, conversation).running && item.roundNumber > 0 &&
                                item.roundNumber == conversation?.latestUserMessage?.item?.roundNumber)
                        FullContentButton(item, item.id in loadingFull) { loadFull(item) }
                    }
                } }
                row.items.lastOrNull()?.takeIf { it.turnLastItem }?.let { tail ->
                    Box(Modifier.background(messageSurface(LocalChatAppearance.current, tail.type == "userMessage", stripes[row.key] ?: 0, night))) {
                        TurnTime(tail, running = !tail.turnCompleted && headerActivity(session, conversation).running && row == rows.lastOrNull())
                    }
                }
            }
            if (echo != null) item(key = echo.key) {
                ChatMessageBubble(MessageItem(echo.key, "userMessage", echo.text, "", "", "", "", null, emptyList()), receiptKey = echo.key, awaitingSync = true,
                    stripe = stripes[echo.key] ?: 0)
            }
            conversation?.approvals?.forEach { approval -> item(key = "approval-${approval.id}") {
                ApprovalCard(approval, session?.activeTurnId, busy, conversation.capabilities.approvals, onApproval)
            } }
            conversation?.questions?.forEach { request -> item(key = "question-${request.id}") {
                QuestionCard(request, busy, questionDraftStore, threadId, conversation.capabilities.questions,
                    questionStorageAvailable, onRefreshQuestions, onAnswer)
            } }
            conversation?.controlNotice?.let { notice -> item(key = "control-notice") {
                Text(notice, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = TextMuted, fontSize = 12.sp)
            } }
            if (showPlanChoice) item(key = "plan-choice") {
                DecisionSurface(modifier = Modifier.focusRequester(planFocus).focusable()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("PLAN · 规划完成", fontSize = 11.sp, color = TextInk)
                        Spacer(Modifier.height(6.dp))
                        Text("下一步由你决定", fontWeight = FontWeight.SemiBold, color = TextInk)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { composerExpanded = true; scope.launch { delay(300); try { composerFocus.requestFocus() } catch (_: IllegalStateException) { } } }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("继续规划", color = TextInk, fontSize = 12.sp) }
                            Button(onClick = onImplement, enabled = !busy && conversation?.capabilities?.implementPlan == true, colors = decisionButtonColors(), modifier = Modifier.weight(1f)) { Text("实施计划", fontSize = 12.sp) }
                        }
                        if (conversation?.capabilities?.implementPlan != true) Text("实施计划请在电脑处理", color = TextMuted, fontSize = 12.sp)
                    }
                }
            }
            if (activityStatus != null) item(key = "current-activity") {
                Box(Modifier.padding(horizontal = 16.dp)) { ActivityFooter(activityStatus) }
            }
        }
        }
        GlassChatHeader((if (terminalName.isBlank()) "" else "$terminalName · ") + (session?.title ?: "对话"), headerExpanded, night, onExpand = { revealHeader() },
            onBack = onBack, onToggleNight = { revealHeader(); onToggleNight() }, backdrop = backdrop, backdropOrigin = backdropOrigin,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp), activity = headerActivity(session, conversation),
            taskSubtitle = conversation?.userMessageCount?.let { "共 $it 轮 · 点击回看" } ?: "点击回看任务",
            onTaskClick = {
                recallSelection = taskReference
                recallPrevious = messages.filter { it.type == "userMessage" }.asReversed()
                taskSearchMessage = ""; recallOpen = true
            })
        if (recallInset > 0.dp) stickyTask?.let { item ->
            StickyTask(item, { jumpToTask(TaskReference(item)) }, Modifier.align(Alignment.TopStart).padding(top = headerInset))
        }
        HistoryPullIndicator(historyPull.distance, threshold, historyPull.dragging, loadingHistory,
            conversation?.hasOlder == true, historyError, conversation?.historyReset == true, backdrop, backdropOrigin,
            Modifier.align(Alignment.TopCenter).padding(top = headerInset + recallInset))
        if (conversation == null) Column(Modifier.align(Alignment.Center).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (loadError.isBlank()) {
                if (motion) CircularProgressIndicator(modifier = Modifier.size(22.dp).processingLight(true), strokeWidth = 2.dp, color = Green)
                Spacer(Modifier.height(12.dp))
                Text(if (desktopOpening) "正在读取原对话，电脑可能切换到这条对话…" else "正在加载对话…",
                    Modifier.testTag("conversation-loading-label").processingLight(true), color = TextMuted, fontSize = 13.sp)
            } else {
                Text("暂时无法读取对话", color = TextInk, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(5.dp))
                Text(loadError, color = TextMuted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onRetry, enabled = !busy) { Text("重试") }
            }
        }
        TranscriptNavigation(showNavigation, activityStatus, composerExpanded, with(density) { composerHeight.toDp() },
            backdrop, backdropOrigin, modifier = Modifier.align(Alignment.BottomCenter), onClick = {
                if (!returningToTail) navigationScope.launch {
                    returningToTail = true
                    try {
                        listState.reachTranscriptEnd(animated = motion)
                        listState.reachTranscriptEnd(animated = false)
                        followTail = true
                    }
                    finally { returningToTail = false }
                }
            })
        GlassComposer(draft = draft, onDraft = { editDraft(it) }, mode = session?.mode ?: "default", busy = busy || conversation == null || echo != null,
            modeEnabled = conversation?.capabilities?.mode == true,
            running = session?.activeTurnId != null, sending = sending,
            working = headerActivity(session, conversation).running,
            startedAt = conversation?.turns?.find { it.id == session?.activeTurnId }?.items?.firstNotNullOfOrNull { it.turnStartedAtMs },
            expanded = composerExpanded, onExpanded = { composerExpanded = it },
            dockRight = dockRight, onDockRight = { dockRight = it; dockPrefs.edit().putBoolean("composer_dock_right", it).apply() },
            hasAttention = showPlanChoice || conversation?.needsAttention() == true,
            error = sendError, focusRequester = composerFocus, backdrop = backdrop, backdropOrigin = backdropOrigin,
            onHeight = { composerHeight = it }, onMode = onMode, onStop = onStop,
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 14.dp, vertical = 12.dp),
            onSend = {
                val text = draft
                val submittedDraft = draftStore?.read(threadId)
                if (text.isNotBlank() && !sending) {
                    val previousIds = messages.filter { it.type == "userMessage" }.map { it.id }.toSet() + listOfNotNull(conversation?.latestUserMessage?.key)
                    sendError = ""; sending = true
                    scope.launch {
                        try {
                            val started = android.os.SystemClock.elapsedRealtime()
                            val turnId = onSend(text)
                            receipt = SendPresentation("sent-${java.util.UUID.randomUUID()}", text, turnId, previousIds)
                            if (draftStore != null) draftStore.clearIfUnchanged(threadId, submittedDraft)
                            else if (transientDraft == text) transientDraft = ""
                            // A fast receipt still gets one perceptible, short confirmation transition.
                            if (motion) delay((280 - (android.os.SystemClock.elapsedRealtime() - started)).coerceAtLeast(0))
                            focus.clearFocus(); keyboard?.hide()
                            composerExpanded = true
                        }
                        catch (exception: CancellationException) { throw exception }
                        catch (exception: Exception) {
                            // Draft never leaves the editor before acceptance, including uncertain sends.
                            composerExpanded = true
                            sendError = networkError(exception, sending = true)
                        } finally { sending = false }
                    }
                }
            })
        }
    }
    if (recallOpen) TaskRecallDialog(recallSelection, recallPrevious, legacyTask,
        loadingHistory, taskSearchMessage, ::dismissRecall, onLoadTask, ::jumpToTask,
        onCancel = { taskSearchJob?.cancel(); taskSearchMessage = "已取消查找。" }, onLoadPage = onLoadTaskPage,
        runningRound = taskReference?.item?.roundNumber?.takeIf { it > 0 && headerActivity(session, conversation).running },
        liveTasks = messages + listOfNotNull(conversation?.latestUserMessage?.item))
}

@Composable
internal fun NativeMarkdown(text: String, claimReveal: () -> String? = { null }, reveal: ReplyReveal? = null) {
    val context = LocalContext.current
    val skin = LocalChatAppearance.current.skin
    val night = LocalAppPalette.current.isNight
    val density = LocalDensity.current
    val markwon = remember(context, density.density, density.fontScale) { chatMarkdown(context) }
    val motion = rememberChatMotion()
    AndroidView(
        factory = { ReplyRevealTextView(it).apply { configureChatText(this) } },
        update = { configureSkinText(it, skin, night); it.bind(markwon, text, motion, reveal, claimReveal) },
        onRelease = { it.finishReveal() },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun ToolCard(item: MessageItem, loadingFull: Boolean = false, onLoadFull: () -> Unit = {},
    onCopyFull: suspend (MessageItem) -> MessageItem = { it }) {
    var expanded by rememberSaveable(item.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        ToolHeader(item, expanded, { expanded = !expanded }, loadingFull, onCopyFull)
        ToolDetails(item, expanded, loadingFull, onLoadFull)
    }
}

@Composable
internal fun ToolHeader(item: MessageItem, expanded: Boolean, onToggle: () -> Unit, loadingFull: Boolean,
    onCopyFull: suspend (MessageItem) -> MessageItem, ordinal: String? = null, processing: Boolean = item.status == "inProgress") {
    val palette = LocalAppPalette.current
    val muted = zebraSecondary(LocalChatAppearance.current.zebra, palette.isNight, palette.muted)
    val command = item.type == "commandExecution"
    val summary = if (command) item.command.lineSequence().firstOrNull().orEmpty() else item.changes.firstOrNull()?.path.orEmpty()
    Row(Modifier.fillMaxWidth().heightIn(min = toolHeaderHeight()).testTag("tool-${item.id}")
        .semantics { stateDescription = if (expanded) "已展开" else "已收起" }
        .clickable(role = Role.Button, onClickLabel = if (expanded) "收起详情" else "展开详情", onClick = onToggle)
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolChevron(expanded, "tool-chevron-${item.id}")
        if (ordinal != null) Text(ordinal, color = muted, fontSize = 10.sp, modifier = Modifier.padding(end = 6.dp))
        Text(toolSummaryLabel(item), Modifier.testTag("tool-status-${item.id}").processingLight(processing),
            color = if (toolSummaryLabel(item).endsWith("失败")) palette.errorInk else muted, fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Text(summary, Modifier.weight(1f), color = muted, fontSize = 12.sp, style = TerminalTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        ContentTime(item, processing)
        if (expanded) ContentCopyButton(item, onCopyFull, enabled = !loadingFull, color = muted)
    }
}

@Composable
internal fun ToolDetails(item: MessageItem, expanded: Boolean, loadingFull: Boolean, onLoadFull: () -> Unit) {
    val motion = rememberChatMotion()
    AnimatedVisibility(expanded,
        enter = expandVertically(tween(if (motion) 360 else 0, easing = TranscriptEase), expandFrom = Alignment.Top) + fadeIn(tween(if (motion) 240 else 0)),
        exit = shrinkVertically(tween(if (motion) 280 else 0, easing = TranscriptEase), shrinkTowards = Alignment.Top) + fadeOut(tween(if (motion) 140 else 0))) {
        Column(Modifier.fillMaxWidth()) {
            ToolCodeBody(item)
            FullContentButton(item, loadingFull, onLoadFull)
        }
    }
}

@Composable
private fun FullContentButton(item: MessageItem, loading: Boolean, onLoad: () -> Unit) {
    if (item.truncated) TextButton(enabled = !loading, onClick = onLoad, modifier = Modifier.testTag("full-${item.id}")) {
        Text(if (loading) "正在下载全文…" else "仅显示预览 · 加载完整内容（约 ${((item.fullBytes + 1023) / 1024).coerceAtLeast(1)} KB）",
            Modifier.processingLight(loading), color = TextMuted, fontSize = 12.sp)
    }
}

internal data class CodeRow(val text: String, val kind: String, val trailing: String? = null)

internal fun toolCodeRows(item: MessageItem): List<CodeRow> = buildList {
    if (item.type == "commandExecution") {
        add(CodeRow("COMMAND", "label", if (item.exitCode == null) null else "EXIT ${item.exitCode}"))
        item.command.ifBlank { "—" }.lineSequence().forEach { add(CodeRow(it, "code")) }
        if (item.cwd.isNotBlank()) add(CodeRow(item.cwd, "path"))
        add(CodeRow("OUTPUT", "label"))
        item.output.ifBlank { "(no output)" }.lineSequence().forEach { add(CodeRow(it, "code")) }
    } else if (item.changes.isEmpty()) {
        add(CodeRow("没有文件修改详情", "path"))
    } else item.changes.forEach { change ->
        add(CodeRow("DIFF", "label", change.path))
        change.diff.lineSequence().forEach { line -> add(CodeRow(line, "diff")) }
    }
}

@Composable
internal fun toolHeaderHeight() = (with(LocalDensity.current) { 22.sp.toDp() } + 16.dp).coerceAtLeast(48.dp)

@Composable
internal fun toolCodeHeight(rows: List<CodeRow>) = (with(LocalDensity.current) { 17.sp.toDp() } * rows.size + 8.dp).coerceAtMost(240.dp)

@Composable
private fun ToolCodeBody(item: MessageItem) {
    // Keep huge output lazy; one text layout per visible line, not a megabyte Text node.
    val rows = remember(item) { toolCodeRows(item) }
    val horizontal = rememberScrollState()
    val style = LocalChatAppearance.current.zebra
    LazyColumn(Modifier.fillMaxWidth().height(toolCodeHeight(rows)).background(Color(0xFF0B1220))
        .testTag("code-${item.id}"), contentPadding = PaddingValues(vertical = 4.dp)) {
        itemsIndexed(rows, key = { index, _ -> index }) { index, row ->
          Box(Modifier.fillMaxWidth().background(zebraSurface(style, index, true)).padding(horizontal = 12.dp)) {
            when (row.kind) {
                "label" -> TerminalLabel(row.text, row.trailing)
                "path" -> Text(row.text, color = Color(0xFF93A6BC), fontSize = 11.sp, lineHeight = 17.sp, style = TerminalTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                "diff" -> {
                    val added = row.text.startsWith("+") && !row.text.startsWith("+++")
                    val removed = row.text.startsWith("-") && !row.text.startsWith("---")
                    Text(
                        row.text.ifEmpty { " " }, modifier = Modifier.horizontalScroll(horizontal)
                            .background(when { added -> Color(0xFF173A31); removed -> Color(0xFF42252E); else -> Color.Transparent }),
                        color = when { added -> Color(0xFF8EE7A8); removed -> Color(0xFFFF9DA6); row.text.startsWith("@@") -> Color(0xFF64D9FF); else -> Color(0xFFC9D5E3) },
                        style = TerminalTextStyle, fontSize = 12.sp, lineHeight = 17.sp, softWrap = false,
                    )
                }
                else -> TerminalCode(row.text, horizontal)
            }
          }
        }
    }
}

@Composable
private fun TerminalLabel(label: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Color(0xFF93A6BC), fontSize = 11.sp, lineHeight = 17.sp, style = TerminalTextStyle, fontWeight = FontWeight.Medium)
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Text(trailing, modifier = Modifier.weight(1f), color = Color(0xFF93A6BC), fontSize = 11.sp, lineHeight = 17.sp, style = TerminalTextStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private val terminalTokenPattern = Regex("""("(?:\\.|[^"])*"|'(?:\\.|[^'])*'|--?[A-Za-z][\w-]*|\b\d+\b|\b(?:git|npm|adb|gh|node|pwsh|curl)\b)""")

@Composable
private fun TerminalCode(source: String, horizontal: androidx.compose.foundation.ScrollState) {
    val highlighted = remember(source) {
        buildAnnotatedString {
            append(source)
            terminalTokenPattern.findAll(source).forEach { match ->
                val token = match.value
                val color = when {
                    token.startsWith('"') || token.startsWith('\'') -> Color(0xFFFFC27A)
                    token.startsWith('-') -> Color(0xFFC9A7FF)
                    token.firstOrNull()?.isDigit() == true -> Color(0xFF71D8F7)
                    else -> Color(0xFF7EE0B0)
                }
                addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
            }
        }
    }
    Text(
        highlighted,
        modifier = Modifier.horizontalScroll(horizontal),
        color = Color(0xFFDCE7F4), style = TerminalTextStyle, fontSize = 12.sp,
        lineHeight = 17.sp, softWrap = false,
    )
}

@Composable
internal fun ApprovalCard(approval: Approval, activeTurnId: String?, busy: Boolean, enabled: Boolean = false,
    onDecision: (Approval, Boolean) -> Unit) {
    var showDetails by remember { mutableStateOf(false) }
    DecisionSurface(warning = true) {
        Column(Modifier.padding(14.dp)) {
            Text("等待批准 · ${approval.kind}", fontWeight = FontWeight.SemiBold, color = TextInk)
            TextButton(onClick = { showDetails = true }) { Text("查看请求详情", color = TextInk) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onDecision(approval, false) }, enabled = enabled && !busy, modifier = Modifier.weight(1f)) { Text("拒绝", color = TextInk) }
                Button(onClick = { onDecision(approval, true) }, colors = decisionButtonColors(), enabled = enabled && !busy && approval.approvable && activeTurnId == approval.turnId, modifier = Modifier.weight(1f)) { Text("仅批准这次") }
            }
            if (!enabled) Text("此批准需要在电脑处理", color = TextMuted, fontSize = 12.sp)
        }
    }
    if (showDetails) AlertDialog(onDismissRequest = { showDetails = false }, title = { Text("请求详情") }, text = {
        Text(approval.details, Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState()), style = TerminalTextStyle, fontSize = 12.sp)
    }, confirmButton = { TextButton(onClick = { showDetails = false }) { Text("关闭") } })
}

@Composable
internal fun QuestionCard(request: QuestionRequest, busy: Boolean, store: QuestionDraftStore? = null,
    threadId: String = "", capable: Boolean = true, storageAvailable: Boolean = true,
    onRefresh: () -> Unit = {},
    onAnswer: (QuestionRequest, Map<String, String>) -> Unit) {
    var transient by remember(store, threadId, request.id, request.revision) { mutableStateOf(QuestionDraft()) }
    var unsaved by remember(store, threadId, request.id, request.revision) { mutableStateOf<QuestionDraft?>(null) }
    var saveError by remember(store, threadId, request.id, request.revision) { mutableStateOf(false) }
    val draft = unsaved ?: store?.read(threadId, request) ?: transient
    fun save(next: QuestionDraft) {
        if (store == null) { transient = next; return }
        try { store.save(threadId, request, next); unsaved = null; saveError = false }
        catch (_: Exception) { unsaved = next; saveError = true }
    }
    val palette = LocalAppPalette.current
    val pending = request.status == "pending" && draft.state == "pending"
    val needsDesktop = request.status == "desktop_required" || !capable || request.questions.any { it.secret }
    val canEdit = pending && !needsDesktop && storageAvailable && !saveError && !busy
    val heading = when {
        request.status == "accepted" -> "Desktop已记录答案"
        request.status == "closed" -> "已处理或失效"
        request.status == "desktop_required" -> "需要在电脑回答"
        request.status == "unknown" -> "提交结果待核对"
        draft.state == "accepted" -> "Desktop已记录答案"
        draft.state == "closed" -> "已处理或失效"
        draft.state == "unknown" -> "提交结果待核对"
        draft.state == "submitting" -> "正在提交"
        needsDesktop -> "需要在电脑回答"
        else -> "需要你回答"
    }
    DecisionSurface {
        Column(Modifier.padding(14.dp)) {
            Text(heading, fontWeight = FontWeight.SemiBold, color = palette.ink)
            val notice = when {
                saveError -> "答案未能加密保存，仅保留在当前页面；请检查手机存储，勿提交。"
                request.status == "desktop_required" -> request.notice.ifBlank { "请在电脑处理此问题。" }
                request.status == "accepted" || request.status == "closed" -> request.notice.ifBlank { "问题已处理或失效。" }
                request.status == "unknown" || draft.state == "unknown" -> "提交结果待核对；请刷新确认，不要重复提交。"
                draft.state == "submitting" -> "正在等待电脑确认回答。"
                draft.notice.isNotBlank() -> draft.notice
                request.questions.any { it.secret } -> "此问题含私密输入，请在电脑回答。"
                !capable -> "当前电脑不支持手机回答，请在电脑处理。"
                !storageAvailable -> "无法加密保存答案，请检查手机存储后重试。"
                request.notice.isNotBlank() -> request.notice
                else -> ""
            }
            if (notice.isNotBlank()) Text(notice, color = palette.muted, fontSize = 12.sp)
            if (draft.state == "unknown" || request.status == "unknown") TextButton(onClick = onRefresh, enabled = !busy) {
                Text("刷新核对", color = palette.ink)
            }
            request.questions.forEachIndexed { index, question ->
                Spacer(Modifier.height(9.dp))
                Text("第 ${index + 1} 题 / 共 ${request.questions.size} 题 · ${question.title.ifBlank { question.text }}", color = palette.ink, fontSize = 13.sp)
                if (question.title.isNotBlank()) Text(question.text, color = palette.ink, fontSize = 12.sp)
                question.options.forEach { option ->
                    DecisionOption(option.label, question.id !in draft.other && draft.answers[question.id] == option.label, canEdit, option.description) {
                        save(draft.copy(answers = draft.answers + (question.id to option.label), other = draft.other - question.id))
                    }
                }
                val otherSelected = question.id in draft.other
                if (question.allowOther && question.options.isNotEmpty()) DecisionOption("其他答案", otherSelected, canEdit) {
                    save(draft.copy(answers = draft.answers + (question.id to ""), other = draft.other + question.id))
                }
                if (!question.secret && (question.options.isEmpty() || (question.allowOther && otherSelected))) OutlinedTextField(
                    value = draft.answers[question.id].orEmpty(), onValueChange = { save(draft.copy(answers = draft.answers + (question.id to it))) },
                    label = { Text("回答", color = palette.ink) }, modifier = Modifier.fillMaxWidth().testTag("question-answer-${question.id}"), enabled = canEdit,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = palette.ink, unfocusedTextColor = palette.ink,
                        disabledTextColor = palette.ink, cursorColor = palette.ink,
                        focusedBorderColor = palette.ink, unfocusedBorderColor = palette.muted, disabledBorderColor = palette.muted),
                )
            }
            Button(onClick = { onAnswer(request, draft.answers) }, modifier = Modifier.testTag("question-submit"), colors = decisionButtonColors(),
                enabled = canEdit && request.questions.isNotEmpty() && request.questions.all { !draft.answers[it.id].isNullOrBlank() }) {
                Text(if (busy || draft.state == "submitting") "正在提交" else "提交")
            }
        }
    }
}
