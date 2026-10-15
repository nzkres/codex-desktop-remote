package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test

class InterfacePreviewStateTest {
    @Test fun completionIsUnreadUntilLatestResultVisibleInForeground() {
        val done = InterfacePreviewState().complete()
        assertTrue(done.unread)
        assertTrue(done.seeResult(false, true).unread)
        assertTrue(done.seeResult(true, false).unread)
        assertFalse(done.seeResult(true, true).unread)
    }
    @Test fun anotherCompletionBecomesUnreadAgain() {
        val read = InterfacePreviewState().complete().seeResult(true, true)
        val next = read.complete()
        assertTrue(next.unread)
        assertEquals(read.revision + 1, next.revision)
        assertEquals(read.seenRevision, next.seenRevision)
    }
    @Test fun unknownCannotBeReplayedAndKeepsDraft() {
        val start = InterfacePreviewState(phase = PreviewPhase.Idle)
        val unknown = start.submit().receive(PreviewOutcome.Unknown)
        assertEquals(start.draft, unknown.draft)
        assertFalse(unknown.canSend)
        assertEquals(unknown, unknown.submit())
    }
    @Test fun definiteFailureKeepsDraftAndAllowsExplicitRetry() {
        val start = InterfacePreviewState(phase = PreviewPhase.Idle)
        val failed = start.submit().receive(PreviewOutcome.Failure)
        assertEquals(start.draft, failed.draft)
        assertTrue(failed.canSend)
        assertEquals(PreviewPhase.Sending, failed.submit().phase)
    }
    @Test fun sendingIsSingleShotAndAcknowledgementClearsOnlyDemoDraft() {
        val pending = InterfacePreviewState(phase = PreviewPhase.Idle).submit()
        assertFalse(pending.canSend)
        assertEquals(pending, pending.submit())
        val accepted = pending.receive(PreviewOutcome.Success)
        assertEquals(PreviewPhase.Received, accepted.phase)
        assertEquals("", accepted.draft)
        assertFalse(accepted.canSend)
    }
    @Test fun readWatermarkCanBeRestoredWithoutMarkingNewResultsRead() {
        val restored = InterfacePreviewState(phase = PreviewPhase.Completed, revision = 4, seenRevision = 3)
        assertTrue(restored.unread)
        assertFalse(restored.seeResult(true, true).unread)
        assertTrue(restored.copy(phase = PreviewPhase.Running).seeResult(true, true).unread)
    }
}
