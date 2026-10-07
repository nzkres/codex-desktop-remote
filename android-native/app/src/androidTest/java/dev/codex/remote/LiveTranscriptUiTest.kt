package dev.codex.remote

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class LiveTranscriptUiTest {
    @get:Rule val compose = createComposeRule()
    private fun reply(id: String, text: String) = MessageItem(id, "agentMessage", text, "", "", "", "", null, emptyList())
    private val session = Session("live-ui", "fixture", "看历史，也知道此刻的进展", "RUNNING", "live", "default", 0)
    private val original = Conversation(listOf(Turn("live", "inProgress", (0..35).map {
        reply("history-$it", "第 $it 段历史内容。\n\n保持阅读位置，不被新的消息打断。")
    })), Observation("running", 0, "gateway", "reasoning"), emptyList(), emptyList())

    @Test fun historyKeepsStatusAlignedWithEitherDockAndReturnSurvivesTaskCompletion() {
        val content = mutableStateOf(original)
        val current = mutableStateOf(session)
        compose.setContent { HapiStyle(false) { Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            ChatScreen(session.id, current.value, "合成验收 · 不连接真实任务", content.value, "", false, false,
                onToggleNight = {}, onBack = {}, onRetry = {}, onSend = { null }, onStop = {}, onMode = {},
                onImplement = {}, onApproval = { _, _ -> }, onAnswer = { _, _ -> })
        } } }
        compose.waitForIdle()
        compose.onNodeWithTag("transcript").performTouchInput { swipeDown(startY = height * .15f, endY = height * .62f) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("transcript-navigation").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("transcript-navigation").assertContentDescriptionEquals("正在执行，回到最新消息")
        var nav = compose.onNodeWithTag("transcript-navigation").fetchSemanticsNode().boundsInRoot
        var shell = compose.onNodeWithTag("composer-shell").fetchSemanticsNode().boundsInRoot
        assertTrue("Expanded composer must not cover the status button", nav.bottom <= shell.top)
        captureTestScreen("25-history-status-expanded")
        compose.onNodeWithContentDescription("收起到右侧").performClick()
        compose.waitForIdle()
        nav = compose.onNodeWithTag("transcript-navigation").fetchSemanticsNode().boundsInRoot
        shell = compose.onNodeWithTag("composer-shell").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(nav.top - shell.top) < 3f && abs(nav.bottom - shell.bottom) < 3f)
        assertTrue("Right dock and status must not overlap", nav.right < shell.left)
        captureTestScreen("26-history-status-right")
        compose.onNodeWithContentDescription("展开输入框").performClick()
        compose.onNodeWithContentDescription("收起到左侧").performClick()
        compose.waitForIdle()
        nav = compose.onNodeWithTag("transcript-navigation").fetchSemanticsNode().boundsInRoot
        shell = compose.onNodeWithTag("composer-shell").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(nav.center.y - shell.center.y) < 3f)
        assertTrue("Left dock and status must not overlap", nav.left > shell.right)
        captureTestScreen("27-history-status-left")

        val historical = SemanticsMatcher("historical message") { it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("message-history-") }
        val anchor = compose.onAllNodes(historical).fetchSemanticsNodes().first { it.boundsInRoot.top > 100f }
        val tag = anchor.config[SemanticsProperties.TestTag]
        val top = anchor.boundsInRoot.top
        compose.runOnIdle {
            content.value = original.copy(turns = listOf(original.turns.single().copy(items = original.turns.single().items +
                reply("latest", "新的回复不会把读者拉到底部。\n".repeat(65)))))
        }
        compose.waitForIdle()
        assertEquals(top, compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.top, 2f)
        compose.runOnIdle { current.value = session.copy(status = "WAITING_USER") }
        compose.onNodeWithTag("transcript-navigation").assertContentDescriptionEquals("等待你的回答，回到最新消息")
        compose.runOnIdle {
            current.value = session.copy(status = "COMPLETED", activeTurnId = null)
            content.value = content.value.copy(observation = original.observation.copy(state = "completed"))
        }
        compose.onNodeWithTag("transcript-navigation").assertContentDescriptionEquals("回到最新，回到最新消息").performClick()
        compose.waitUntil(8000) { compose.onAllNodesWithTag("transcript-navigation").fetchSemanticsNodes().isEmpty() }
        val latest = compose.onNodeWithTag("message-latest").fetchSemanticsNode().boundsInRoot
        shell = compose.onNodeWithTag("composer-shell").fetchSemanticsNode().boundsInRoot
        assertTrue("Even a multi-screen final reply ends above the composer", latest.bottom <= shell.top + 2f)
        assertTrue("The return must reach the end, not the top of the last message", shell.top - latest.bottom < 100f)
        compose.onNodeWithTag("transcript").performTouchInput { swipeDown(startY = height * .15f, endY = height * .65f) }
        compose.onNodeWithTag("transcript-navigation").assertIsDisplayed().performClick()
        compose.waitUntil(8000) { compose.onAllNodesWithTag("transcript-navigation").fetchSemanticsNodes().isEmpty() }
        captureTestScreen("28-history-return-to-latest")
    }

    @Test fun revealChangesRealGlyphPixelsWithoutReparsingOrRewritingMarkdown() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.runOnUiThread {
            val view = ReplyRevealTextView(context).apply { configureChatText(this); setTextColor(android.graphics.Color.BLACK) }
            val markwon = chatMarkdown(context)
            val initial = "历史文字。"
            val source = initial + "\n\n## 逐渐浮现\n\n" + "中文、English 和 👨‍👩‍👧‍👦 emoji 连续出现。".repeat(8) + "\n\n```sh\necho hello\n```"
            view.bind(markwon, initial, false) { null }
            view.bind(markwon, source, true) { initial }
            val field = ReplyRevealTextView::class.java.getDeclaredField("animator").apply { isAccessible = true }
            val animator = field.get(view) as ValueAnimator
            animator.pause()
            fun render(time: Long): Pair<Bitmap, Int> {
                animator.currentPlayTime = time
                view.measure(View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.AT_MOST))
                view.layout(0, 0, view.measuredWidth, view.measuredHeight)
                val bitmap = Bitmap.createBitmap(720, 4000, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                return bitmap to view.measuredHeight
            }
            val early = render(90)
            val earlyCharacters = view.visibleCharacters
            val later = render(700)
            assertTrue(view.isRevealing)
            assertTrue(view.visibleCharacters > earlyCharacters)
            assertTrue("Reply grows without preallocating an empty screen", later.second > early.second)
            var changed = 0
            for (y in 0 until 1600) for (x in 0 until 720) if (early.first.getPixel(x, y) != later.first.getPixel(x, y)) changed++
            assertTrue("Actual rendered glyph pixels must change", changed > 100)
            assertEquals(markwon.toMarkdown(source).toString(), view.text.toString())
            // Motion disabled mid-stream must reveal everything immediately, without changing the source.
            view.bind(markwon, source, false) { error("Same revision must not be claimed again") }
            assertFalse(view.isRevealing)
            assertEquals(view.text.length, view.visibleCharacters)
            early.first.recycle(); later.first.recycle()
        }
    }

    @Test fun unicodeBoundariesAndRecyclingNeverReplayOldContent() {
        compose.runOnUiThread {
            assertArrayEquals(intArrayOf(0, 1, 12, 14, 18, 19), characterBoundaries("A👨‍👩‍👧‍👦e\u0301🇨🇳B"))
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val markwon = chatMarkdown(context)
            val tracker = TranscriptSequence()
            tracker.accept(original, true, android.os.SystemClock.uptimeMillis())
            val source = "**新消息** 👨‍👩‍👧‍👦 e\u0301\n下一行。"
            val next = original.copy(turns = listOf(original.turns.single().copy(items = original.turns.single().items + reply("new", source))))
            tracker.accept(next, true, android.os.SystemClock.uptimeMillis())
            val first = ReplyRevealTextView(context)
            first.bind(markwon, source, true, tracker.frame().reveals["new"]) { null }
            assertTrue(first.isRevealing)
            first.finishReveal()
            tracker.advance(android.os.SystemClock.uptimeMillis() + 5000)
            val recycled = ReplyRevealTextView(context)
            recycled.bind(markwon, source, true, tracker.frame().reveals["new"]) { null }
            assertFalse("Scrolling back to a recycled row must not replay it", recycled.isRevealing)
            assertEquals(markwon.toMarkdown(source).toString(), recycled.text.toString())
        }
    }

    @Test fun liveReplyActuallyAnimatesInsideChatAndFollowsItsGrowingHeight() {
        val content = mutableStateOf(original)
        val current = mutableStateOf(session)
        lateinit var root: View
        compose.setContent {
            root = LocalActivity.current!!.window.decorView
            HapiStyle(false) { Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                ChatScreen(session.id, current.value, "实时排版验收", content.value, "", false, false,
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
        val sawReveal = AtomicBoolean(false)
        val listener = ViewTreeObserver.OnDrawListener {
            if (views(root).any { it.text.startsWith("实时显露验收") && it.isRevealing }) sawReveal.set(true)
        }
        compose.runOnUiThread { root.viewTreeObserver.addOnDrawListener(listener) }
        try {
            compose.runOnIdle {
                content.value = original.copy(turns = listOf(original.turns.single().copy(items = original.turns.single().items +
                    reply("live-growth", "实时显露验收。中文与 English 连续展开。\n\n".repeat(18)))))
            }
            compose.waitUntil(6000) { sawReveal.get() }
            compose.waitUntil(6000) {
                var revealing = true
                compose.runOnUiThread { revealing = views(root).any { it.isRevealing } }
                !revealing
            }
            compose.waitForIdle()
            compose.onNodeWithTag("transcript-navigation").assertDoesNotExist()
            compose.onNodeWithTag("transcript-activity").assertIsDisplayed()
            val footer = compose.onNodeWithTag("transcript-activity").fetchSemanticsNode().boundsInRoot
            val shell = compose.onNodeWithTag("composer-shell").fetchSemanticsNode().boundsInRoot
            assertTrue("Newly revealed lines should stay above the composer", footer.bottom <= shell.top + 2f)
            assertTrue("Following must reach the real tail after reveal", shell.top - footer.bottom < 100f)
            captureTestScreen("29-live-reply-followed")
        } finally {
            compose.runOnUiThread { root.viewTreeObserver.removeOnDrawListener(listener) }
        }
    }
}
