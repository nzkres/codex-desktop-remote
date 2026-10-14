package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test

class TranscriptActivityTest {
    private val session = Session("fixture", "p", "task", "RUNNING", "t", "default", 0)
    private val command = MessageItem("c", "commandExecution", "", "echo hello", "", "", "inProgress", null, emptyList())
    private fun conversation(state: String = "running", items: List<MessageItem> = emptyList()) =
        Conversation(listOf(Turn("t", "inProgress", items)), Observation(state, 0, "gateway", "reasoning"), emptyList(), emptyList())

    @Test fun onlyCurrentWorkAnimates() {
        assertEquals(TranscriptActivity("正在思考", true), transcriptActivity(session, conversation()))
        assertEquals(TranscriptActivity("正在运行命令", true), transcriptActivity(session, conversation(items = listOf(command))))
        assertEquals(TranscriptActivity("正在修改文件", true), transcriptActivity(session, conversation(items = listOf(command.copy(type = "fileChange")))))
        val done = session.copy(status = "COMPLETED", activeTurnId = null)
        assertNull(transcriptActivity(done, conversation("completed", listOf(command))))
        assertNull(transcriptActivity(session, null))
    }

    @Test fun attentionAndQuietDoNotPretendToBeRunning() {
        assertEquals(TranscriptActivity("等待你的批准"), transcriptActivity(session.copy(status = "WAITING_APPROVAL"), conversation()))
        assertEquals(TranscriptActivity("等待你的回答"), transcriptActivity(session.copy(status = "WAITING_USER"), conversation()))
        assertEquals(TranscriptActivity("等待新的活动记录"), transcriptActivity(session, conversation("quiet")))
        assertFalse(transcriptActivity(session, conversation().copy(questions = listOf(QuestionRequest("q", emptyList()))))!!.animated)
        assertFalse(transcriptActivity(session, conversation().copy(approvals = listOf(Approval("a", "t", "command", "details", true))))!!.animated)
    }

    @Test fun completedQuestionCardsStayReadableWithoutWaitingIndicator() {
        val done = session.copy(status = "COMPLETED", activeTurnId = null)
        val base = conversation("completed")
        for (status in listOf("accepted", "closed")) {
            val current = base.copy(questions = listOf(QuestionRequest("q", emptyList(), status = status)))
            assertFalse(status, current.needsAttention())
            assertNull(status, transcriptActivity(done, current))
            assertNull(status, transcriptActivity(done.copy(status = "WAITING_USER"), current))
        }
        for (status in listOf("pending", "unknown", "desktop_required", "future_status")) {
            val current = base.copy(questions = listOf(QuestionRequest("q", emptyList(), status = status)))
            assertTrue(status, current.needsAttention())
            assertEquals(status, TranscriptActivity("等待你的回答"), transcriptActivity(done, current))
        }
        assertTrue(base.copy(approvals = listOf(Approval("a", "t", "command", "details", false))).needsAttention())
    }

    @Test fun oldTurnAndCompletedCommandDoNotDescribeCurrentActivity() {
        val old = Turn("old", "inProgress", listOf(command))
        val latest = Turn("t", "inProgress", listOf(command.copy(id = "new", status = "completed", exitCode = 0)))
        assertEquals("正在思考", transcriptActivity(session, conversation().copy(turns = listOf(old, latest)))!!.label)
    }

    @Test fun collapsedLabelsPreserveFailureAndUnknownStatus() {
        assertEquals("命令执行失败", toolSummaryLabel(command.copy(status = "completed", exitCode = 1)))
        assertEquals("已运行", toolSummaryLabel(command.copy(status = "completed", exitCode = 0)))
        assertEquals("命令", toolSummaryLabel(command.copy(status = "")))
        assertEquals("文件修改失败", toolSummaryLabel(command.copy(type = "fileChange", status = "failed")))
    }
}
