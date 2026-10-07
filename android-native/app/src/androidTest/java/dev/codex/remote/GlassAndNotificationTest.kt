package dev.codex.remote

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.filters.SdkSuppress
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic UI only: never logs in, contacts the relay, or sends a real Codex message. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class GlassAndNotificationTest {
    @get:Rule(order = 0) val permissions = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    @get:Rule(order = 1) val compose = createComposeRule()
    private val id = "00000000-0000-4000-8000-000000000001"
    private val session = Session(id, "fixture", "让想法自然流动", "COMPLETED", null, "default", 100)

    private fun launch(night: Boolean = false, onSend: (String) -> Unit = {}) {
        compose.setContent {
            var dark by remember { mutableStateOf(night) }
            HapiStyle(dark) {
                val turns = listOf(Turn("turn-preview", "completed", listOf(
                    MessageItem("user", "userMessage", "把手机和电脑上的对话连起来，让我能随时继续工作。", "", "", "", "", null, emptyList()),
                    MessageItem("reply", "agentMessage", "## 让工作，自然接续\n\n你可以在电脑开始，在手机继续。对话记录始终留在同一处。\n\n- 任务完成时，轻轻提醒你\n- 需要决定时，回到原来的对话\n- 收起输入框，留更多空间阅读\n\n细节已经准备好。接下来，交给你的想法。", "", "", "", "", null, emptyList()),
                )))
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                ChatScreen(threadId = id, session = session, projectName = "Personal workspace", conversation = Conversation(turns, Observation("completed", 100, "gateway", "agentMessage"), emptyList(), emptyList()),
                    loadError = "", busy = false, night = dark, onToggleNight = { dark = !dark }, onBack = {}, onRetry = {},
                    onSend = { onSend(it); null }, onStop = {}, onMode = {}, onImplement = {}, onApproval = { _, _ -> }, onAnswer = { _, _ -> })
                }
            }
        }
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        captureTestScreen(name)
    }

    @Test fun glassMorphPreservesDraftAndSupportsBothEdges() {
        launch()
        screenshot("01-light-expanded")
        compose.onNode(hasSetTextAction()).performTextInput("这段草稿要保留")
        compose.onNodeWithContentDescription("收起到左侧").performClick()
        compose.waitForIdle()
        val bubble = compose.onNodeWithContentDescription("展开输入框，草稿已保留")
        bubble.assertIsDisplayed()
        val leftBounds = bubble.fetchSemanticsNode().boundsInRoot
        assertTrue(leftBounds.width < 240f)
        screenshot("02-light-bubble-left")
        bubble.performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertTrue(bubble.fetchSemanticsNode().boundsInRoot.left > leftBounds.left)
        screenshot("03-light-bubble-right")
        bubble.performClick()
        compose.onNode(hasSetTextAction()).assertTextEquals("这段草稿要保留")
        compose.onNodeWithContentDescription("收起到右侧").assertIsDisplayed()
        compose.revealChatHeader()
        compose.onNodeWithContentDescription("切换深夜背景").performClick()
        screenshot("04-night-expanded")
    }

    @Test fun sendRemainsSinglePreservesWhitespaceAndClearsAcceptedDraft() {
        var sent = ""; var count = 0
        launch(night = true) { sent = it; count++ }
        compose.onNode(hasSetTextAction()).performTextInput("  保留原来的发送协议  ")
        compose.onNodeWithContentDescription("发送消息").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("展开输入框").fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
        assertEquals("  保留原来的发送协议  ", sent)
        assertEquals(1, count)
        // The accepted receipt waits for the keyboard/shell before scrolling. A collapsed
        // accessibility label appears before that delayed scroll has finished.
        compose.waitUntil(5000) {
            val message = compose.onAllNodesWithTag("sent-message").fetchSemanticsNodes().singleOrNull()
            val viewport = compose.onNodeWithTag("transcript").fetchSemanticsNode().boundsInRoot
            message != null && message.boundsInRoot.height > 0f &&
                message.boundsInRoot.top >= viewport.top && message.boundsInRoot.bottom <= viewport.bottom
        }
        compose.onNodeWithTag("sent-message").assertIsDisplayed()
        captureTestScreen("22-long-chat-accepted")
        compose.onNodeWithContentDescription("展开输入框").performClick()
        // A blank editor still exposes its placeholder through merged Text semantics.
        compose.onNode(hasSetTextAction()).assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithContentDescription("发送消息").assertIsNotEnabled()
    }

    @Test fun notificationsHavePrivateLockscreenAndReadOnlyDestination() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = "a".repeat(64)
        TaskNotifications.createChannels(context)
        assertTrue(TaskNotifications.post(context, scope, session, listOf(TaskSignal("question", "question:q"))))
        val manager = context.getSystemService(NotificationManager::class.java)
        compose.waitUntil(10_000) { manager.activeNotifications.any { it.tag?.contains(scope) == true } }
        val posted = manager.activeNotifications.single { it.tag?.contains(scope) == true }.notification
        assertEquals(Notification.VISIBILITY_PRIVATE, posted.visibility)
        assertNotNull(posted.publicVersion)
        assertEquals("有新的任务消息，解锁后查看", posted.publicVersion.extras.getString(Notification.EXTRA_TEXT))
        assertTrue(posted.actions.isNullOrEmpty()) // Cannot approve or submit answers from a notification.
        assertTrue(posted.contentIntent.isImmutable)
        assertNull(TaskNotifications.destination(Intent()))
        val destination = TaskNotifications.destination(Intent().putExtra("notification_scope", scope).putExtra("notification_thread", id))
        assertEquals(NotificationDestination(scope, id), destination)
        TaskNotifications.clearThread(context, scope, id)
        compose.waitUntil(10_000) { manager.activeNotifications.none { it.tag?.contains(scope) == true } }
    }
}
