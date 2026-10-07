package dev.codex.remote

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.*
import org.junit.Test

class ReadingControlsTest {
    @Test fun thresholdRequiresReleaseAndCannotFireTwice() {
        val gate = HistoryPullGate()
        gate.pull(100f, 64f)
        assertEquals(50f, gate.distance, .001f)
        assertFalse(gate.release(64f))
        gate.pull(140f, 64f)
        assertTrue(gate.dragging)
        assertTrue(gate.release(64f))
        assertFalse(gate.release(64f))
        assertEquals(0f, gate.distance, .001f)
    }

    @Test fun reversingAndCancellationDisarmThePull() {
        val gate = HistoryPullGate()
        gate.pull(200f, 64f)
        assertEquals(-80f, gate.retract(-80f), .001f)
        assertFalse(gate.release(64f))
        gate.pull(5000f, 64f)
        assertTrue(gate.distance <= 64f * 1.65f)
        gate.cancel()
        assertFalse(gate.release(64f))
    }

    @Test fun arrivalAnimationOnlyTargetsPrependedUnknownMessages() {
        fun item(id: String) = MessageItem(id, "agentMessage", id, "", "", "", "", null, emptyList())
        val previous = listOf(item("a"), item("b"))
        assertEquals(setOf("older1", "older2"), olderArrivalIds(previous, listOf(item("older1"), item("older2")) + previous + item("newest")))
        assertTrue(olderArrivalIds(previous, listOf(item("reset"))).isEmpty())
        assertTrue(olderArrivalIds(emptyList(), previous).isEmpty())
    }

    @Test fun stringEchoKeepsCaretSelectionAndChineseComposition() {
        val editing = TextFieldValue("中文输入中", selection = TextRange(3), composition = TextRange(2, 4))
        assertSame(editing, synchronizeDraft(editing, editing.text))
        val selection = editing.copy(selection = TextRange(1, 3))
        assertSame(selection, synchronizeDraft(selection, selection.text))
        val cleared = synchronizeDraft(editing, "")
        assertEquals(TextRange.Zero, cleared.selection)
        assertNull(cleared.composition)
        val restored = synchronizeDraft(cleared, "恢复草稿")
        assertEquals(TextRange(4), restored.selection)
    }
}
