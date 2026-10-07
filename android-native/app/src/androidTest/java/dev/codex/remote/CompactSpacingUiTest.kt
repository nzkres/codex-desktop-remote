package dev.codex.remote

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class CompactSpacingUiTest {
    @get:Rule(order = 0) val timeout: Timeout = Timeout.builder().withTimeout(120, TimeUnit.SECONDS).build()
    @get:Rule(order = 1) val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val clipboard get() = context.getSystemService(ClipboardManager::class.java)
    @After fun cleanup() { clipboard.clearPrimaryClip(); compose.mainClock.autoAdvance = true }

    @Test fun compactMetadataAndComposerKeepExpandedTouchTargetsAndLargeText() {
        val dark = mutableStateOf(false)
        val scale = mutableStateOf(1f)
        var pixelsPerDp = 1f
        val raw = "留白更紧凑，内容仍清晰。\n\n第二段保留原来的换行。"
        val item = MessageItem("compact-reply", "agentMessage", raw, "", "", "", "completed", null, emptyList())
        val conversation = Conversation(listOf(Turn("t", "completed", listOf(item))),
            Observation("completed", 0, "gateway", "agentMessage"), emptyList(), emptyList())
        val session = Session("spacing", "fixture", "紧凑间距验收", "COMPLETED", null, "default", 0)
        compose.setContent {
            val density = LocalDensity.current.density
            SideEffect { pixelsPerDp = density }
            CompositionLocalProvider(LocalDensity provides Density(density, scale.value)) {
                HapiStyle(dark.value) {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        ChatScreen(session.id, session, "合成验收", conversation, "", false, dark.value,
                            {}, {}, {}, { null }, {}, {}, {}, { _, _ -> }, { _, _ -> })
                    }
                }
            }
        }
        for ((night, fontScale) in listOf(false to 1f, true to 1.5f)) {
            compose.runOnIdle { dark.value = night; scale.value = fontScale }
            compose.waitForIdle()
            val meta = compose.onNodeWithTag("message-meta-compact-reply").fetchSemanticsNode().boundsInRoot
            val composerMeta = compose.onNodeWithTag("composer-meta").fetchSemanticsNode().boundsInRoot
            val shell = compose.onNodeWithTag("composer-shell").fetchSemanticsNode().boundsInRoot
            val maxRow = if (fontScale == 1f) 34f else 44f
            assertTrue("Codex metadata should not reserve a 48dp blank row", meta.height / pixelsPerDp <= maxRow)
            assertTrue("Message label should be compact", composerMeta.height / pixelsPerDp <= maxRow)
            assertTrue("Compact composer includes its 10dp safe bottom inset", shell.height / pixelsPerDp <= if (fontScale == 1f) 146f else 162f)
            captureTestScreen("54-compact-spacing-${if (night) "night-large" else "light"}")
            // Tap outside the visible 32dp control, but inside its 48dp pointer target.
            val copy = compose.onNodeWithTag("copy-compact-reply").fetchSemanticsNode().boundsInRoot
            compose.onRoot().performTouchInput { click(Offset(copy.center.x, copy.top - 4f * pixelsPerDp)) }
            compose.waitUntil(5000) { clipboard.primaryClip?.getItemAt(0)?.text?.toString() == raw }
            clipboard.clearPrimaryClip()
            // IconButton's merged semantics can already include its expanded hit bounds.
            // Use the actual glyph center: 20dp is outside the 32dp visual control,
            // but inside the 48dp pointer target (24dp radius).
            val glyph = compose.onNodeWithTag("composer-collapse-right-glyph", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            compose.onRoot().performTouchInput { click(Offset(glyph.center.x, glyph.center.y - 20f * pixelsPerDp)) }
            compose.waitForIdle()
            captureTestScreen("55-compact-hit-${if (night) "night-large" else "light"}")
            compose.onNodeWithContentDescription("展开输入框").assertIsDisplayed().performClick()
            compose.onNodeWithTag("composer-editor").assertIsDisplayed()
        }
    }
}
