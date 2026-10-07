package dev.codex.remote

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic clipboard on the disposable CI emulator only. No real messages or accounts. */
@RunWith(AndroidJUnit4::class)
class ContentCopyUiTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)
    private fun message(id: String, type: String, text: String) = MessageItem(id, type, text, "", "", "", "", null, emptyList())
    private fun assertClip(text: String) = compose.runOnIdle { assertEquals(text, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
    private fun seedClipboard() = compose.runOnIdle { clipboard.setPrimaryClip(ClipData.newPlainText("fixture", "KEEP_OLD_CLIP")) }
    @After fun clearSyntheticClipboard() {
        if (Build.VERSION.SDK_INT >= 28) clipboard.clearPrimaryClip()
        else clipboard.setPrimaryClip(ClipData.newPlainText("fixture", ""))
    }

    @Test fun userAndReplyCopyExactCurrentSourceAndSuppressSystemPreview() {
        val user = message("user-copy", "userMessage", "保留换行\n和空格  ")
        val reply = mutableStateOf(message("reply-copy", "agentMessage", "## 回答\n\n```sh\necho 你好\n```"))
        compose.setContent { HapiStyle(false) { Column(Modifier.padding(16.dp)) {
            ChatMessageBubble(user)
            ChatMessageBubble(reply.value)
        } } }
        compose.onNodeWithTag("copy-user-copy").performClick()
        compose.waitForIdle()
        assertClip(user.text)
        compose.onNodeWithTag("copy-user-copy").assertContentDescriptionEquals("已复制")
        compose.onNodeWithTag("copy-reply-copy").performClick()
        compose.waitForIdle()
        assertClip(reply.value.text)
        compose.runOnIdle { assertTrue(clipboard.primaryClipDescription?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true) }
        captureTestScreen("23-message-copy-light")
        compose.runOnIdle { reply.value = reply.value.copy(text = "新到达的回复内容") }
        compose.onNodeWithTag("copy-reply-copy").performClick()
        compose.waitForIdle()
        assertClip("新到达的回复内容")
    }

    @Test fun previewDownloadsOnlyAfterExplicitCopyAndNeverCopiesPartialText() {
        val preview = message("long-copy", "agentMessage", "只有预览…").copy(truncated = true, fullBytes = 4096)
        val gate = CompletableDeferred<MessageItem>()
        var downloads = 0
        compose.setContent { HapiStyle(false) {
            ChatMessageBubble(preview, onLoadFull = { downloads++; gate.await() })
        } }
        compose.waitForIdle()
        seedClipboard()
        assertEquals(0, downloads)
        compose.onNodeWithTag("copy-long-copy").assertContentDescriptionEquals("下载全文并复制").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("copy-long-copy").assertIsNotEnabled()
        assertClip("KEEP_OLD_CLIP")
        compose.runOnIdle { gate.complete(preview.copy(text = "完整回复\n包括预览未显示的末尾", truncated = false)) }
        compose.waitUntil(5000) { compose.onAllNodesWithContentDescription("已复制").fetchSemanticsNodes().isNotEmpty() }
        assertClip("完整回复\n包括预览未显示的末尾")
        assertEquals(1, downloads)
    }

    @Test fun failedDownloadPreservesClipboardAndAllowsRetry() {
        val preview = message("failed-copy", "agentMessage", "不要复制残缺预览").copy(truncated = true)
        var downloads = 0
        compose.setContent { HapiStyle(false) { ChatMessageBubble(preview, onLoadFull = {
            downloads++
            if (downloads == 1) throw java.io.IOException("offline")
            it.copy(text = "重试后完整内容", truncated = false)
        }) } }
        seedClipboard()
        compose.onNodeWithTag("copy-failed-copy").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("copy-failed-copy").assertIsEnabled().assertContentDescriptionEquals("复制失败，重试")
        assertClip("KEEP_OLD_CLIP")
        compose.onNodeWithTag("copy-failed-copy").performClick()
        compose.waitForIdle()
        assertClip("重试后完整内容")
        assertEquals(2, downloads)
    }

    @Test fun leavingContentCancelsPendingClipboardWrite() {
        val visible = mutableStateOf(true)
        val gate = CompletableDeferred<MessageItem>()
        val preview = message("cancel-copy", "agentMessage", "预览").copy(truncated = true)
        var started = false
        compose.setContent { HapiStyle(false) { if (visible.value) ChatMessageBubble(preview, onLoadFull = { started = true; gate.await() }) } }
        seedClipboard()
        compose.onNodeWithTag("copy-cancel-copy").performClick()
        compose.waitUntil(5000) { started }
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { gate.complete(preview.copy(text = "不应写入", truncated = false)) }
        compose.waitForIdle()
        assertClip("KEEP_OLD_CLIP")
    }

    @Test fun expandedToolsCopyCommandOutputAndFileDiffWithoutUnfoldingSideEffects() {
        val command = message("command-copy", "commandExecution", "").copy(command = "npm test", output = "ok\n\n", exitCode = 0)
        val file = message("file-copy", "fileChange", "").copy(changes = listOf(Change("src/ui.kt", "-old\n+new")))
        compose.setContent { HapiStyle(true) { Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            ToolCard(command)
            ToolCard(file)
        } } }
        compose.onNodeWithTag("copy-command-copy").assertDoesNotExist()
        compose.onNodeWithTag("tool-command-copy").performClick()
        compose.onNodeWithTag("copy-command-copy").performScrollTo().performClick()
        compose.waitForIdle()
        assertClip("npm test\n\nok\n\n")
        compose.onNodeWithTag("tool-command-copy").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已展开"))
        compose.onNodeWithTag("tool-command-copy").performClick()
        compose.onNodeWithTag("tool-file-copy").performClick()
        compose.onNodeWithTag("copy-file-copy").performScrollTo().performClick()
        compose.waitForIdle()
        assertClip("src/ui.kt\n-old\n+new")
        captureTestScreen("24-tool-copy-night")
    }
}
