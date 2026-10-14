package dev.codex.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred

@RunWith(AndroidJUnit4::class)
class TaskRecallUiTest {
    @get:Rule(order = 0) val timeout: Timeout = Timeout.builder().withTimeout(120, TimeUnit.SECONDS).build()
    @get:Rule(order = 1) val compose = createComposeRule()
    private fun message(id: String, type: String = "agentMessage", text: String = "回复 $id\n任务仍在执行。") =
        MessageItem(id, type, text, "", "", "", "completed", null, emptyList())
    private val task = message("original-task", "userMessage", "检查玻璃界面\n\n保留第二段和换行。")
    private fun snapshot(items: List<MessageItem>, latest: MessageItem = task, older: Boolean = true) = Conversation(
        listOf(Turn("t", "completed", items)), Observation("completed", 0, "gateway", ""), emptyList(), emptyList(),
        hasOlder = older, latestUserMessage = SyncEntry(latest.id, "r", "t", "completed", latest), taskReferenceSupported = true,
        capabilities = ConversationCapabilities(mode = true))

    @Test fun taskOutsideWindowReadsWithoutHistoryThenExplicitlyFindsThreePagesAtATime() {
        val content = mutableStateOf(snapshot((1..25).map { message("r$it") }))
        var pages = 0; var fullReads = 0
        compose.setContent { HapiStyle(true) {
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                ChatScreen("recall", Session("recall", "fixture", "任务回看验收", "COMPLETED", null, "default", 0), "fixture",
                    content.value, "", false, true, {}, {}, {}, { null }, {}, {}, {}, { _, _ -> }, { _, _ -> },
                    onLoadOlder = {
                        pages++
                        val older = if (pages == 4) listOf(task) else listOf(message("older-$pages"))
                        content.value = content.value.copy(turns = listOf(Turn("t", "completed", older + content.value.turns.single().items)))
                        content.value
                    }, onLoadTask = { fullReads++; it })
            }
        } }
        compose.onNodeWithTag("task-recall-strip").assertIsDisplayed().performClick()
        compose.onNodeWithTag("task-recall-original").assertTextEquals(task.text)
        assertEquals(0, pages); assertEquals(0, fullReads)
        captureTestScreen("56-task-recall-night-original")
        compose.runOnIdle { content.value = content.value.copy(latestUserMessage = SyncEntry("desktop", "r", "t", "completed", message("desktop", "userMessage", "电脑新任务"))) }
        compose.onNodeWithTag("task-recall-original").assertTextEquals(task.text)
        compose.onNodeWithText("跳到这条消息").performClick()
        compose.waitUntil(10000) { pages == 3 }
        compose.onNodeWithText("继续查找").assertIsDisplayed().performClick()
        compose.waitUntil(10000) { pages == 4 }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("task-recall-panel").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("message-${task.id}").assertIsDisplayed()
        compose.onNodeWithTag("transcript-navigation").assertIsDisplayed()
        captureTestScreen("57-task-recall-jumped")
        compose.onNodeWithTag("transcript-navigation").performClick()
        compose.onNodeWithTag("message-r25").assertIsDisplayed()
    }

    @Test fun decisionsUseReadablePaletteAndKeepAnswersWhileBusy() {
        val night = mutableStateOf(false); val busy = mutableStateOf(false)
        var submitted: Map<String, String> = emptyMap()
        val request = QuestionRequest("q", listOf(Question("mode", "选择方案", "说明文字也需要清晰可读",
            listOf(QuestionOption("推荐方案", "推荐说明"), QuestionOption("备用方案", "备用说明")), false, true)))
        compose.setContent { HapiStyle(night.value) {
            val p = LocalAppPalette.current
            fun contrast(a: Color, b: Color): Float {
                val x = a.luminance(); val y = b.luminance()
                return (maxOf(x, y) + .05f) / (minOf(x, y) + .05f)
            }
            SideEffect {
                assertTrue(contrast(p.ink, p.questionSurface) >= 4.5f)
                assertTrue(contrast(p.ink, p.warningSurface) >= 4.5f)
                assertTrue(contrast(p.muted, p.questionSurface) >= 3f)
            }
            Column(Modifier.fillMaxSize().background(p.background).windowInsetsPadding(WindowInsets.safeDrawing).padding(14.dp)) {
                QuestionCard(request, busy.value) { _, answers -> submitted = answers; busy.value = true }
                Spacer(Modifier.height(12.dp))
                ApprovalCard(Approval("a", "t", "执行命令", "合成请求", true), "t", busy.value) { _, _ -> }
            }
        } }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; busy.value = false }
            compose.onNodeWithText("推荐方案").performClick()
            captureTestScreen("58-decisions-${if (dark) "night" else "light"}")
            compose.onNodeWithText("提交").performClick()
            compose.onNodeWithText("备用方案").assertIsNotEnabled()
            assertEquals("推荐方案", submitted["mode"])
            compose.runOnIdle { busy.value = false }
            compose.onNodeWithText("推荐方案").assertIsSelected()
        }
    }

    @Test fun compactModeHasSmallSurfaceButRealEdgeClickAndBottomGutter() {
        val dark = mutableStateOf(false); val large = mutableStateOf(false)
        var density = 1f
        compose.setContent {
            val d = LocalDensity.current.density
            SideEffect { density = d }
            CompositionLocalProvider(LocalDensity provides Density(d, if (large.value) 1.5f else 1f)) {
                HapiStyle(dark.value) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        ChatScreen("compact-mode", Session("compact-mode", "fixture", "比例验收", "COMPLETED", null, "plan", 0),
                            "fixture", snapshot(listOf(task), older = false), "", false, dark.value,
                            {}, {}, {}, { null }, {}, {}, {}, { _, _ -> }, { _, _ -> })
                    }
                }
            }
        }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; large.value = night }
            compose.waitForIdle()
            val bounds = compose.onNodeWithTag("composer-mode").fetchSemanticsNode().boundsInRoot
            val surface = compose.onNodeWithTag("compact-action-surface", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val shell = compose.onNodeWithTag("composer-shell").fetchSemanticsNode().boundsInRoot
            assertTrue(bounds.height / density >= 48f)
            assertTrue(surface.height < bounds.height)
            assertTrue((shell.bottom - surface.bottom) / density >= 10f)
            captureTestScreen("59-composer-proportions-${if (night) "night-large" else "light"}")
            compose.onRoot().performTouchInput { click(Offset(bounds.center.x, bounds.top + 2f * density)) }
            compose.onNodeWithText("Agent · 直接分析和执行").assertIsDisplayed().performClick()
        }
    }

    @Test fun originalIsFetchedOnlyOnOpenAndLegacyIsExplicit() {
        val open = mutableStateOf(false)
        val full = CompletableDeferred<MessageItem>()
        var reads = 0
        compose.setContent { HapiStyle(true) {
            if (open.value) TaskRecallDialog(TaskReference(task.copy(text = "预览", truncated = true)), emptyList(), true,
                false, "", { open.value = false }, { reads++; full.await() }, {}, {})
        } }
        compose.waitForIdle(); assertEquals(0, reads)
        compose.runOnIdle { open.value = true }
        compose.waitUntil(5000) { reads == 1 }
        compose.onNodeWithText("旧 Gateway 无法确认窗口外的最新任务；当前仅展示已加载记录，请升级。").assertIsDisplayed()
        compose.runOnIdle { full.complete(task) }
        compose.onNodeWithTag("task-recall-original").assertTextEquals(task.text)
        compose.onNodeWithText("关闭").performClick()
        assertEquals(1, reads)
    }

    @Test fun secretAnswerRequiresDesktop() {
        val busy = mutableStateOf(false)
        var submitted = ""
        compose.setContent { HapiStyle(true) {
            QuestionCard(QuestionRequest("secret", listOf(Question("answer", "私密答案", "合成测试", emptyList(), true, true))), busy.value) { _, answers -> submitted = answers.getValue("answer"); busy.value = true }
        } }
        compose.onNodeWithText("需要在电脑回答").assertIsDisplayed()
        compose.onNodeWithText("此问题含私密输入，请在电脑回答。").assertIsDisplayed()
        compose.onNodeWithTag("question-answer-answer").assertDoesNotExist()
        compose.onNodeWithText("提交").assertIsNotEnabled()
        assertEquals("", submitted)
    }

    @Test fun questionStatusHeadingsKeepDesktopRequiredOptionsReadable() {
        val status = mutableStateOf("desktop_required")
        val question = Question("choice", "选择", "原题", listOf(QuestionOption("方案一", "完整说明")), false, true)
        compose.setContent { HapiStyle(false) {
            QuestionCard(QuestionRequest("r", listOf(question), kind = "async", status = status.value, notice = "请在电脑回答"), false) { _, _ -> }
        } }
        compose.onNodeWithText("需要在电脑回答").assertIsDisplayed()
        compose.onNodeWithText("方案一").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("完整说明").assertIsDisplayed()
        for ((value, title) in listOf("unknown" to "提交结果待核对", "accepted" to "Desktop已记录答案", "closed" to "已处理或失效")) {
            compose.runOnIdle { status.value = value }
            compose.onNodeWithText(title).assertIsDisplayed()
            compose.onNodeWithText("提交").assertIsNotEnabled()
        }
    }

    @Test fun multipleQuestionsKeepDescriptionsAndSubmitLiteralOtherAnswer() {
        var submitted: Map<String, String>? = null
        val request = QuestionRequest("r", listOf(
            Question("first", "选择", "选择一种方式", listOf(QuestionOption("推荐", "推荐项说明")), false, true),
            Question("second", "原因", "请写原因", emptyList(), false, true)))
        compose.setContent { HapiStyle(false) { QuestionCard(request, false) { _, answers -> submitted = answers } } }
        compose.onNodeWithText("推荐项说明").assertIsDisplayed()
        compose.onNodeWithText("第 2 题 / 共 2 题 · 原因").assertIsDisplayed()
        compose.onNodeWithText("提交").assertIsNotEnabled()
        compose.onNodeWithText("其他答案").performClick()
        compose.onNodeWithTag("question-answer-first").performTextInput("__other__")
        compose.onNodeWithTag("question-answer-second").performTextInput("合成理由")
        compose.onNodeWithText("提交").performClick()
        assertEquals(mapOf("first" to "__other__", "second" to "合成理由"), submitted)
    }
}
