package dev.codex.remote

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateGateTest {
    @Test fun relayAndConnectorErrorsKeepTheirActualReason() {
        for (code in listOf("terminal_paused", "terminal_offline", "thread_busy", "send_unknown", "permissions_changed")) {
            for (field in listOf("error", "code")) {
                val parsed = gatewayErrorCode(JSONObject().put(field, code))
                val failure = updateStatusFailure(GatewayException("server text", 503, parsed))
                assertEquals(code, failure.code)
                assertNotNull(updateBlockReason(failure.code))
            }
        }
        assertNotEquals(updateBlockReason("terminal_paused"), updateBlockReason("terminal_offline"))
        assertNotEquals(updateBlockReason("send_unknown"), updateBlockReason("update_pending_send"))
    }
    @Test fun arbitraryServerTextIsNotAnErrorCode() {
        assertNull(gatewayErrorCode(JSONObject().put("error", "private server text / path")))
        assertEquals("thread_busy", gatewayErrorCode(JSONObject().put("code", "thread_busy").put("error", "terminal_offline")))
        assertEquals("update_login_required", updateStatusFailure(GatewayException("private", 401)).code)
        assertEquals("update_status_timeout", updateStatusFailure(java.net.SocketTimeoutException()).code)
        assertEquals("update_status_network", updateStatusFailure(java.io.IOException()).code)
        assertEquals("update_status_invalid", updateStatusFailure(IllegalArgumentException()).code)
    }
    @Test fun localReceiptAndBusyStateRemainBlockingWithDistinctReasons() {
        fun rejected(target: String, busy: Boolean, pending: Boolean): String {
            try { requireLocalUpdateSafety(target, busy, pending); fail("must block") }
            catch (error: UpdateFailure) { return error.code }
            error("unreachable")
        }
        assertEquals("update_target_required", rejected("", false, false))
        assertEquals("update_local_busy", rejected("a".repeat(32), true, false))
        assertEquals("update_pending_send", rejected("a".repeat(32), false, true))
        requireLocalUpdateSafety("a".repeat(32), false, false)
    }
    private fun row() = JSONObject()
        .put("idle", true).put("version", "0.1.0-alpha.3")
        .put("permissionsVerified", true).put("receiptsCertain", true)
    private fun rejects(value: JSONObject) {
        try { parseConnectorUpdateProof(value); fail("expected rejection") }
        catch (error: UpdateFailure) { assertEquals("update_activity_uncertain", error.code) }
    }
    @Test fun exactFreshProofAndLocalGate() {
        val proof = parseConnectorUpdateProof(row())
        assertTrue(updateMayInstall("a".repeat(32), false, false, proof))
        assertFalse(updateMayInstall("", false, false, proof))
        assertFalse(updateMayInstall("a".repeat(32), true, false, proof))
        assertFalse(updateMayInstall("a".repeat(32), false, true, proof))
        assertFalse(updateMayInstall("a".repeat(32), false, false, null))
    }
    @Test fun falseUnknownAndIncompleteResponsesBlock() {
        rejects(row().put("idle", false))
        rejects(row().put("receiptsCertain", JSONObject.NULL))
        rejects(row().put("permissionsVerified", "true"))
        val missing = row()
        missing.remove("idle")
        rejects(missing)
    }
    @Test fun oldOrUnexpectedShapeBlocks() {
        rejects(JSONObject())
        rejects(row().put("extra", "unknown"))
        rejects(row().put("version", "unknown"))
    }
}
