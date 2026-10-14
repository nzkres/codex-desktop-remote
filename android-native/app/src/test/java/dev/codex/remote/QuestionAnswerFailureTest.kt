package dev.codex.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuestionAnswerFailureTest {
    @Test fun onlyVerifiedPreflightRejectionsCanRestoreEditing() {
        for ((code, status) in mapOf("invalid_answer" to 400, "question_changed" to 409,
            "question_unsupported" to 501, "operation_busy" to 409, "update_quarantine" to 503,
            "disconnect_preparing" to 503, "runtime_status_unknown" to 503,
            "permissions_changed" to 409, "outside_scope" to 403)) {
            assertTrue(code, questionAnswerRejectedBeforeSend(GatewayException("not sent", status, code)))
        }
        assertTrue(questionAnswerRejectedBeforeSend(GatewayException("not sent", 409, "question_unsupported")))
        assertFalse(questionAnswerRejectedBeforeSend(GatewayException("unexpected status", 503, "invalid_answer")))
        assertFalse(questionAnswerRejectedBeforeSend(GatewayException("bad gateway", 502, "operation_busy")))
        assertFalse(questionAnswerRejectedBeforeSend(GatewayException("timeout", 0)))
        assertFalse(questionAnswerRejectedBeforeSend(GatewayException("receipt uncertain", 503, "receipt_unknown")))
    }
}
