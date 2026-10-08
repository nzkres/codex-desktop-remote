package dev.codex.remote

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateGateTest {
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
