package dev.codex.remote

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.Timeout
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class TerminalTranscriptUiTest {
    @get:Rule(order = 0) val timeout: Timeout = Timeout.builder().withTimeout(90, TimeUnit.SECONDS).build()
    @get:Rule(order = 1) val compose = createComposeRule()
    private fun reply(id: String, text: String = id) = MessageItem(id, "agentMessage", text, "", "", "", "completed", null, emptyList())
    private val session = Session("terminal-ui", "fixture", "按顺序，清楚地看见结果", "COMPLETED", null, "default", 0)
    private fun snapshot(items: List<MessageItem>) = Conversation(listOf(Turn("t", "completed", items)),
        Observation("completed", 0, "gateway", "agentMessage"), emptyList(), emptyList())

    @Test fun bothSpeakersUseFullWidthWithAlignedTextAndNoBubbleOffsets() {
        compose.setContent { HapiStyle(false) { Column(Modifier.fillMaxWidth().testTag("page")) {
            ChatMessageBubble(reply("user", "同一条左边线，不再左右错开。").copy(type = "userMessage"))
            ChatMessageBubble(reply("assistant", "开放的终端排版，保留清楚的角色区分。"))
        } } }
        val page = compose.onNodeWithTag("page").fetchSemanticsNode().boundsInRoot
        val user = compose.onNodeWithTag("message-user").fetchSemanticsNode().boundsInRoot
        val assistant = compose.onNodeWithTag("message-assistant").fetchSemanticsNode().boundsInRoot
        assertEquals(page.left, user.left, .5f); assertEquals(page.right, user.right, .5f)
        assertEquals(user.left, assistant.left, .5f); assertEquals(user.right, assistant.right, .5f)
        compose.onNodeWithText("› 你的任务").assertIsDisplayed()
        compose.onNodeWithText("✦ Codex").assertIsDisplayed()
        compose.onNodeWithTag("copy-user").assertIsEnabled()
        compose.onNodeWithTag("copy-assistant").assertIsEnabled()
        captureTestScreen("35-terminal-full-width")
    }

    @Test fun repeatedCommandsAreHiddenAndDrawerActuallyInterpolatesWithoutDownloading() {
        val items = (1..12).map { reply("cmd$it").copy(type = "commandExecution", text = "", command = "REPEATED_PRIVATE_CODE",
            output = "HIDDEN_OUTPUT", exitCode = if (it == 12) 1 else 0) }
        val row = TranscriptRow("tools-cmd1", items)
        var downloads = 0
        compose.mainClock.autoAdvance = false
        compose.setContent { HapiStyle(false) {
            var expanded by remember { mutableStateOf(false) }
            ToolRunRow(row, expanded, { expanded = !expanded }, emptySet(), { downloads++ }, { it })
        } }
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithText("执行记录 · 12 条命令 · 1 项失败").assertIsDisplayed()
        compose.onNodeWithText("REPEATED_PRIVATE_CODE").assertDoesNotExist()
        compose.onNodeWithText("HIDDEN_OUTPUT").assertDoesNotExist()
        val closed = compose.onNodeWithTag(row.key).fetchSemanticsNode().boundsInRoot.height
        compose.onNodeWithTag("toggle-${row.key}").performClick()
        compose.mainClock.advanceTimeBy(100)
        val middle = compose.onNodeWithTag(row.key).fetchSemanticsNode().boundsInRoot.height
        compose.mainClock.advanceTimeBy(400)
        val opened = compose.onNodeWithTag(row.key).fetchSemanticsNode().boundsInRoot.height
        assertTrue("Drawer needs real intermediate geometry", middle > closed && middle < opened)
        compose.onNodeWithText("HIDDEN_OUTPUT").assertDoesNotExist()
        assertEquals(0, downloads)
        compose.onNodeWithTag("tool-cmd1").performClick()
        compose.mainClock.advanceTimeBy(450)
        compose.onNodeWithText("HIDDEN_OUTPUT").assertIsDisplayed()
        assertEquals(0, downloads)
        captureTestScreen("36-terminal-command-drawer")
        compose.onNodeWithTag("toggle-${row.key}").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("HIDDEN_OUTPUT").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
    }

    @Test fun completedBatchRevealsInOrderInsideRealChatWithoutRunningSignal() {
        val initial = listOf(reply("history", "历史文字直接显示。"))
        val content = mutableStateOf(snapshot(initial))
        lateinit var root: View
        compose.setContent {
            root = LocalActivity.current!!.window.decorView
            HapiStyle(false) { Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                ChatScreen(session.id, session, "合成验收", content.value, "", false, false,
                    onToggleNight = {}, onBack = {}, onRetry = {}, onSend = { null }, onStop = {}, onMode = {},
                    onImplement = {}, onApproval = { _, _ -> }, onAnswer = { _, _ -> })
            } }
        }
        compose.onNodeWithContentDescription("收起到右侧").performClick()
        compose.waitForIdle()
        fun views(view: View): Sequence<ReplyRevealTextView> = sequence {
            if (view is ReplyRevealTextView) yield(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(views(view.getChildAt(index)))
        }
        val firstRevealed = AtomicBoolean(false)
        val secondRevealed = AtomicBoolean(false)
        val overlap = AtomicBoolean(false)
        val listener = ViewTreeObserver.OnDrawListener {
            val visible = views(root).toList()
            val first = visible.firstOrNull { it.text.startsWith("第一条") }
            val second = visible.firstOrNull { it.text.startsWith("第二条") }
            if (first?.isRevealing == true) firstRevealed.set(true)
            if (second?.isRevealing == true) secondRevealed.set(true)
            if (second != null && (first?.isRevealing == true || !firstRevealed.get())) overlap.set(true)
        }
        compose.runOnUiThread { root.viewTreeObserver.addOnDrawListener(listener) }
        try {
            compose.runOnIdle { content.value = snapshot(initial + listOf(
                reply("first", "第一条：" + "少一点，慢一点；按真实顺序呈现。".repeat(5)),
                reply("second", "第二条：第一条显露后再开始，不会两条一起弹出。"))) }
            compose.waitUntil(6000) { firstRevealed.get() && secondRevealed.get() }
            assertFalse("Separate reply rows must not animate simultaneously", overlap.get())
            compose.waitUntil(6000) {
                var active = true
                compose.runOnUiThread { active = views(root).any { it.isRevealing } }
                !active
            }
            compose.onNodeWithTag("transcript-activity").assertDoesNotExist()
            compose.onNodeWithTag("copy-second").assertIsEnabled()
            captureTestScreen("37-terminal-sequential-completed")
        } finally { compose.runOnUiThread { root.viewTreeObserver.removeOnDrawListener(listener) } }
    }
}
