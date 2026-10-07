package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test

class TranscriptSequenceTest {
    private fun reply(id: String, text: String = id) = MessageItem(id, "agentMessage", text, "", "", "", "", null, emptyList())
    private fun tool(id: String) = reply(id).copy(type = "commandExecution", text = "", command = "repeat command", status = "completed", exitCode = 0)
    private fun snapshot(vararg items: MessageItem) = Conversation(listOf(Turn("t", "completed", items.toList())),
        Observation("completed", 0, "gateway", "agentMessage"), emptyList(), emptyList())

    @Test fun initialHistoryIsImmediateAndCompletedFastTurnStillSequences() {
        val queue = TranscriptSequence()
        queue.accept(snapshot(reply("old")), true, 100)
        assertTrue(queue.frame().reveals.isEmpty())
        queue.accept(snapshot(reply("old"), reply("a"), reply("b")), true, 200)
        assertEquals(listOf("old", "a"), queue.frame().items.map { it.id })
        assertEquals(setOf("a"), queue.frame().reveals.keys)
        val first = queue.frame().reveals.getValue("a")
        queue.advance(first.startedAt + first.duration)
        assertEquals(listOf("old", "a", "b"), queue.frame().items.map { it.id })
        assertEquals(setOf("b"), queue.frame().reveals.keys)
    }

    @Test fun revisionsOfActiveReplyAreCoalescedWithoutDroppingText() {
        val queue = TranscriptSequence()
        queue.accept(snapshot(reply("a", "old")), true, 0)
        queue.accept(snapshot(reply("a", "old + first"), reply("b")), true, 100)
        val active = queue.frame().reveals.getValue("a")
        queue.accept(snapshot(reply("a", "old + first + second"), reply("b")), true, 110)
        queue.accept(snapshot(reply("a", "old + first + second + third"), reply("b")), true, 120)
        assertEquals("old + first", queue.frame().items.single().text)
        queue.advance(active.startedAt + active.duration)
        assertEquals("old + first", queue.frame().reveals.getValue("a").from)
        assertEquals("old + first + second + third", queue.frame().items.single().text)
        queue.advance(4300)
        assertEquals(listOf("a", "b"), queue.frame().items.map { it.id })
    }

    @Test fun historyAndBackgroundFlushAndNeverReplay() {
        val queue = TranscriptSequence()
        val initial = snapshot(reply("a"))
        val next = snapshot(reply("a"), reply("b"), reply("c"))
        queue.accept(initial, true, 0)
        queue.accept(next, true, 100)
        queue.accept(next, false, 110)
        assertEquals(3, queue.frame().items.size)
        assertTrue(queue.frame().reveals.isEmpty())
        queue.accept(next, true, 200)
        assertTrue(queue.frame().reveals.isEmpty())
    }

    @Test fun historicalPrependsDownloadsAndRewritesAreImmediate() {
        val queue = TranscriptSequence()
        queue.accept(snapshot(reply("a", "preview").copy(truncated = true)), true, 0)
        queue.accept(snapshot(reply("history"), reply("a", "preview full")), true, 100)
        assertTrue(queue.frame().reveals.isEmpty())
        assertEquals(2, queue.frame().items.size)
        queue.accept(snapshot(reply("history"), reply("a", "preview full downloaded")), true, 200, setOf("a"))
        assertTrue(queue.frame().reveals.isEmpty())
        queue.accept(snapshot(reply("history"), reply("a", "corrected")), true, 300)
        assertTrue(queue.frame().reveals.isEmpty())
        assertEquals("corrected", queue.frame().items.last().text)
    }

    @Test fun statusOnlyChangeDoesNotRestartOrCancelReveal() {
        val queue = TranscriptSequence()
        queue.accept(snapshot(reply("a")), true, 0)
        queue.accept(snapshot(reply("a", "a and more")), true, 100)
        val reveal = queue.frame().reveals.getValue("a")
        queue.accept(snapshot(reply("a", "a and more").copy(status = "completed")), true, 110)
        assertEquals(reveal, queue.frame().reveals.getValue("a"))
    }

    @Test fun newlyReceivedLargePreviewRevealsButExplicitFullDownloadDoesNot() {
        val queue = TranscriptSequence()
        queue.accept(snapshot(reply("old")), true, 0)
        val preview = reply("large", "长回复预览。".repeat(500)).copy(truncated = true, fullBytes = 90_000)
        queue.accept(snapshot(reply("old"), preview), true, 100)
        assertEquals(setOf("large"), queue.frame().reveals.keys)
        val full = preview.copy(text = preview.text + "剩余全文", truncated = false)
        queue.accept(snapshot(reply("old"), full), true, 150, setOf("large"))
        assertTrue(queue.frame().reveals.isEmpty())
        assertEquals(full.text, queue.frame().items.last().text)
    }

    @Test fun newUserAndResetCannotLeaveOldRepliesQueued() {
        val queue = TranscriptSequence()
        queue.accept(snapshot(reply("a")), true, 0)
        queue.accept(snapshot(reply("a"), reply("b"), reply("c")), true, 100)
        queue.accept(snapshot(reply("a"), reply("b"), reply("c"), reply("u").copy(type = "userMessage")), true, 110)
        assertEquals(4, queue.frame().items.size)
        assertTrue(queue.frame().reveals.isEmpty())
        queue.accept(snapshot(reply("reset")).copy(historyReset = true), true, 200)
        assertEquals(listOf("reset"), queue.frame().items.map { it.id })
        assertTrue(queue.frame().reveals.isEmpty())
    }

    @Test fun largeBatchHasBoundedCatchUpAndLongTextHasFasterCharacterRate() {
        assertTrue(replyRevealDuration(20) / 20.0 > replyRevealDuration(2000) / 2000.0)
        assertTrue(replyRevealDuration(2000, 8) < replyRevealDuration(2000, 1))
        val queue = TranscriptSequence()
        queue.accept(snapshot(reply("old")), true, 0)
        queue.accept(snapshot(reply("old"), *(0..99).map { reply("r$it", "large".repeat(500)) }.toTypedArray()), true, 100)
        queue.advance(4300)
        assertEquals(101, queue.frame().items.size)
        assertTrue(queue.frame().reveals.isEmpty())
    }

    @Test fun groupingKeepsOrderAndNeverLeaksCommandSourceIntoSummary() {
        val items = listOf(reply("a"), tool("c1"), tool("c2"), reply("b"), tool("c3"))
        val rows = ToolRunProjector().project(items, snapshot(*items.toTypedArray()).turns)
        assertEquals(listOf("a", "tools-c1", "b", "tools-c3"), rows.map { it.key })
        assertEquals(listOf("c1", "c2"), rows[1].items.map { it.id })
        assertFalse(toolRunSummary(rows[1].items).contains("repeat command"))
    }

    @Test fun groupedHistoryKeepsStableKeyAcrossPrependAndAppend() {
        val projector = ToolRunProjector()
        fun project(vararg items: MessageItem) = projector.project(items.toList(), snapshot(*items).turns)
        val key = project(tool("c2"), tool("c3")).single().key
        assertEquals(key, project(tool("c1"), tool("c2"), tool("c3")).single().key)
        assertEquals(key, project(tool("c1"), tool("c2"), tool("c3"), tool("c4")).single().key)
    }

    @Test fun hiddenReplyAndDifferentTurnsStillSeparateToolGroups() {
        val items = listOf(tool("c1"), reply("queued"), tool("c2"))
        assertEquals(2, ToolRunProjector().project(items.filter { it.type != "agentMessage" }, snapshot(*items.toTypedArray()).turns).size)
        val turns = listOf(Turn("one", "completed", listOf(tool("c1"))), Turn("two", "completed", listOf(tool("c2"))))
        assertEquals(2, ToolRunProjector().project(turns.flatMap { it.items }, turns).size)
    }

    @Test fun failuresRunningAndUnknownStatesRemainTruthful() {
        val items = listOf(tool("bad").copy(exitCode = 1), tool("run").copy(status = "inProgress", exitCode = null),
            tool("unknown").copy(status = "", exitCode = null))
        val summary = toolRunSummary(items)
        assertTrue(summary.contains("3 条命令") && summary.contains("1 项失败") && summary.contains("1 项运行中"))
        assertFalse(summary.contains("已完成"))
    }
}
