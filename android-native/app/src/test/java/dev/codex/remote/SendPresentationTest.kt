package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test

class SendPresentationTest {
    private fun message(id: String, text: String) = MessageItem(id, "userMessage", text, "", "", "", "", null, emptyList())
    @Test fun usesReceiptTurnInsteadOfMatchingAnOlderIdenticalPrompt() {
        val receipt = SendPresentation("local", "继续", "new-turn", setOf("old"))
        val turns = listOf(Turn("old-turn", "completed", listOf(message("old", "继续"))), Turn("new-turn", "inProgress", listOf(message("new", "继续"))))
        assertEquals("new", confirmedSentItem(receipt, turns)?.id)
    }
    @Test fun truncatedPreviewStillMatchesAuthoritativeTurn() {
        val receipt = SendPresentation("local", "很长的消息".repeat(100), "new-turn", emptySet())
        assertEquals("new", confirmedSentItem(receipt, listOf(Turn("new-turn", "inProgress", listOf(message("new", "很长的…")))))?.id)
    }
    @Test fun missingReceiptUsesOnlyNewUserItemsWithMatchingText() {
        val receipt = SendPresentation("local", "继续", null, setOf("old"))
        assertNull(confirmedSentItem(receipt, listOf(Turn("old-turn", "completed", listOf(message("old", "继续"), message("other", "别的消息"))))))
        assertEquals("new", confirmedSentItem(receipt, listOf(Turn("new-turn", "completed", listOf(message("new", "继续\n")))))?.id)
    }
    @Test fun confirmedReceiptNeverAttachesToAnotherRepeatedMessageAfterHistoryReset() {
        val receipt = SendPresentation("local", "继续", "turn", emptySet(), confirmedItemId = "confirmed")
        assertNull(confirmedSentItem(receipt, listOf(Turn("later", "completed", listOf(message("later", "继续"))))))
        assertNotNull(receipt.confirmedItemId) // UI uses this to prevent resurrecting a local echo.
    }
}
