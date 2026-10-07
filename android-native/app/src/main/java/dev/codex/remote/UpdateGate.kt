package dev.codex.remote

import org.json.JSONObject

internal data class ConnectorUpdateProof(val version: String)

/** A fresh, exact connector response is required; old connectors and unknown fields fail closed. */
internal fun parseConnectorUpdateProof(row: JSONObject): ConnectorUpdateProof {
    if (row.keySet() != setOf("idle", "version", "permissionsVerified", "receiptsCertain") ||
        row.opt("idle") != true || row.opt("permissionsVerified") != true ||
        row.opt("receiptsCertain") != true || row.opt("version") !is String) {
        throw UpdateFailure("update_activity_uncertain")
    }
    val version = row.getString("version")
    if (!version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-alpha\\.[0-9]+)?"))) {
        throw UpdateFailure("update_activity_uncertain")
    }
    return ConnectorUpdateProof(version)
}

internal fun updateMayInstall(targetId: String, busy: Boolean, pendingSend: Boolean,
    proof: ConnectorUpdateProof?): Boolean =
    targetId.matches(Regex("[0-9a-f]{32}")) && !busy && !pendingSend && proof != null
