package dev.codex.remote

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic transcript only; never sends commands to a real account or Gateway. */
@RunWith(AndroidJUnit4::class)
class TranscriptUiTest {
    @get:Rule val compose = createComposeRule()
    private val command = MessageItem("command", "commandExecution", "", "npm run test", "TOOL_OUTPUT_PREVIEW", "", "completed", 0, emptyList(), true, 8192)
    private val file = MessageItem("file", "fileChange", "", "", "", "", "completed", null, listOf(Change("src/ui.kt", "+FILE_DIFF_PREVIEW")))
    private val session = Session("fixture-ui", "fixture", "专注于对话", "RUNNING", "turn", "default", 0)
    private val conversation = Conversation(listOf(Turn("turn", "inProgress", listOf(
        MessageItem("user", "userMessage", "检查修改，然后继续。", "", "", "", "", null, emptyList()),
        command, file,
    ))), Observation("running", 0, "gateway", "reasoning"), emptyList(), emptyList())

    @Test fun detailsStartCollapsedAndOnlyExplicitFullButtonDownloads() {
        var downloads = 0
        compose.setContent { HapiStyle(false) { Column(Modifier.padding(16.dp)) {
            ToolCard(command, onLoadFull = { downloads++ })
            ToolCard(file)
        } } }
        compose.onNodeWithTag("tool-command").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已收起"))
        compose.onNodeWithTag("tool-file").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已收起"))
        compose.onNodeWithText("TOOL_OUTPUT_PREVIEW").assertDoesNotExist()
        compose.onNodeWithText("+FILE_DIFF_PREVIEW").assertDoesNotExist()
        compose.onNodeWithTag("full-command").assertDoesNotExist()
        compose.onNodeWithTag("tool-command").performClick()
        compose.onNodeWithText("TOOL_OUTPUT_PREVIEW").assertIsDisplayed()
        compose.onNodeWithTag("full-command").assertIsDisplayed()
        assertEquals(0, downloads)
        compose.onNodeWithTag("full-command").performClick()
        assertEquals(1, downloads)
        captureTestScreen("11-tool-expanded")
        compose.onNodeWithTag("tool-command").performClick()
        compose.onNodeWithText("TOOL_OUTPUT_PREVIEW").assertDoesNotExist()
        compose.onNodeWithTag("tool-file").performClick()
        compose.onNodeWithText("+FILE_DIFF_PREVIEW").assertIsDisplayed()
    }

    @Test fun activityIsLastAndStopSurvivesWithoutTopPanel() {
        var stops = 0
        val state = mutableStateOf(session)
        val content = mutableStateOf(conversation)
        compose.setContent {
            var night by remember { mutableStateOf(false) }
            HapiStyle(night) { Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                ChatScreen(session.id, state.value, "Personal workspace", content.value, "", false, night,
                    onToggleNight = { night = !night }, onBack = {}, onRetry = {}, onSend = { null }, onStop = { stops++ },
                    onMode = {}, onImplement = {}, onApproval = { _, _ -> }, onAnswer = { _, _ -> })
            } }
        }
        compose.onNodeWithTag("transcript").performScrollToNode(hasTestTag("transcript-activity"))
        compose.onNodeWithTag("transcript-activity").assertTextEquals("正在思考")
        compose.onNodeWithText("执行中").assertDoesNotExist()
        compose.onNodeWithText("更新于", substring = true).assertDoesNotExist()
        assertTrue(compose.onNodeWithTag("transcript-activity").fetchSemanticsNode().boundsInRoot.top >=
            compose.onNodeWithTag("tools-command").fetchSemanticsNode().boundsInRoot.bottom)
        compose.onNodeWithContentDescription("停止任务").assertIsEnabled().performClick()
        assertEquals(1, stops)
        compose.waitForIdle()
        captureTestScreen("12-transcript-light-collapsed")
        compose.revealChatHeader()
        compose.onNodeWithContentDescription("切换深夜背景").performClick()
        compose.onNodeWithContentDescription("切换浅色背景").assertExists()
        compose.waitForIdle()
        captureTestScreen("13-transcript-night-collapsed")
        compose.runOnIdle { state.value = session.copy(status = "WAITING_USER") }
        compose.onNodeWithTag("transcript-activity").assertTextEquals("等待你的回答")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "静态状态"))
        compose.runOnIdle {
            state.value = session.copy(status = "COMPLETED", activeTurnId = null)
            content.value = conversation.copy(observation = conversation.observation.copy(state = "completed"))
        }
        compose.onNodeWithTag("transcript-activity").assertDoesNotExist()
        compose.onNodeWithContentDescription("停止任务").assertDoesNotExist()
        compose.onNodeWithContentDescription("发送消息").assertExists()
    }

    @Test fun sweepChangesGlyphsAndPausesInBackground() {
        // Pause BEFORE composition: the test harness cancels infinite animations
        // started under autoAdvance, and pausing afterwards cannot restart them.
        compose.mainClock.autoAdvance = false
        val owner = object : LifecycleOwner {
            val registry = LifecycleRegistry(this)
            override val lifecycle: Lifecycle get() = registry
        }
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.setContent { CompositionLocalProvider(LocalLifecycleOwner provides owner) {
            HapiStyle(false) { ActivityFooter(TranscriptActivity("正在运行命令", true)) }
        } }
        compose.mainClock.advanceTimeByFrame()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithTag("transcript-activity").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在处理，明亮扫光"))
        val first = compose.onNodeWithTag("transcript-activity").captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(1450)
        compose.waitForIdle()
        val second = compose.onNodeWithTag("transcript-activity").captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until first.height) for (x in 0 until first.width) if (first[x, y] != second[x, y]) changed++
        assertTrue("The text light must visibly move", changed > 20)
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.STARTED }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithTag("transcript-activity").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "静态状态"))
        compose.runOnUiThread { owner.registry.currentState = Lifecycle.State.RESUMED }
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        compose.onNodeWithTag("transcript-activity").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "正在处理，明亮扫光"))
        compose.mainClock.autoAdvance = true
    }
}
