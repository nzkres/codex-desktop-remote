package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test

class TaskSignalsTest {
    private fun activity(id: String = "a", stamp: String = "done:1", active: Boolean = false, time: Long = 110) = SignalActivity(id, stamp, active, time)
    private fun snapshot(turn: String = "t1", done: Boolean = true, questions: Set<String> = emptySet(), approvals: Set<String> = emptySet(), plan: Boolean = false) = SignalSnapshot(turn, done, plan, questions, approvals)

    @Test fun firstSyncDoesNotFloodOldCompletions() {
        val tracker = TaskSignalTracker(startedAt = 100)
        assertTrue(tracker.candidates(listOf(activity())).isEmpty())
        assertTrue(tracker.candidates(listOf(activity())).isEmpty())
    }
    @Test fun fastTurnBetweenPollsIsDetected() {
        val tracker = TaskSignalTracker(startedAt = 100)
        tracker.candidates(listOf(activity()))
        val next = activity(stamp = "done:2")
        assertEquals(listOf(next), tracker.candidates(listOf(next)))
        val events = tracker.preview(next, snapshot("t2"))
        assertEquals("completed:t2", events.single().key)
        tracker.acknowledge(next, events)
        assertTrue(tracker.preview(next, snapshot("t2")).isEmpty())
    }
    @Test fun pendingQuestionsAndApprovalsAreNotSuppressedOnFirstSync() {
        val tracker = TaskSignalTracker(startedAt = 100)
        val waiting = activity(active = true)
        assertEquals(listOf(waiting), tracker.candidates(listOf(waiting)))
        val state = snapshot(done = false, questions = setOf("q1", "q2"), approvals = setOf("p1"))
        val events = tracker.preview(waiting, state)
        assertEquals(3, events.size)
        tracker.acknowledge(waiting, events)
        assertTrue(tracker.preview(waiting, state).isEmpty())
        assertEquals("question:q3", tracker.preview(waiting, state.copy(questions = setOf("q3"))).single().key)
    }
    @Test fun failedReadOrDeliveryDoesNotAdvanceCursor() {
        val tracker = TaskSignalTracker(startedAt = 100)
        tracker.candidates(listOf(activity()))
        val next = activity(stamp = "done:2")
        tracker.candidates(listOf(next))
        assertEquals(listOf(next), tracker.candidates(listOf(next)))
        assertEquals(tracker.preview(next, snapshot("t2")), tracker.preview(next, snapshot("t2")))
    }
    @Test fun persistedLedgerDeduplicatesAfterProcessRestart() {
        val tracker = TaskSignalTracker(startedAt = 100)
        val running = activity(active = true)
        tracker.candidates(listOf(running))
        tracker.acknowledge(running, tracker.preview(running, snapshot()))
        val restored = TaskSignalTracker(true, 100, tracker.checkpoints.toMutableMap())
        assertTrue(restored.preview(running, snapshot()).isEmpty())
    }
    @Test fun importedOldThreadDoesNotNotifyButNewCompletedThreadDoes() {
        val tracker = TaskSignalTracker(startedAt = 100)
        tracker.candidates(emptyList())
        assertTrue(tracker.candidates(listOf(activity(time = 90))).isEmpty())
        val fresh = activity(id = "new", time = 120)
        assertEquals(listOf(fresh), tracker.candidates(listOf(fresh)))
    }
    @Test fun interruptedOrFailedTurnIsNeverAnnouncedAsCompleted() {
        val tracker = TaskSignalTracker(startedAt = 100)
        assertTrue(tracker.preview(activity(), snapshot(done = false)).isEmpty())
    }
    @Test fun planCompletionRequiresAttentionAndEmptyTurnDoesNotNotify() {
        val tracker = TaskSignalTracker(startedAt = 100)
        assertEquals("plan", tracker.preview(activity(), snapshot(plan = true)).single().kind)
        assertTrue(tracker.preview(activity(), snapshot(turn = "")).isEmpty())
    }
    @Test fun identicalRequestIdsInDifferentThreadsDoNotCollide() {
        val tracker = TaskSignalTracker(startedAt = 100)
        val state = snapshot(done = false, questions = setOf("q1"))
        tracker.acknowledge(activity(), tracker.preview(activity(), state))
        assertEquals(1, tracker.preview(activity(id = "b"), state).size)
    }
}
