package dev.codex.remote

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** All sends are synthetic deferred receipts. No live account, relay or Codex task is touched. */
@RunWith(AndroidJUnit4::class)
class GlassSendTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    private val session = Session("glass-send", "fixture", "轻盈一点，专注一点", "COMPLETED", null, "default", 0)
    private fun item(id: String, type: String, text: String) = MessageItem(id, type, text, "", "", "", "", null, emptyList())
    private val original = Conversation(listOf(Turn("old", "completed", listOf(
        item("old-user", "userMessage", "给我留一点空间，让想法自然流动。"),
        item("old-reply", "agentMessage", "## 清晰，才是轻盈\n\n你的消息靠右，我的回复靠左。\n\n淡淡的颜色区分角色，文字保持清晰。输入框浮在内容之上，让背景的光与色自然透过。")))), Observation("completed", 0, "gateway", "agentMessage"), emptyList(), emptyList())
    @After fun restorePreferences() { prefs.edit().remove("chat_style").remove("glass_clarity").remove("reduce_motion").apply() }

    private fun launch(content: State<Conversation> = mutableStateOf(original), send: suspend (String) -> String? = { "sent-turn" }) {
        compose.setContent {
            var dark by remember { mutableStateOf(false) }
            HapiStyle(dark) { Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                ChatScreen(session.id, session, "Personal workspace", content.value, "", false, dark,
                    onToggleNight = { dark = !dark }, onBack = {}, onRetry = {}, onSend = send, onStop = {}, onMode = {},
                    onImplement = {}, onApproval = { _, _ -> }, onAnswer = { _, _ -> })
            } }
        }
    }

    @Test fun rotatesOnlyWhileSendingThenKeepsEditorAndExactlyOneAcceptedMessage() {
        compose.mainClock.autoAdvance = false
        val gate = CompletableDeferred<String?>()
        val content = mutableStateOf(original)
        var submissions = 0
        launch(content) { submissions++; gate.await() }
        compose.mainClock.advanceTimeBy(100)
        compose.onNode(hasSetTextAction()).performTextInput("把这一刻，轻轻放进对话。")
        compose.onNodeWithContentDescription("发送消息").performClick()
        compose.mainClock.advanceTimeBy(180)
        compose.onNodeWithContentDescription("正在发送").assertIsNotEnabled()
        val first = compose.onNodeWithTag("send-orbit", useUnmergedTree = true).captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(420)
        val second = compose.onNodeWithTag("send-orbit", useUnmergedTree = true).captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until first.height) for (x in 0 until first.width) if (first[x, y] != second[x, y]) changed++
        assertTrue("Sending orbit must actually rotate", changed > 12)
        captureTestScreen("17-glass-sending")
        compose.runOnIdle { gate.complete("sent-turn") }
        compose.mainClock.autoAdvance = true
        compose.waitUntil(5000) { compose.onAllNodesWithTag("sent-message").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertEquals(1, submissions)
        compose.onNodeWithTag("sent-message").assertIsDisplayed()
        compose.onNodeWithText("已发送 · 同步中").assertIsDisplayed()
        val sent = compose.onNodeWithTag("sent-message").fetchSemanticsNode().boundsInRoot
        val shell = compose.onNodeWithTag("composer-shell").fetchSemanticsNode().boundsInRoot
        assertTrue("Accepted message must not hide below the collapsed composer", sent.bottom <= shell.top + 3f)
        assertTrue("Even a short conversation should settle above the dock, not at the top", shell.top - sent.bottom < 64f)
        captureTestScreen("18-glass-accepted-collapsed")
        compose.runOnIdle { content.value = original.copy(turns = original.turns + Turn("sent-turn", "inProgress", listOf(item("real-user", "userMessage", "把这一刻，轻轻放进对话。")))) }
        compose.waitForIdle()
        compose.onAllNodesWithTag("sent-message").assertCountEquals(1)
        compose.onNodeWithText("已发送 · 同步中").assertDoesNotExist()
        compose.onNodeWithTag("composer-editor").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
    }

    @Test fun failureRetainsDraftAndDoesNotCollapseOrInventSuccess() {
        launch { throw java.io.IOException("offline") }
        compose.onNode(hasSetTextAction()).performTextInput("失败也不能丢失这段草稿")
        compose.onNodeWithContentDescription("发送消息").performClick()
        compose.waitForIdle()
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("失败也不能丢失这段草稿")))
        compose.onNodeWithContentDescription("收起到右侧").assertIsDisplayed()
        compose.onNodeWithTag("sent-message").assertDoesNotExist()
        compose.onNodeWithContentDescription("发送消息").assertIsEnabled()
    }

    @Test fun stylesPersistAndBothThemesKeepDistinctMessageSurfaces() {
        launch()
        for (style in ChatStyle.entries) {
            compose.runOnIdle { prefs.edit().putString("chat_style", style.name).commit() }
            compose.waitForIdle()
            assertEquals(style, readChatAppearance(prefs).style)
            assertNotEquals(conversationColors(style, false).mine, conversationColors(style, false).reply)
            assertNotEquals(conversationColors(style, true).mine, conversationColors(style, true).reply)
            captureTestScreen("19-style-${style.name}-light")
            compose.revealChatHeader()
            compose.onNodeWithContentDescription("切换深夜背景").performClick()
            compose.waitForIdle()
            captureTestScreen("20-style-${style.name}-night")
            compose.revealChatHeader()
            compose.onNodeWithContentDescription("切换浅色背景").performClick()
        }
    }

    @Test fun settingsExposeStyleClarityAndReducedMotion() {
        compose.setContent { HapiStyle(false) { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { ChatAppearanceSettings() } } }
        compose.onNodeWithContentDescription("配色：鼠尾草").performScrollTo().performClick()
        compose.onNodeWithContentDescription("配色：鼠尾草，已选中").assertIsDisplayed()
        compose.onNodeWithContentDescription("玻璃清透程度").performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(.95f) }
        compose.onNodeWithContentDescription("减少动画").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(.95f, readChatAppearance(prefs).clarity, .02f)
        assertTrue(readChatAppearance(prefs).reduceMotion)
        captureTestScreen("21-appearance-settings")
    }

    @Test fun actualBackdropColorsPassThroughGlassAndChangeWithoutRebuildingComposer() {
        val source = mutableStateOf(Color(0xFF72A8D2))
        compose.setContent { HapiStyle(false) {
            val backdrop = rememberGraphicsLayer()
            Box(Modifier.size(220.dp).drawWithContent { drawContent() }) {
                Box(Modifier.fillMaxSize().drawWithContent { backdrop.record { this@drawWithContent.drawContent() }; drawLayer(backdrop) }.background(source.value))
                GlassMaterial(backdrop, Offset.Zero, Modifier.fillMaxSize())
            }
        } }
        compose.waitForIdle()
        val before = compose.onNodeWithTag("glass-material").captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }
        compose.runOnIdle { source.value = Color(0xFFD6A675) }
        compose.waitForIdle()
        val after = compose.onNodeWithTag("glass-material").captureToImage().toPixelMap().let { it[it.width / 2, it.height / 2] }
        assertTrue("Backdrop blue must pass through, not be covered by a white card", before.blue - before.red > .15f)
        assertTrue("Backdrop changes must remain visible through the material", after.red - before.red > .18f && before.blue - after.blue > .18f)
    }

    @Test fun acceptedWashMovesOnceAndReducedMotionKeepsItStatic() {
        compose.mainClock.autoAdvance = false
        val reduced = mutableStateOf(false)
        compose.setContent { HapiStyle(false) { CompositionLocalProvider(LocalChatAppearance provides ChatAppearance(reduceMotion = reduced.value)) {
            ChatMessageBubble(item("accepted", "userMessage", "一道轻柔的光，确认消息已经送达。"), receiptKey = "receipt")
        } } }
        compose.mainClock.advanceTimeBy(30)
        val before = compose.onNodeWithTag("sent-message").captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(600)
        val during = compose.onNodeWithTag("sent-message").captureToImage().toPixelMap()
        var changed = 0
        for (y in 0 until before.height) for (x in 0 until before.width) if (before[x, y] != during[x, y]) changed++
        assertTrue("Accepted message wash must actually move", changed > 20)
        compose.runOnIdle { reduced.value = true }
        compose.mainClock.advanceTimeBy(40)
        val static = compose.onNodeWithTag("sent-message").captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(1200)
        val later = compose.onNodeWithTag("sent-message").captureToImage().toPixelMap()
        for (y in 0 until static.height) for (x in 0 until static.width) assertEquals(static[x, y], later[x, y])
    }
}
