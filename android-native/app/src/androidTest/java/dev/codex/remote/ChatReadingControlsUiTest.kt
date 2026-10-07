package dev.codex.remote

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Synthetic pages and drafts only. Never fetches real conversation history or sends commands. */
@RunWith(AndroidJUnit4::class)
class ChatReadingControlsUiTest {
    @get:Rule(order = 0) val timeout: Timeout = Timeout.builder().withTimeout(90, TimeUnit.SECONDS).withLookingForStuckThread(true).build()
    @get:Rule(order = 1) val compose = createComposeRule()
    private fun trace(stage: String) { android.util.Log.i("ReadingControlsTest", stage) }
    private val session = Session("reading-ui", "fixture", "只留一行标题，把空间还给聊天", "COMPLETED", null, "default", 0)
    private fun message(id: String) = MessageItem(id, "agentMessage", "$id\n\n留在正在阅读的位置。", "", "", "", "", null, emptyList())
    private val original = Conversation(listOf(Turn("t", "completed", (10..27).map { message("row-$it") })),
        Observation("completed", 0, "gateway", "agentMessage"), emptyList(), emptyList(), hasOlder = true)
    private fun prepend(current: Conversation, ids: IntRange, more: Boolean) = current.copy(
        turns = listOf(current.turns.single().copy(items = ids.map { message("row-$it") } + current.turns.single().items)), hasOlder = more)
    private fun launch(content: State<Conversation> = mutableStateOf(original), visible: State<Boolean> = mutableStateOf(true),
        load: suspend () -> Conversation? = { null }) {
        compose.setContent {
            var night by remember { mutableStateOf(false) }
            if (visible.value) HapiStyle(night) { Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                ChatScreen(session.id, session, "不应该显示的旧副标题", content.value, "", false, night,
                    onToggleNight = { night = !night }, onBack = {}, onRetry = {}, onSend = { null }, onStop = {},
                    onMode = {}, onImplement = {}, onApproval = { _, _ -> }, onAnswer = { _, _ -> }, onLoadOlder = load)
            } }
        }
    }
    private fun startPull(end: Float = .68f) {
        trace("pull: start $end")
        compose.onNodeWithTag("transcript").performTouchInput {
            down(Offset(width * .6f, height * .18f))
            moveTo(Offset(width * .6f, height * end), delayMillis = 350)
        }
        trace("pull: held")
    }
    private fun releasePull() {
        trace("pull: release")
        compose.onNodeWithTag("transcript").performTouchInput { up() }
        trace("pull: released")
    }
    private fun goToHistoryStart() {
        // A real reader first scrolls away from the live tail. A semantics-only scrollToIndex
        // bypasses that user intent and is correctly followed back to the latest reply.
        trace("history: leave tail")
        compose.onNodeWithTag("transcript").performTouchInput { swipeDown(startY = height * .2f, endY = height * .24f) }
        trace("history: scroll to start")
        compose.onNodeWithTag("transcript").performScrollToIndex(0)
        trace("history: at start")
    }

    @Test fun titleDefaultsCompactAndTouchMorphDoesNotResizeTranscript() {
        compose.mainClock.autoAdvance = false
        launch()
        compose.mainClock.advanceTimeBy(100)
        val viewport = compose.onNodeWithTag("transcript").fetchSemanticsNode().boundsInRoot
        val compact = compose.onNodeWithTag("chat-header-glass", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("chat-header").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已收起"))
        compose.onNodeWithText("不应该显示的旧副标题").assertDoesNotExist()
        compose.onNodeWithTag("transcript").performTouchInput { down(Offset(width * .6f, height * .35f)) }
        compose.mainClock.advanceTimeBy(160)
        val middle = compose.onNodeWithTag("chat-header-glass", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Header must interpolate, not pop open", middle.width > compact.width)
        compose.mainClock.advanceTimeBy(240)
        val expanded = compose.onNodeWithTag("chat-header-glass", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(expanded.width > middle.width)
        assertEquals(56f / 52f, expanded.height / compact.height, .05f)
        assertEquals(viewport, compose.onNodeWithTag("transcript").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("返回任务列表").assertIsDisplayed()
        captureTestScreen("30-glass-header-expanded")
        compose.onNodeWithTag("transcript").performTouchInput { up() }
        compose.mainClock.advanceTimeBy(3000)
        compose.onNodeWithTag("chat-header").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已收起"))
        assertEquals(viewport, compose.onNodeWithTag("transcript").fetchSemanticsNode().boundsInRoot)
        captureTestScreen("31-glass-header-compact")
        compose.mainClock.autoAdvance = true
    }

    @Test fun releaseLoadsOnePageKeepsAnchorAndAllowsAnotherPull() {
        val content = mutableStateOf(original)
        val gate = CompletableDeferred<Conversation>()
        var requests = 0
        launch(content) {
            requests++
            trace("history: request $requests")
            val fresh = if (requests == 1) gate.await() else prepend(content.value, 0..4, false)
            content.value = fresh
            fresh
        }
        compose.onNodeWithContentDescription("收起到右侧").performClick()
        trace("history: composer collapsed")
        goToHistoryStart()
        val anchorTop = compose.onNodeWithTag("message-row-10").fetchSemanticsNode().boundsInRoot.top
        startPull(.23f)
        releasePull()
        compose.waitForIdle()
        assertEquals("A short pull must not download", 0, requests)
        startPull()
        compose.onNodeWithTag("history-pull-indicator").assertContentDescriptionEquals("松开加载更早消息")
        assertEquals("No request before release", 0, requests)
        releasePull()
        compose.waitUntil(5000) { requests == 1 }
        compose.onNodeWithTag("history-pull-indicator").assertContentDescriptionEquals("正在加载更早消息")
        // During loading the list uses Android's edge stretch. Do not query semantics
        // between down/up: that waits for idle while the stretch is waiting for up.
        trace("history: repeat complete gesture while pending")
        compose.onNodeWithTag("transcript").performTouchInput {
            down(Offset(width * .6f, height * .18f))
            moveTo(Offset(width * .6f, height * .68f), delayMillis = 350)
            up()
        }
        assertEquals("A pending page must not be requested again", 1, requests)
        captureTestScreen("32-history-pull-loading")
        trace("history: complete pending page")
        compose.runOnIdle { gate.complete(prepend(original, 5..9, true)) }
        compose.waitUntil(5000) { !compose.onAllNodesWithContentDescription("正在加载更早消息").fetchSemanticsNodes().any() }
        compose.waitForIdle()
        captureTestScreen("33-history-loaded-anchor")
        compose.onNodeWithTag("message-row-10").assertIsDisplayed()
        assertEquals("Prepending must retain the current reading anchor", anchorTop,
            compose.onNodeWithTag("message-row-10").fetchSemanticsNode().boundsInRoot.top, 3f)
        trace("history: anchor retained")
        goToHistoryStart()
        startPull(); releasePull()
        compose.waitUntil(5000) { requests == 2 }
        compose.waitForIdle()
        compose.onNodeWithTag("message-row-5").assertIsDisplayed()
        goToHistoryStart()
        startPull()
        compose.onNodeWithTag("history-pull-indicator").assertContentDescriptionEquals("已到最早消息")
        releasePull()
        compose.waitForIdle()
        assertEquals(2, requests)
    }

    @Test fun failedHistoryCanRetryAndLeavingCancelsPendingRequest() {
        val visible = mutableStateOf(true)
        val gate = CompletableDeferred<Conversation>()
        var requests = 0
        var completed = false
        var cancelled = false
        launch(visible = visible) {
            requests++
            if (requests == 1) throw java.io.IOException("synthetic history failure")
            try { gate.await().also { completed = true } }
            finally { cancelled = !completed }
        }
        goToHistoryStart()
        startPull(); releasePull()
        compose.waitUntil(5000) { requests == 1 }
        compose.onNodeWithTag("history-pull-indicator").assertContentDescriptionEquals("加载失败，下拉重试")
        startPull(); releasePull()
        compose.waitUntil(5000) { requests == 2 }
        compose.runOnIdle { visible.value = false }
        compose.waitUntil(5000) { cancelled }
        compose.runOnIdle { gate.complete(prepend(original, 5..9, false)) }
        assertFalse(completed)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun multilinePasteAndTypingFollowCaretWithoutStealingMiddleEdits() {
        launch(mutableStateOf(original.copy(hasOlder = false)))
        val editor = compose.onNodeWithTag("composer-editor")
        val source = (1..24).joinToString("\n") { "第 $it 行 · long draft" }
        editor.performTextInput(source)
        fun assertCaretVisible(expectedSelection: Int) {
            compose.waitForIdle()
            val node = editor.fetchSemanticsNode()
            assertEquals(TextRange(expectedSelection), node.config[SemanticsProperties.TextSelectionRange])
            val results = mutableListOf<TextLayoutResult>()
            editor.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
            val cursor = results.single().getCursorRect(expectedSelection)
            val viewport = compose.onNodeWithTag("composer-input-viewport", useUnmergedTree = true).fetchSemanticsNode()
            val scroll = viewport.config[SemanticsProperties.VerticalScrollAxisRange]
            val pad = with(compose.density) { 12.dp.toPx() }
            assertTrue("Long draft must scroll internally", scroll.maxValue() > 0)
            assertTrue("Caret must not be hidden below input viewport", cursor.bottom + pad - scroll.value() <= viewport.boundsInRoot.height + 3f)
            assertTrue("Caret must not be hidden above input viewport", cursor.top + pad - scroll.value() >= -3f)
        }
        assertCaretVisible(source.length)
        editor.performTextInput("\n继续输入，应该自动跟随。")
        assertCaretVisible(source.length + "\n继续输入，应该自动跟随。".length)
        captureTestScreen("34-long-draft-caret")
        editor.performTextInputSelection(TextRange(3))
        editor.performTextInput("修改")
        assertCaretVisible(5)
        compose.onNodeWithContentDescription("收起到左侧").performClick()
        compose.onNodeWithContentDescription("展开输入框，草稿已保留").performClick()
        assertCaretVisible(5)
    }
}
