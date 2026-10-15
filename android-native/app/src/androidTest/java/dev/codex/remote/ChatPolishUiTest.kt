package dev.codex.remote

import android.content.ClipboardManager
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** Synthetic messages only: no live server, credentials, downloads or phone state. */
@RunWith(AndroidJUnit4::class)
class ChatPolishUiTest {
    @get:Rule(order = 0) val timeout: Timeout = Timeout.builder().withTimeout(90, TimeUnit.SECONDS).build()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)
    private val session = Session("polish", "fixture", "清楚地看见每一步", "COMPLETED", null, "default", 0)
    private fun message(id: String, type: String = "agentMessage", text: String = "这是一条清晰的记录。") =
        MessageItem(id, type, text, "", "", "", "completed", null, emptyList())
    private fun snapshot(items: List<MessageItem> = emptyList(), state: String = "completed", turn: String = "t") =
        Conversation(listOf(Turn(turn, state, items)), Observation(state, 0, "gateway", items.lastOrNull()?.type ?: ""), emptyList(), emptyList())

    @After fun cleanup() {
        compose.mainClock.autoAdvance = true
        prefs.edit().remove("zebra_style").remove("skin_effect").apply()
        clipboard.clearPrimaryClip()
    }

    @Test fun sentAndSynchronizedUserTextKeepsNewlinesBlankLinesAndIndentation() {
        val raw = "\n  第一行\n第二行\n\n  **保留原文**\n"
        val content = mutableStateOf(snapshot())
        var submitted: String? = null
        lateinit var root: View
        compose.setContent {
            root = LocalActivity.current!!.window.decorView
            HapiStyle(false) { Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                ChatScreen(session.id, session, "合成验收", content.value, "", false, false,
                    onToggleNight = {}, onBack = {}, onRetry = {}, onSend = { submitted = it; "sent-turn" }, onStop = {}, onMode = {},
                    onImplement = {}, onApproval = { _, _ -> }, onAnswer = { _, _ -> })
            } }
        }
        fun userViews(view: View): Sequence<TextView> = sequence {
            if (view is TextView && view.tag?.toString()?.startsWith("user-text-") == true) yield(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(userViews(view.getChildAt(index)))
        }
        fun assertRendered() = compose.runOnIdle {
            val view = userViews(root).single()
            assertEquals(raw, view.text.toString())
            assertTrue("Blank lines must contribute real layout lines", view.lineCount >= 6)
        }
        compose.onNode(hasSetTextAction()).performTextInput(raw)
        compose.onNodeWithContentDescription("发送消息").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("sent-message").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertEquals(raw, submitted)
        assertRendered()
        compose.runOnIdle { content.value = snapshot(listOf(message("real-user", "userMessage", raw)), turn = "sent-turn") }
        compose.waitForIdle()
        compose.onAllNodesWithTag("sent-message").assertCountEquals(1)
        assertRendered()
        captureTestScreen("38-user-literal-newlines")
        compose.onNodeWithTag("copy-real-user").performClick()
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(raw, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
    }

    @Test fun headerKeepsTaskSummaryWhileCircleTracksExecutionAndStopsForAttentionOrReducedMotion() {
        val command = message("live", "commandExecution", "").copy(command = "npm test", status = "inProgress")
        val request = message("request", "userMessage", "检查当前任务")
        val content = mutableStateOf(snapshot(listOf(request, command), "running").copy(userMessageCount = 1,
            taskReferenceSupported = true, latestUserMessage = SyncEntry(request.id, "r", "t", "running", request)))
        val task = mutableStateOf(session.copy(status = "RUNNING", activeTurnId = "t"))
        val reduced = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent { HapiStyle(false) {
            CompositionLocalProvider(LocalChatAppearance provides ChatAppearance(reduceMotion = reduced.value)) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    ChatScreen(session.id, task.value, "合成验收", content.value, "", false, false,
                        onToggleNight = {}, onBack = {}, onRetry = {}, onSend = { null }, onStop = {}, onMode = {},
                        onImplement = {}, onApproval = { _, _ -> }, onAnswer = { _, _ -> })
                }
            }
        } }
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("header-subtitle", useUnmergedTree = true).assertTextEquals("共 1 轮 · 点击回看")
        val spinner = compose.onNodeWithTag("header-loading", useUnmergedTree = true)
        spinner.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "转动"))
        val before = spinner.captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(280)
        val after = spinner.captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until before.height) for (x in 0 until before.width) if (before[x, y] != after[x, y]) changed++
        assertTrue("Header must visibly rotate, not only announce loading", changed > 10)
        compose.runOnIdle { content.value = content.value.copy(turns = listOf(Turn("t", "running",
            listOf(request, command.copy(output = "Running tests\n12 tests passed\n"))))) }
        compose.mainClock.advanceTimeBy(50)
        compose.onNodeWithTag("header-subtitle", useUnmergedTree = true).assertTextEquals("共 1 轮 · 点击回看")
        captureTestScreen("39-header-live-output")
        compose.runOnIdle { reduced.value = true }
        compose.mainClock.advanceTimeBy(50)
        spinner.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "静止"))
        compose.runOnIdle { reduced.value = false; task.value = task.value.copy(status = "WAITING_USER") }
        compose.mainClock.advanceTimeBy(50)
        spinner.assertContentDescriptionEquals("任务未在执行")
        spinner.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "静止"))
        compose.onNodeWithTag("header-subtitle", useUnmergedTree = true).assertTextEquals("共 1 轮 · 点击回看")
    }

    @Test fun commandDetailsFitContentReachEdgesAndKeepSquareControlsAccessible() {
        val command = mutableStateOf(message("compact", "commandExecution", "").copy(command = "echo ready", output = "ready", exitCode = 0))
        compose.setContent { HapiStyle(false) {
            var expanded by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).testTag("viewport")) {
                ToolRunRow(TranscriptRow("tools-compact", listOf(command.value)), expanded, { expanded = !expanded }, emptySet(), {}, { it })
            }
        } }
        compose.onNodeWithTag("toggle-tools-compact").performClick()
        compose.waitForIdle()
        val closedCommand = compose.onNodeWithTag("tool-compact").fetchSemanticsNode().boundsInRoot
        val list = compose.onNodeWithTag("list-tools-compact").fetchSemanticsNode().boundsInRoot
        assertEquals("Single unopened command has no fixed tall blank area", closedCommand.height, list.height, 1f)
        compose.onNodeWithTag("tool-compact").performClick()
        compose.waitForIdle()
        val page = compose.onNodeWithTag("viewport").fetchSemanticsNode().boundsInRoot
        val heading = compose.onNodeWithTag("tool-compact").fetchSemanticsNode().boundsInRoot
        val code = compose.onNodeWithTag("code-compact").fetchSemanticsNode().boundsInRoot
        assertEquals(page.left, code.left, 1f); assertEquals(page.right, code.right, 1f)
        assertEquals("No separate empty copy bar above code", heading.bottom, code.top, 1f)
        assertTrue("Four compact code rows should be less than two touch-target rows", code.height < heading.height * 2)
        val arrow = compose.onNodeWithTag("tool-chevron-compact", useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(arrow.width, arrow.height, 1f)
        assertTrue(arrow.top >= heading.top && arrow.bottom <= heading.bottom)
        captureTestScreen("40-compact-command-details")
        compose.runOnIdle { command.value = command.value.copy(output = (1..200).joinToString("\n") { "line $it" }) }
        compose.onNodeWithTag("code-compact").performScrollToIndex(202)
        compose.onNodeWithTag("tool-chevron-compact", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("copy-compact").assertIsDisplayed()
        compose.onNodeWithTag("tool-compact").performClick()
        compose.onNodeWithTag("code-compact").assertDoesNotExist()
    }

    @Test fun everyZebraPresetPersistsAndBothThemesRenderAlternatingFullWidthRows() {
        prefs.edit().putString("skin_effect", GridEffect.Off.name).commit()
        val settings = mutableStateOf(true)
        val night = mutableStateOf(false)
        compose.setContent { HapiStyle(night.value) {
            Column(Modifier.fillMaxSize().background(LocalAppPalette.current.background)
                .windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState())) {
                if (settings.value) ChatAppearanceSettings() else {
                    ChatMessageBubble(message("stripe-a"), stripe = 0)
                    ChatMessageBubble(message("stripe-b"), stripe = 1)
                    ChatMessageBubble(message("stripe-user", "userMessage", "我的原文\n保持换行。"), stripe = 0)
                    ChatMessageBubble(message("stripe-c", text = "斑马纹按记录交替，文字沿同一条左边线。"), stripe = 1)
                }
            }
        } }
        for (style in ZebraStyle.entries) {
            val current = readChatAppearance(prefs).zebra
            compose.onNodeWithContentDescription("斑马纹：${style.label}${if (style == current) "，已选中" else ""}")
                .performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(style, readChatAppearance(prefs).zebra)
            compose.onNodeWithContentDescription("斑马纹：${style.label}，已选中").assertIsDisplayed()
        }
        captureTestScreen("41-zebra-settings")
        compose.runOnIdle { settings.value = false }
        for (style in ZebraStyle.entries.filter { it != ZebraStyle.Off }) {
            for (dark in listOf(false, true)) {
                compose.runOnIdle { prefs.edit().putString("zebra_style", style.name).commit(); night.value = dark }
                compose.waitForIdle()
                val a = compose.onNodeWithTag("message-stripe-a").captureToImage().toPixelMap().let { it[1, 1] }
                val b = compose.onNodeWithTag("message-stripe-b").captureToImage().toPixelMap().let { it[1, 1] }
                assertNotEquals("Preset must produce two distinct rendered surfaces", a, b)
                assertEquals(zebraSurface(style, 0, dark).red, a.red, .01f)
                assertEquals(zebraSurface(style, 1, dark).blue, b.blue, .01f)
                captureTestScreen("42-zebra-${style.name}-${if (dark) "night" else "light"}")
            }
        }
    }
}
