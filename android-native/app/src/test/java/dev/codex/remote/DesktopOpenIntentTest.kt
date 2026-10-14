package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test

class DesktopOpenIntentTest {
    @Test fun requestIsConsumedOnceByItsOriginalClientAndConversation() {
        val original = Any()
        val other = Any()
        val intent = DesktopOpenIntent()
        intent.request(original, "thread-a")

        assertFalse(intent.consume(other, "thread-a"))
        assertFalse(intent.consume(original, "thread-b"))
        assertTrue(intent.consume(original, "thread-a"))
        assertFalse(intent.consume(original, "thread-a"))
    }

    @Test fun switchingOrNotificationNavigationDropsOldIntent() {
        val original = Any()
        val other = Any()
        val intent = DesktopOpenIntent()
        intent.request(original, "thread-a")
        intent.clear()
        assertFalse(intent.consume(original, "thread-a"))

        intent.request(original, "thread-a")
        intent.request(other, "thread-b")
        assertFalse(intent.consume(original, "thread-a"))
        assertTrue(intent.consume(other, "thread-b"))
    }

    @Test fun pendingOpenWaitOnlyAcceptsSpecificPureReadFailures() {
        assertTrue(desktopOpenMayStillLoad("owner_unavailable"))
        assertTrue(desktopOpenMayStillLoad("snapshot_unavailable"))
        for (code in listOf(null, "desktop_open_pending", "permissions_changed", "send_unknown", "thread_busy"))
            assertFalse(desktopOpenMayStillLoad(code))
    }
}
