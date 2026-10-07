package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test
import java.net.SocketTimeoutException

class ConversationWindowTest {
    private val metadata = Conversation(emptyList(), Observation("running", 1, "gateway", ""), emptyList(), emptyList(), hasOlder = true)
    private fun entry(id: String, revision: String = "r1", text: String = id, truncated: Boolean = false) = SyncEntry(id, revision, "turn", "inProgress",
        MessageItem(id, "agentMessage", text, "", "", "", "", null, emptyList(), truncated))

    @Test fun pagesUpdatesAndRemovalAreOrderedWithoutDuplicates() {
        val window = ConversationWindow()
        window.applyPage("one", true, listOf("b", "c"), listOf(entry("b"), entry("c")), metadata)
        val older = window.applyPage("two", false, listOf("a", "b", "c", "d"), listOf(entry("a"), entry("d")), metadata)
        assertEquals(listOf("a", "b", "c", "d"), older.turns.single().items.map { it.id })
        val edited = window.applyPage("three", false, listOf("a", "b", "d"), listOf(entry("b", "r2", "edited")), metadata)
        assertEquals(listOf("a", "edited", "d"), edited.turns.single().items.map { it.text })
    }

    @Test fun explicitFullContentSurvivesStatusRefreshButNotContentChange() {
        val window = ConversationWindow()
        window.applyPage("one", true, listOf("a"), listOf(entry("a", truncated = true)), metadata)
        window.expand(entry("a", text = "full content"))
        val status = window.applyPage("two", false, listOf("a"), listOf(entry("a", truncated = true).copy(turnStatus = "completed")), metadata)
        assertEquals("full content", status.turns.single().items.single().text)
        assertEquals("completed", status.turns.single().status)
        val changed = window.applyPage("three", false, listOf("a"), listOf(entry("a", "r2", "new preview", true)), metadata)
        assertTrue(changed.turns.single().items.single().truncated)
    }

    @Test fun invalidDeltaDoesNotAdvanceCursorOrPartiallyMutateHistory() {
        val window = ConversationWindow()
        val first = window.applyPage("one", true, listOf("a"), listOf(entry("a")), metadata)
        try { window.applyPage("bad", false, listOf("a", "missing"), listOf(entry("a", text = "wrong")), metadata); fail() }
        catch (_: IllegalArgumentException) { }
        assertEquals("one", window.cursor); assertEquals(first, window.current)
    }

    @Test fun resetReplacesOldWindowAndMetadataChangesDoNotNeedText() {
        val window = ConversationWindow()
        window.applyPage("one", true, listOf("a"), listOf(entry("a")), metadata)
        val reset = window.applyPage("two", true, listOf("b"), listOf(entry("b")), metadata.copy(hasOlder = false))
        assertEquals(listOf("b"), reset.turns.single().items.map { it.id }); assertFalse(reset.hasOlder)
        val changed = window.applyPage("three", false, listOf("b"), emptyList(), metadata.copy(questions = listOf(QuestionRequest("q", emptyList()))))
        assertEquals("q", changed.questions.single().id)
    }

    @Test fun timeoutMessageDistinguishesReadFromUncertainSend() {
        assertTrue(networkError(SocketTimeoutException("timeout")).contains("不代表电脑任务停止"))
        assertTrue(networkError(SocketTimeoutException("timeout"), sending = true).contains("避免重复发送"))
    }

    @Test fun timingOnlyRevisionsKeepDownloadedOriginalAndRefreshFooter() {
        val window = ConversationWindow()
        val original = entry("a", text = "complete original").let { it.copy(item = it.item.copy(contentRevision = "body-1", turnLastItem = true)) }
        window.applyPage("one", true, listOf("a"), listOf(original), metadata)
        val updated = original.copy(revision = "r2", turnStatus = "completed", item = original.item.copy(text = "preview",
            truncated = true, turnLastItem = false, turnCompleted = true, turnDurationMs = 65_000))
        val item = window.applyPage("two", false, listOf("a"), listOf(updated), metadata.copy(userMessageCount = 57)).turns.single().items.single()
        assertEquals("complete original", item.text); assertFalse(item.truncated)
        assertEquals(65_000L, item.turnDurationMs); assertTrue(item.turnCompleted); assertFalse(item.turnLastItem)
        assertEquals(57, window.current?.userMessageCount)
        val changed = updated.copy(revision = "r3", item = updated.item.copy(contentRevision = "body-2"))
        assertTrue(window.applyPage("three", false, listOf("a"), listOf(changed), metadata).turns.single().items.single().truncated)
    }
}
