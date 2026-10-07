package dev.codex.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ConversationTimeUiTest {
    @get:Rule(order = 0) val timeout: Timeout = Timeout.builder().withTimeout(120, TimeUnit.SECONDS).build()
    @get:Rule(order = 1) val compose = createComposeRule()
    private fun task(id: String, round: Int, completed: Boolean = true) = MessageItem(id, "userMessage",
        "合成任务 $round\n保留原文换行", "", "", "", "", null, emptyList(), timestampMs = 1_790_674_800_000,
        roundNumber = round, turnStartedAtMs = if (completed) 1_790_674_800_000 else System.currentTimeMillis() - 65_000, turnEndedAtMs = if (completed) 1_790_674_865_000 else null,
        turnDurationMs = if (completed) 65_000 else null, turnCompleted = completed)

    @Test fun linkedSubtitleFitsGlassAtLargeFontAndOpensFromCompactHeader() {
        val large = mutableStateOf(false); val expanded = mutableStateOf(false); val night = mutableStateOf(false)
        var clicked = 0; var density = 1f
        compose.setContent { HapiStyle(night.value) {
            val d = LocalDensity.current.density
            density = d
            CompositionLocalProvider(LocalDensity provides Density(d, if (large.value) 1.6f else 1f),
                LocalChatAppearance provides ChatAppearance(reduceMotion = true)) {
                Box(Modifier.fillMaxSize().background(LocalAppPalette.current.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    val backdrop = rememberGraphicsLayer()
                    Box(Modifier.fillMaxSize().drawWithContent {
                        backdrop.record { this@drawWithContent.drawContent() }; drawLayer(backdrop)
                    }.background(LocalAppPalette.current.background))
                    GlassChatHeader("这是一个很长的对话标题 · 布局验收", expanded.value, night.value, { expanded.value = true }, {}, {},
                        backdrop, Offset.Zero, taskSubtitle = "共 57 轮 · [图片] 检查透明头部与很长的任务摘要",
                        onTaskClick = { clicked++ })
                }
            }
        } }
        for (big in listOf(false, true)) for (open in listOf(false, true)) {
            compose.runOnIdle { large.value = big; expanded.value = open; night.value = big }
            val subtitle = compose.onNodeWithTag("header-subtitle", useUnmergedTree = true)
            subtitle.assertTextContains("共 57 轮 · [图片]", substring = true)
            val results = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            subtitle.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(results) }
            assertEquals(1, results.single().lineCount)
            assertEquals(TextDecoration.Underline, results.single().layoutInput.style.textDecoration)
            val text = subtitle.fetchSemanticsNode().boundsInRoot
            val glass = compose.onNodeWithTag("chat-header-glass", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            compose.waitForIdle()
            captureTestScreen("60-task-header-${if (big) "night-large" else "light"}-${if (open) "open" else "compact"}")
            // didOverflowHeight also includes didExceedMaxLines (the intentional one-line
            // ellipsis). Compare the rendered line height to its allocation instead.
            val layout = results.single()
            assertTrue("Subtitle line cannot be vertically clipped: ${layout.multiParagraph.height} > ${layout.size.height}",
                layout.multiParagraph.height <= layout.size.height + 1f)
            assertTrue("Subtitle has a real bottom gutter", text.top >= glass.top && text.bottom + 4f * density <= glass.bottom)
            assertTrue("Large text must grow the glass itself", glass.height / density >= if (big) if (open) 75f else 63f else if (open) 55f else 51f)
            val link = compose.onNodeWithTag("task-recall-strip")
            assertTrue(link.fetchSemanticsNode().boundsInRoot.height / density >= 48f)
            link.performClick()
            compose.waitForIdle()
        }
        assertEquals(4, clicked)
    }

    @Test fun completedTimesStayStaticWhileRunningTimeSweepsAndReducedMotionStopsIt() {
        val finished = mutableStateOf(false); val reduced = mutableStateOf(false); val night = mutableStateOf(false)
        compose.setContent { HapiStyle(night.value) {
            CompositionLocalProvider(LocalChatAppearance provides ChatAppearance(reduceMotion = reduced.value)) {
                val item = task("time", 4, finished.value)
                Column(Modifier.fillMaxSize().background(LocalAppPalette.current.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    ChatMessageBubble(item, processing = !finished.value)
                    TurnTime(item, running = !finished.value)
                    TurnTime(task("past", 3), running = true, detail = true)
                }
            }
        } }
        fun light(tag: String, moving: Boolean) = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .assert(SemanticsMatcher.expectValue(ProcessingLightState, if (moving) "moving" else "static"))
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; finished.value = false; reduced.value = false }
            light("content-time-time", true); light("turn-time-time", true); light("turn-time-past", false)
            captureTestScreen("61-conversation-times-${if (dark) "night" else "light"}-running")
            compose.runOnIdle { finished.value = true }
            light("content-time-time", false); light("turn-time-time", false)
            compose.onNodeWithTag("turn-time-time").assertTextContains("第 4 轮 · 用时 1分5秒")
            captureTestScreen("62-conversation-times-${if (dark) "night" else "light"}-finished")
            compose.runOnIdle { finished.value = false; reduced.value = true }
            light("content-time-time", false); light("turn-time-time", false)
        }
    }

    @Test fun taskDirectoryLoadsOnlyOnOpenThenPagesAndKeepsSelectedOriginal() {
        val open = mutableStateOf(false); val night = mutableStateOf(false)
        var reads = 0; val anchors = mutableListOf<String?>()
        val latest = task("latest", 3).copy(text = "", imageCount = 1)
        compose.setContent { HapiStyle(night.value) {
            CompositionLocalProvider(LocalChatAppearance provides ChatAppearance(reduceMotion = true)) {
                if (open.value) TaskRecallDialog(TaskReference(latest), emptyList(), false, false, "", { open.value = false },
                    { it }, {}, {}, onLoadPage = { before ->
                        reads++; anchors += before
                        if (before == null) TaskHistoryPage(listOf(latest, task("second", 2)), "second", 3)
                        else TaskHistoryPage(listOf(task("first", 1)), null, 3)
                    })
            }
        } }
        compose.waitForIdle(); assertEquals(0, reads)
        compose.runOnIdle { open.value = true }
        compose.waitUntil(5000) { reads == 1 }
        compose.onNodeWithTag("task-recall-original").assertTextEquals("[图片]")
        compose.onNodeWithText("加载更早任务").performScrollTo().performClick()
        compose.waitUntil(5000) { reads == 2 }
        assertEquals(listOf(null, "second"), anchors)
        compose.onNodeWithTag("task-entry-first").performScrollTo().performClick()
        compose.onNodeWithTag("task-recall-original").assertTextEquals("合成任务 1\n保留原文换行")
        captureTestScreen("63-task-directory-light")
        compose.runOnIdle { night.value = true }
        compose.waitForIdle()
        captureTestScreen("64-task-directory-night")
        compose.onNodeWithText("关闭").performClick()
        assertEquals(2, reads)
    }

    @Test fun transcriptPlacesDurationAfterFinalReplyAndTimesEveryContentHeading() {
        val user = task("request", 12)
        val command = user.copy(id = "command", type = "commandExecution", text = "", command = "echo synthetic", status = "completed")
        val answer = user.copy(id = "answer", type = "agentMessage", text = "任务已经完成。", turnLastItem = true)
        val session = Session("timed", "fixture", "头部任务与消息时间", "COMPLETED", null, "default", 0)
        val conversation = Conversation(listOf(Turn("t", "completed", listOf(user, command, answer))),
            Observation("completed", 0, "gateway", ""), emptyList(), emptyList(), latestUserMessage = SyncEntry(user.id, "r", "t", "completed", user),
            taskReferenceSupported = true, userMessageCount = 12)
        compose.setContent { HapiStyle(true) {
            CompositionLocalProvider(LocalChatAppearance provides ChatAppearance(reduceMotion = true)) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    ChatScreen(session.id, session, "fixture", conversation, "", false, true, {}, {}, {}, { null }, {}, {}, {}, { _, _ -> }, { _, _ -> })
                }
            }
        } }
        compose.onNodeWithTag("content-time-request", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("content-time-command", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("content-time-answer", useUnmergedTree = true).assertExists()
        val footer = compose.onNodeWithTag("turn-time-answer").assertTextEquals("第 12 轮 · 用时 1分5秒")
        val answerBounds = compose.onNodeWithTag("message-answer").fetchSemanticsNode().boundsInRoot
        assertTrue(footer.fetchSemanticsNode().boundsInRoot.top >= answerBounds.bottom)
        compose.onNodeWithTag("turn-time-request").assertDoesNotExist()
        captureTestScreen("65-timed-transcript-integrated")
        compose.onNodeWithTag("task-recall-strip").performTouchInput { click() }
        compose.onNodeWithTag("task-recall-original").assertTextEquals(user.text)
        compose.onNodeWithText("关闭").performClick()
    }

    @Test fun redesignedTaskReaderIsWideNewestFirstAndNeverOverlaysLongText() {
        val night = mutableStateOf(false); val large = mutableStateOf(false)
        val latest = task("reader-latest", 24).copy(text = (1..36).joinToString("\n") { "第 $it 行 · 合成任务，验证完整换行和长文阅读。" })
        val earlier = task("reader-earlier", 23)
        var density = 1f
        compose.setContent { HapiStyle(night.value) {
            val d = LocalDensity.current.density; density = d
            val p = LocalAppPalette.current
            val surface = p.ink.copy(alpha = .035f).compositeOver(p.card)
            val text = p.ink.copy(alpha = .74f).compositeOver(surface)
            val contrast = (maxOf(text.luminance(), surface.luminance()) + .05f) /
                (minOf(text.luminance(), surface.luminance()) + .05f)
            SideEffect { assertTrue("Small reader metadata stays readable", contrast >= 4.5f) }
            CompositionLocalProvider(LocalDensity provides Density(d, if (large.value) 1.6f else 1f),
                LocalChatAppearance provides ChatAppearance(reduceMotion = true)) {
                TaskRecallDialog(TaskReference(latest), listOf(earlier), false, false, "", {}, { it }, {}, {},
                    onLoadPage = { TaskHistoryPage(listOf(latest, earlier), null, 24) })
            }
        } }
        for (dark in listOf(false, true)) {
            compose.runOnIdle { night.value = dark; large.value = dark }
            compose.onNodeWithTag("task-entry-reader-latest").performScrollTo().assertIsDisplayed()
            compose.onNodeWithTag("task-recall-original").assertTextEquals(latest.text)
            val textLayouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            compose.onNodeWithTag("task-recall-original").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(textLayouts) }
            assertEquals("Dialog must render the requested accessibility font scale", if (dark) 1.6f else 1f,
                textLayouts.single().layoutInput.density.fontScale, .01f)
            val shell = compose.onNodeWithTag("task-detail-surface").fetchSemanticsNode().boundsInRoot
            val viewport = compose.onNodeWithTag("task-recall-panel").fetchSemanticsNode().boundsInRoot
            val actions = compose.onNodeWithTag("task-detail-actions").fetchSemanticsNode().boundsInRoot
            assertTrue("Reading surface must be almost screen wide", shell.width / density >= 380f)
            assertTrue("Footer is outside the reading viewport", viewport.bottom <= actions.top + 1f)
            assertTrue("Close is above, never over, the body", compose.onNodeWithText("关闭").fetchSemanticsNode().boundsInRoot.bottom <= viewport.top)
            captureTestScreen("66-task-reader-${if (dark) "night-large" else "light"}-top")
            compose.onNodeWithTag("task-entry-reader-earlier").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("跳到这条消息").assertIsDisplayed()
            captureTestScreen("67-task-reader-${if (dark) "night-large" else "light"}-scrolled")
        }
        compose.onNodeWithTag("task-entry-reader-earlier").performClick()
        compose.onNodeWithTag("task-recall-original").assertTextEquals(earlier.text)
        compose.onNodeWithText("已选第 23 轮").assertIsDisplayed()
    }

    @Test fun readerSeparatesSentHeaderFromEndFooterAndKeepsCrossDayDates() {
        val dark = mutableStateOf(false); val big = mutableStateOf(false); val crossDay = mutableStateOf(false)
        val base = task("positioned-time", 7)
        compose.setContent { HapiStyle(dark.value) {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, if (big.value) 1.6f else 1f),
                LocalChatAppearance provides ChatAppearance(reduceMotion = true)) {
                // Reopen the synthetic record when its timestamps change, like reopening a historical task.
                key(crossDay.value) {
                    val item = if (crossDay.value) base.copy(turnEndedAtMs = base.turnEndedAtMs!! + 86_400_000,
                        turnDurationMs = 86_465_000) else base
                    TaskRecallDialog(TaskReference(item), emptyList(), false, false, "", {}, { it }, {}, {})
                }
            }
        } }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night; big.value = night }
            val round = compose.onNodeWithTag("task-round-${base.id}", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            val sentNode = compose.onNodeWithTag("task-sent-${base.id}", useUnmergedTree = true)
            val sent = sentNode.fetchSemanticsNode().boundsInRoot
            val endedNode = compose.onNodeWithTag("task-ended-${base.id}", useUnmergedTree = true)
            val ended = endedNode.fetchSemanticsNode().boundsInRoot
            val body = compose.onNodeWithTag("task-recall-original").fetchSemanticsNode().boundsInRoot
            val duration = compose.onNodeWithTag("task-duration-${base.id}").fetchSemanticsNode().boundsInRoot
            assertTrue("Sent time belongs to the header's right half", sent.left > round.right)
            assertTrue("Body separates the two timestamps", sent.bottom < body.top && ended.top > body.bottom)
            assertTrue("Duration and end have separate columns", duration.right < ended.left)
            sentNode.assertTextEquals("发送 ${clockText(base.timestampMs)}")
            endedNode.assertTextEquals("结束 ${clockText(base.turnEndedAtMs)}")
                .assert(SemanticsMatcher.expectValue(ProcessingLightState, "static"))
            compose.onNodeWithTag("task-end-date-${base.id}").assertDoesNotExist()
            captureTestScreen("68-task-time-columns-${if (night) "night-large" else "light"}")
        }
        compose.runOnIdle { crossDay.value = true }
        compose.onNodeWithTag("task-end-date-${base.id}").assertTextEquals(dateText(base.turnEndedAtMs!! + 86_400_000))
        captureTestScreen("69-task-time-cross-day-large")
    }
}
