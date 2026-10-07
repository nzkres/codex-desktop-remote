package dev.codex.remote

import android.content.res.Configuration
import android.graphics.Paint
import android.text.Spanned
import android.view.View
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.Markwon
import io.noties.markwon.core.spans.CodeBlockSpan
import io.noties.markwon.ext.tables.TablePlugin
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CompactTypographyTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val markdown = "## 更紧凑，也更清晰\n\n中文正文保留自然的阅读节奏。English paths and code 保持对齐。\n\n第二段不再隔着整行空白。\n\n- 查看任务\n- 继续对话\n\n```sh\necho 0O1Il\n\necho 保留代码空行\n```"

    @Test fun paragraphsAreShorterWithoutChangingTextOrCodeBlankLines() {
        compose.runOnUiThread {
            val old = TextView(context).apply { textSize = 15f; setLineSpacing(2f, 1f) }
            val fresh = TextView(context).apply { configureChatText(this) }
            Markwon.builder(context).usePlugin(TablePlugin.create(context)).build().setMarkdown(old, markdown)
            chatMarkdown(context).setMarkdown(fresh, markdown)
            assertEquals(old.text.toString(), fresh.text.toString())
            val styled = fresh.text as Spanned
            val gaps = styled.getSpans(0, styled.length, ParagraphGap::class.java)
            assertTrue("Paragraph gaps must actually be compacted", gaps.isNotEmpty())
            for (gap in gaps) assertTrue(styled.getSpans(styled.getSpanStart(gap), styled.getSpanEnd(gap), CodeBlockSpan::class.java).isEmpty())
            val width = (320 * context.resources.displayMetrics.density).toInt()
            fun height(view: TextView): Int {
                view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                return view.measuredHeight
            }
            assertTrue("Compact rendering should save at least 10% height", height(fresh) < height(old) * .9f)
            assertFalse(fresh.includeFontPadding)
            // Real configuration scaling, not merely a Compose-only density override.
            val largeContext = context.createConfigurationContext(Configuration(context.resources.configuration).apply { fontScale = 1.5f })
            val large = TextView(largeContext).apply { configureChatText(this) }
            chatMarkdown(largeContext).setMarkdown(large, markdown)
            assertTrue(large.textSize > fresh.textSize)
            assertTrue(height(large) > height(fresh))
        }
    }

    @Test fun fontIsPinnedMonospaceWithChineseFallbackAndBundledLicense() {
        val bytes = context.resources.openRawResource(R.font.jetbrains_mono_regular).use { it.readBytes() }
        assertEquals("e6fd0d7e91550b3ed2b735d4312474362c4716edc4fc0577a0f61ed782d5aed1",
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        val paint = Paint().apply { typeface = terminalTypeface(context); textSize = 24f }
        assertEquals(paint.measureText("iiii"), paint.measureText("WWWW"), .01f)
        assertTrue(paint.hasGlyph("中"))
        assertTrue(context.assets.open("licenses/JetBrainsMono-OFL.txt").bufferedReader().use { it.readText() }.contains("SIL OPEN FONT LICENSE Version 1.1"))
    }

    @Test fun compactTranscriptKeepsTouchTargetsAndBothThemes() {
        val session = Session("compact", "p", "更紧凑的终端排版", "RUNNING", "t", "default", 0)
        val items = listOf(MessageItem("reply", "agentMessage", "中文正文更紧凑，命令依然清晰。\n\n正文与代码使用不同字体。", "", "", "", "", null, emptyList())) +
            (1..3).map { MessageItem("cmd$it", "commandExecution", "", listOf("git status --short", "npm run test", "ssh ubuntu@relay")[it - 1], "检查通过\n0O1Il != ->", "", "completed", 0, emptyList()) }
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            var night by remember { mutableStateOf(false) }
            HapiStyle(night) { Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                ChatScreen(session.id, session, "Personal workspace",
                    Conversation(listOf(Turn("t", "inProgress", items)), Observation("running", 0, "gateway", "reasoning"), emptyList(), emptyList()),
                    "", false, night, onToggleNight = { night = !night }, onBack = {}, onRetry = {}, onSend = { null }, onStop = {},
                    onMode = {}, onImplement = {}, onApproval = { _, _ -> }, onAnswer = { _, _ -> })
            } }
        }
        compose.onNodeWithTag("transcript").performScrollToNode(hasTestTag("toggle-tools-cmd1"))
        val summary = compose.onNodeWithTag("toggle-tools-cmd1").fetchSemanticsNode().boundsInRoot
        assertTrue(summary.height / density >= 47.5f)
        compose.onNodeWithTag("tool-cmd1").assertDoesNotExist()
        compose.onNodeWithTag("tool-cmd2").assertDoesNotExist()
        compose.onNodeWithText("执行记录 · 3 条命令").assertIsDisplayed()
        compose.waitForIdle()
        captureTestScreen("14-compact-transcript-light")
        compose.revealChatHeader()
        compose.onNodeWithContentDescription("切换深夜背景").performClick()
        compose.onNodeWithContentDescription("切换浅色背景").assertExists()
        compose.waitForIdle()
        captureTestScreen("15-compact-transcript-night")
        compose.onNodeWithContentDescription("收起到右侧").performClick()
        compose.onNodeWithTag("toggle-tools-cmd1").performClick()
        compose.onNodeWithTag("list-tools-cmd1").performScrollToNode(hasTestTag("tool-cmd2"))
        compose.onNodeWithTag("tool-cmd2").performClick()
        compose.onNodeWithTag("list-tools-cmd1").performScrollToNode(hasText("0O1Il != ->"))
        compose.onNodeWithText("0O1Il != ->").assertIsDisplayed()
        compose.waitForIdle()
        captureTestScreen("16-compact-terminal-font")
    }

    @Test fun licenseCanBeReadWithoutOpeningAnExternalPage() {
        compose.setContent { HapiStyle(false) { FontLicenseInfo() } }
        compose.onNodeWithText("字体与开源许可").performClick()
        compose.onNodeWithText("JetBrains Mono · OFL 1.1").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("JetBrains Mono · OFL 1.1").assertDoesNotExist()
    }
}
