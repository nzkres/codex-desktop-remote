package dev.codex.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ProcessingPolishUiTest {
    @get:Rule(order = 0) val timeout: Timeout = Timeout.builder().withTimeout(90, TimeUnit.SECONDS).build()
    @get:Rule(order = 1) val compose = createComposeRule()
    @After fun cleanup() { compose.mainClock.autoAdvance = true }
    private fun light(tag: String, moving: Boolean) = compose.onNodeWithTag(tag, useUnmergedTree = true)
        .assert(SemanticsMatcher.expectValue(ProcessingLightState, if (moving) "moving" else "static"))

    @Test fun backVectorIsCenteredInTouchTargetAndHeaderAtBothFontScales() {
        val scale = mutableStateOf(1f)
        var back = 0
        compose.setContent { HapiStyle(false) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, scale.value)) {
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    GlassChatHeader("检查箭头居中", true, false, {}, { back++ }, {}, rememberGraphicsLayer(), Offset.Zero)
                }
            }
        } }
        for (font in listOf(1f, 1.6f)) {
            compose.runOnIdle { scale.value = font }
            compose.waitForIdle()
            val button = compose.onNodeWithTag("header-back").fetchSemanticsNode().boundsInRoot
            val glyphNode = compose.onNodeWithTag("header-back-glyph", useUnmergedTree = true)
            val glyph = glyphNode.fetchSemanticsNode().boundsInRoot
            val header = compose.onNodeWithTag("chat-header-glass", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertEquals(button.center.y, glyph.center.y, 1f)
            assertEquals(header.center.y, glyph.center.y, 1f)
            assertEquals(button.center.x, glyph.center.x, 1f)
            assertEquals(glyph.width, glyph.height, 1f)
            // Verify actual dark stroke bounds, not just its correctly centered empty Canvas.
            val pixels = glyphNode.captureToImage().toPixelMap()
            val ys = (0 until pixels.height).filter { y -> (0 until pixels.width).any { x -> pixels[x, y].luminance() < .15f } }
            assertTrue(ys.isNotEmpty())
            assertEquals(pixels.height / 2f, (ys.first() + ys.last() + 1) / 2f, 1.5f)
            compose.onNodeWithTag("header-back").performClick()
        }
        assertEquals(2, back)
        captureTestScreen("43-centered-header-large-font")
    }

    @Test fun brighterGlyphLightReallyMovesLeftToRightInBothThemesAndStops() {
        val dark = mutableStateOf(false)
        val reduced = mutableStateOf(false)
        val active = mutableStateOf(true)
        compose.mainClock.autoAdvance = false
        compose.setContent { HapiStyle(dark.value) {
            CompositionLocalProvider(LocalChatAppearance provides ChatAppearance(reduceMotion = reduced.value)) {
                Column(Modifier.fillMaxSize().background(LocalAppPalette.current.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(20.dp)) {
                    key(dark.value) { ActivityFooter(TranscriptActivity("正在处理 · 逐步读取最新的运行内容", active.value)) }
                }
            }
        } }
        for (night in listOf(false, true)) {
            compose.runOnIdle { dark.value = night }
            compose.mainClock.advanceTimeBy(32)
            val node = light("transcript-activity", true)
            val base = node.captureToImage().toPixelMap()
            fun centroid(image: PixelMap): Float {
                var weight = 0f; var weightedX = 0f; var peak = 0f
                for (y in 0 until base.height) for (x in 0 until base.width) {
                    val a = base[x, y]; val b = image[x, y]
                    val delta = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
                    if (delta > .12f) { weight += delta; weightedX += x * delta; peak = maxOf(peak, delta) }
                }
                assertTrue("Sweep must clearly change real glyph pixels in night=$night", weight > 15f && peak > .35f)
                return weightedX / weight
            }
            compose.mainClock.advanceTimeBy(600)
            val left = centroid(node.captureToImage().toPixelMap())
            captureTestScreen("44-bright-sweep-${if (night) "night" else "light"}-left")
            compose.mainClock.advanceTimeBy(650)
            val right = centroid(node.captureToImage().toPixelMap())
            assertTrue("Light travels left to right, not an all-text flash", right - left > base.width * .18f)
            captureTestScreen("45-bright-sweep-${if (night) "night" else "light"}-right")
        }
        compose.runOnIdle { reduced.value = true }
        compose.mainClock.advanceTimeBy(32)
        val frozen = light("transcript-activity", false).captureToImage().toPixelMap()
        compose.mainClock.advanceTimeBy(700)
        val later = light("transcript-activity", false).captureToImage().toPixelMap()
        for (y in 0 until frozen.height) for (x in 0 until frozen.width) assertEquals(frozen[x, y], later[x, y])
        compose.runOnIdle { reduced.value = false; active.value = false }
        compose.mainClock.advanceTimeBy(32)
        light("transcript-activity", false)
    }

    @Test fun headerCommandNavigationAndHistoryUseSameProcessingLight() {
        val active = mutableStateOf(true)
        val reduced = mutableStateOf(false)
        val cmd = MessageItem("sweep", "commandExecution", "", "npm test", "", "", "inProgress", null, emptyList())
        compose.mainClock.autoAdvance = false
        compose.setContent { HapiStyle(true) {
            CompositionLocalProvider(LocalChatAppearance provides ChatAppearance(reduceMotion = reduced.value)) {
                val backdrop = rememberGraphicsLayer()
                Column(Modifier.fillMaxSize().background(LocalAppPalette.current.background).windowInsetsPadding(WindowInsets.safeDrawing)) {
                    GlassChatHeader("每一处状态都清晰", true, true, {}, {}, {}, backdrop, Offset.Zero,
                        activity = HeaderActivity(if (active.value) "输出 · tests running" else "等待你的回答", active.value))
                    ToolRunRow(TranscriptRow("tools-sweep", listOf(cmd)), true, {}, emptySet(), {}, { it },
                        activeToolIds = if (active.value) setOf(cmd.id) else emptySet())
                    TranscriptNavigation(true, TranscriptActivity("正在执行", active.value), false, 60.dp, backdrop, Offset.Zero, {})
                    HistoryPullIndicator(0f, 64f, false, active.value, true, "", false, backdrop, Offset.Zero)
                }
            }
        } }
        compose.mainClock.advanceTimeBy(700)
        val labels = listOf("header-subtitle", "header-loading", "status-tools-sweep", "tool-status-sweep", "navigation-status", "history-loading-label")
        labels.forEach { light(it, true) }
        captureTestScreen("46-unified-processing-indicators")
        compose.runOnIdle { reduced.value = true }
        compose.mainClock.advanceTimeBy(32)
        labels.forEach { light(it, false) }
        compose.runOnIdle { reduced.value = false; active.value = false }
        compose.mainClock.advanceTimeBy(300)
        labels.filter { it != "history-loading-label" }.forEach { light(it, false) }
    }

    @Test fun lastMessageAndBottomInsetHaveIdenticalPixelsForAllZebraThemes() {
        var density = 1f
        val dark = mutableStateOf(false)
        val zebra = mutableStateOf(ZebraStyle.Mist)
        val mine = mutableStateOf(false)
        compose.setContent { HapiStyle(dark.value) {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalChatAppearance provides ChatAppearance(zebra = zebra.value, reduceMotion = true)) {
                val item = MessageItem("tail", if (mine.value) "userMessage" else "agentMessage", "最后一条消息\n底部自然衔接。", "", "", "", "completed", null, emptyList())
                val session = Session("tail-ui", "fixture", "底部没有另一块颜色", "COMPLETED", null, "default", 0)
                val content = Conversation(listOf(Turn("t", "completed", listOf(item))), Observation("completed", 0, "gateway", item.type), emptyList(), emptyList())
                Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    ChatScreen(session.id, session, "fixture", content, "", false, dark.value, {}, {}, {}, { null }, {}, {}, {}, { _, _ -> }, { _, _ -> })
                }
            }
        } }
        for (style in ZebraStyle.entries.filter { it != ZebraStyle.Off }) for (night in listOf(false, true)) for (user in listOf(false, true)) {
            compose.runOnIdle { zebra.value = style; dark.value = night; mine.value = user }
            compose.waitForIdle()
            val node = compose.onNodeWithTag("message-tail")
            val message = node.captureToImage().toPixelMap()
            val list = compose.onNodeWithTag("transcript")
            val bounds = list.fetchSemanticsNode().boundsInRoot
            val row = node.fetchSemanticsNode().boundsInRoot
            val pixels = list.captureToImage().toPixelMap()
            // Sample beyond the intentional 2dp role marker, but outside the composer (14dp inset).
            val below = (row.bottom - bounds.top).toInt() + 3
            assertTrue("Reserved composer space must exist", below < pixels.height)
            val surfaceX = (4 * density).toInt()
            assertEquals("No hard bottom color band: $style/$night/$user", message[surfaceX, 1], pixels[surfaceX, below])
            if (user) assertNotEquals("User role marker remains distinct", message[1, 1], message[surfaceX, 1])
        }
        captureTestScreen("47-seamless-transcript-bottom")
    }
}
