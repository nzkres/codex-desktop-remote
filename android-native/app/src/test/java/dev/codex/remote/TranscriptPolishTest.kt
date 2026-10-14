package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

class TranscriptPolishTest {
    private val session = Session("s", "p", "Title", "RUNNING", "t", "default", 0)
    private val command = MessageItem("c", "commandExecution", "", "npm test\nsecond command", "", "", "inProgress", null, emptyList())
    private fun conversation(item: MessageItem = command) = Conversation(listOf(Turn("t", "inProgress", listOf(item))),
        Observation("running", 0, "gateway", item.type), emptyList(), emptyList())

    @Test fun userTextPreservesEveryLineBlankIndentAndLiteralMarkdown() {
        val raw = "第一行\r\n第二行\r\n\r\n  **不要合并**\r第三行"
        assertEquals("第一行\n第二行\n\n  **不要合并**\n第三行", userDisplayText(raw))
        assertTrue(raw.contains("\r\n"))
    }

    @Test fun receiptCanMatchNormalizedServerTextWithoutDiscardingOriginalDraft() {
        val raw = "\n  第一行\n第二行  \n"
        val receipt = SendPresentation("receipt", raw, null, emptySet())
        val user = command.copy(id = "user", type = "userMessage", text = raw.trim())
        assertEquals(user, confirmedSentItem(receipt, listOf(Turn("t", "completed", listOf(user)))))
        assertEquals(raw, receipt.text)
    }

    @Test fun headerUsesCurrentCommandThenLatestOutput() {
        assertEquals(HeaderActivity("命令 · npm test", true), headerActivity(session, conversation()))
        val updated = command.copy(output = "first\n\u001B[32mTests passed\u001B[0m\n")
        assertEquals(HeaderActivity("输出 · Tests passed", true), headerActivity(session, conversation(updated)))
    }

    @Test fun waitingAndCompletionCannotPretendToRunOldCommand() {
        assertEquals(HeaderActivity("等待你的回答"), headerActivity(session.copy(status = "WAITING_USER"), conversation()))
        assertEquals(HeaderActivity("等待你的批准"), headerActivity(session.copy(status = "WAITING_APPROVAL"), conversation()))
        val done = conversation().copy(observation = Observation("completed", 0, "gateway", "commandExecution"))
        assertEquals(HeaderActivity("任务已完成"), headerActivity(session.copy(status = "COMPLETED", activeTurnId = null), done))
        assertEquals(HeaderActivity("等待新的活动记录"), headerActivity(session, conversation().copy(observation = done.observation.copy(state = "quiet"))))
    }

    @Test fun unseenActiveTurnNeverReusesPreviousCommandAndPreviewIsBounded() {
        assertEquals(HeaderActivity("正在处理", true), headerActivity(session.copy(activeTurnId = "new"), conversation()))
        val preview = headerPreview("😀".repeat(300))
        assertEquals(120, preview.codePointCount(0, preview.length))
        assertFalse(Character.isHighSurrogate(preview.last()))
        assertFalse(headerPreview("a\u0000b\nc").contains('\u0000'))
    }

    @Test fun zebraKeepsVisibleParityAcrossOddHistoryPrependAndAppend() {
        val ledger = StripeLedger()
        val before = ledger.assign(listOf("a", "b", "c"))
        val after = ledger.assign(listOf("older", "a", "b", "c", "d"))
        before.forEach { (key, stripe) -> assertEquals(stripe, after[key]) }
        assertNotEquals(after["older"], after["a"])
        assertNotEquals(after["c"], after["d"])
        assertEquals(setOf("b", "c"), ledger.assign(listOf("b", "c")).keys)
    }

    @Test fun codeRowsPreserveOutputAndExitStateWithoutPaddingRows() {
        val completed = command.copy(command = "echo one", output = "one\n\n  two", status = "completed", exitCode = 0)
        val rows = toolCodeRows(completed)
        assertEquals(listOf("COMMAND", "echo one", "OUTPUT", "one", "", "  two"), rows.map { it.text })
        assertEquals("EXIT 0", rows.first().trailing)
    }

    @Test fun smallSecondaryLabelsRemainReadableOnEveryZebraAndRoleSurface() {
        for (zebra in ZebraStyle.entries.filter { it != ZebraStyle.Off }) for (style in ChatStyle.entries)
            for (night in listOf(false, true)) for (mine in listOf(false, true)) for (stripe in 0..1) {
                val fallback = if (night) Color(0xFF9BA8BA) else Color(0xFF6B7280)
                val text = zebraSecondary(zebra, night, fallback).luminance()
                val surface = messageSurface(ChatAppearance(style = style, zebra = zebra, skin = TerminalSkin(effect = GridEffect.Off)), mine, stripe, night).luminance()
                val contrast = (max(text, surface) + .05f) / (min(text, surface) + .05f)
                assertTrue("Small labels need contrast on $zebra / $style / $night / $mine / $stripe: $contrast", contrast >= 4.5f)
                if (mine) {
                    val role = conversationColors(style, night).accent.luminance()
                    val roleContrast = (max(role, surface) + .05f) / (min(role, surface) + .05f)
                    assertTrue("User role also needs contrast on $zebra / $style / $night / $stripe: $roleContrast", roleContrast >= 4.5f)
                }
            }
    }

    @Test fun bottomInsetUsesExactlyTheLastStripeIncludingUserTint() {
        for (zebra in ZebraStyle.entries.filter { it != ZebraStyle.Off }) for (style in ChatStyle.entries)
            for (night in listOf(false, true)) for (mine in listOf(false, true)) for (stripe in 0..1) {
                val appearance = ChatAppearance(style = style, zebra = zebra, skin = TerminalSkin(effect = GridEffect.Off))
                assertEquals(messageSurface(appearance, mine, stripe, night), transcriptPageSurface(appearance, mine, stripe, night))
            }
        assertEquals(1f, transcriptPageSurface(ChatAppearance(zebra = ZebraStyle.Off), false, 0, false).alpha)
    }

    @Test fun lightIsBrighterAtNightAndWaitingOrOldActivityDoesNotAnimate() {
        assertTrue(processingLightColor(true).luminance() > .93f)
        // White on light backgrounds disappears. This blue core remains readable on the darkest light stripe.
        assertTrue((Color(0xFFE3EEF9).luminance() + .05f) / (processingLightColor(false).luminance() + .05f) >= 4.5f)
        val observation = conversation().observation
        assertTrue(sessionProcessing(session, observation))
        for (state in listOf("WAITING_USER", "WAITING_APPROVAL", "COMPLETED", "INTERRUPTED", "FAILED"))
            assertFalse(sessionProcessing(session.copy(status = state), observation))
        assertFalse(sessionProcessing(session, observation.copy(state = "quiet")))
    }
}
