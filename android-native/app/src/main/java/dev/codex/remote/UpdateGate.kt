package dev.codex.remote

import org.json.JSONObject

internal data class ConnectorUpdateProof(val version: String)

// Relay errors use `error`; connector errors use `code`. Never display arbitrary server text.
internal fun gatewayErrorCode(row: JSONObject): String? =
    sequenceOf(row.optString("code"), row.optString("error"))
        .firstOrNull { it.matches(Regex("[a-z_]{1,60}")) }

internal fun updateStatusFailure(error: Exception): UpdateFailure = when (error) {
    is UpdateFailure -> error
    is GatewayException -> UpdateFailure(error.code ?: when (error.status) {
        401 -> "update_login_required"
        403 -> "update_access_denied"
        404, 426 -> "update_connector_unsupported"
        else -> "update_status_http_${error.status}"
    })
    is java.net.SocketTimeoutException -> UpdateFailure("update_status_timeout")
    is java.io.IOException -> UpdateFailure("update_status_network")
    else -> UpdateFailure("update_status_invalid")
}

internal fun updateBlockReason(code: String): String? = when (code) {
    "terminal_paused" -> "这台电脑的远程访问已暂停，请到终端页恢复连接"
    "terminal_offline" -> "电脑连接器离线，请在电脑端连接后重试"
    "terminal_credential_unavailable", "gateway_tunnel_unverified" -> "电脑任务通道尚未连接，请在电脑连接器核对状态"
    "thread_busy" -> "电脑任务正在运行或等待回答、批准，请处理完成后重试"
    "runtime_status_unknown" -> "Desktop 尚未确认任务空闲，请在电脑端核对任务状态"
    "send_unknown", "receipt_invalid", "receipt_limit" -> "电脑连接器的发送回执尚未核对，请在电脑端检查；不会自动重发"
    "update_pending_send" -> "手机保留了尚未确认的发送记录，请核对对应对话；不会自动清除或重发"
    "permissions_changed", "permissions_unverified" -> "Desktop 任务权限尚未核验，请在电脑连接器核对权限"
    "operation_busy", "update_local_busy" -> "另一项操作尚未结束，请等待操作回执后重试"
    "update_uncertain" -> "电脑端存在结果未确认的操作，请先核对操作结果"
    "update_not_connected", "update_target_required" -> "请先在终端页选择要连接的电脑"
    "update_target_changed" -> "所选电脑已变化，请重新检查更新"
    "update_connector_changed" -> "连接器版本在下载期间发生变化，请重新检查更新"
    "update_login_required", "login_required", "login_expired" -> "登录已失效，请重新登录后检查更新"
    "update_access_denied", "terminal_access_revoked" -> "当前账号无权访问这台电脑，请核对终端授权"
    "update_connector_unsupported", "unsupported_route" -> "连接器不支持更新状态核验，请先升级电脑连接器"
    "update_status_timeout", "terminal_request_timeout" -> "核对连接器状态超时，请检查网络和电脑连接"
    "update_status_network" -> "无法连接状态接口，请检查网络和电脑连接"
    "local_unavailable" -> "电脑连接器无法读取 Desktop 状态，请查看电脑端诊断"
    "update_activity_uncertain", "update_status_invalid" -> "连接器未提供完整有效的安全状态，请在电脑端诊断后重试"
    else -> null
}

internal fun requireLocalUpdateSafety(targetId: String, busy: Boolean, pendingSend: Boolean) {
    if (!targetId.matches(Regex("[0-9a-f]{32}"))) throw UpdateFailure("update_target_required")
    if (busy) throw UpdateFailure("update_local_busy")
    if (pendingSend) throw UpdateFailure("update_pending_send")
}

/** A fresh, exact connector response is required; old connectors and unknown fields fail closed. */
internal fun parseConnectorUpdateProof(row: JSONObject): ConnectorUpdateProof {
    if (row.keys().asSequence().toSet() != setOf("idle", "version", "permissionsVerified", "receiptsCertain") ||
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
