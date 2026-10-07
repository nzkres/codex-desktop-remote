package dev.codex.remote

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TaskRecallTest {
    private fun item(id: String, text: String = id) = MessageItem(id, "userMessage", text, "", "", "", "", null, emptyList())
    private fun snapshot(items: List<MessageItem> = emptyList(), older: Boolean = true, latest: MessageItem? = null,
        supported: Boolean = true) = Conversation(listOf(Turn("t", "inProgress", items)), Observation("running", 0, "gateway", ""),
        emptyList(), emptyList(), hasOlder = older, latestUserMessage = latest?.let { SyncEntry(it.id, "r", "t", "inProgress", it) },
        taskReferenceSupported = supported)

    @Test fun authoritativeTaskSurvivesWindowAndDesktopReplacement() {
        val old = item("old"); val newest = item("desktop", "电脑任务\n第二行")
        assertEquals(newest, latestTask(snapshot(listOf(old), latest = newest), null)?.item)
        assertNull(latestTask(snapshot(listOf(old), latest = null), null))
        assertEquals(old, latestTask(snapshot(listOf(old), supported = false), null)?.item)
        val window = ConversationWindow()
        val merged = window.applyPage("cursor", true, emptyList(), emptyList(), snapshot(latest = newest))
        assertEquals(newest, latestTask(merged, null)?.item)
    }

    @Test fun acceptedReceiptPrecedesStaleSnapshotButNotConfirmedDesktopTasks() {
        val previous = item("previous")
        val receipt = SendPresentation("receipt", "新任务\n原文", "t", setOf(previous.id))
        val pending = latestTask(snapshot(latest = previous), receipt)!!
        assertTrue(pending.pendingSync); assertEquals(receipt.text, pending.item.text)
        val confirmed = item("confirmed", receipt.text)
        assertEquals(confirmed, latestTask(snapshot(latest = confirmed), receipt)?.item)
        assertEquals(confirmed, confirmedTaskReceipt(receipt, snapshot(latest = confirmed)))
        val noTurn = receipt.copy(turnId = null)
        assertNull(confirmedTaskReceipt(noTurn, snapshot(latest = item("unrelated", "其他任务"))))
        val desktop = item("desktop")
        assertEquals(desktop, latestTask(snapshot(latest = desktop), receipt.copy(confirmedItemId = confirmed.id))?.item)
        // Failed/unsubmitted draft never becomes a SendPresentation.
        assertEquals(previous, latestTask(snapshot(latest = previous), null)?.item)
    }

    @Test fun searchIsBoundedAndCanContinueWithoutMatchingSimilarText() = runBlocking {
        var calls = 0
        var current = snapshot(listOf(item("lookalike", "same")))
        assertEquals(TaskSearchResult.More, findTaskPage("target", { current }) { calls++; current })
        assertEquals(3, calls)
        assertEquals(TaskSearchResult.Found, findTaskPage("target", { current }) {
            calls++; snapshot(listOf(item("target", "same"))).also { current = it }
        })
        assertEquals(4, calls)
        assertEquals(TaskSearchResult.Found, findTaskPage("target", { current }) { fail("No network for loaded target"); null })
    }

    @Test fun missingResetAndFailureDoNotDrainPages() = runBlocking {
        assertEquals(TaskSearchResult.Missing, findTaskPage("gone", { snapshot(older = false) }) { fail(); null })
        try { findTaskPage("x", { snapshot() }) { error("offline") }; fail() } catch (e: IllegalStateException) { assertEquals("offline", e.message) }
        try { findTaskPage("x", { snapshot() }) { snapshot().copy(historyReset = true) }; fail() }
        catch (e: IllegalStateException) { assertTrue(e.message!!.contains("历史窗口")) }
    }

    @Test fun cancelledSearchStopsBeforeNextPage() = runBlocking {
        val started = CompletableDeferred<Unit>(); var calls = 0
        val job = launch { findTaskPage("x", { snapshot() }) { calls++; started.complete(Unit); awaitCancellation() } }
        started.await(); job.cancelAndJoin(); assertEquals(1, calls)
    }
}
