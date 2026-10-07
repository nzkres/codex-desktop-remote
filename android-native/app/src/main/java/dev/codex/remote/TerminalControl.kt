package dev.codex.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RemoteTerminal(val id: String, val name: String, val state: String, val online: Boolean)
data class TerminalPairing(val id: String, val name: String, val state: String, val expires: Long)
data class PairingCode(val code: String, val expires: Long)
data class TerminalSnapshot(val terminals: List<RemoteTerminal>, val pairings: List<TerminalPairing>)

interface TerminalControl {
    suspend fun snapshot(): TerminalSnapshot
    suspend fun createPairing(): PairingCode
    suspend fun confirm(id: String, name: String)
    suspend fun cancel(id: String)
    suspend fun change(id: String, action: String, name: String)
}

internal class TerminalControlClient(private val base: String, private val token: String) : TerminalControl {
    private fun id(value: String): String {
        require(value.matches(Regex("[0-9a-f]{32}"))) { "终端标识无效，请刷新列表。" }
        return value
    }
    private suspend fun request(path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val connection = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            when (val status = connection.responseCode) {
                401, 403 -> error("登录已失效或没有权限，请重新登录后再试。")
                404, 405 -> error("中继尚未启用远程终端管理，需要升级中继；现有聊天入口不受影响。")
                !in 200..299 -> error("终端服务暂不可用（$status），请刷新核对。")
            }
            val bytes = connection.inputStream.use { it.readBytesBounded(65536) }
            if (bytes.isEmpty()) JSONObject() else JSONObject(bytes.toString(Charsets.UTF_8))
        } finally { connection.disconnect() }
    }
    private fun java.io.InputStream.readBytesBounded(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(buffer)
            if (count < 0) return output.toByteArray()
            check(output.size() + count <= limit) { "终端列表过大，请联系管理员。" }
            output.write(buffer, 0, count)
        }
    }
    override suspend fun snapshot(): TerminalSnapshot {
        val devices = request("/control-api/terminals").getJSONArray("terminals")
        val pending = request("/control-api/pairings").getJSONArray("pairings")
        return TerminalSnapshot((0 until devices.length()).map {
            val row = devices.getJSONObject(it)
            RemoteTerminal(id(row.getString("id")), row.getString("name"), row.getString("state"), row.getBoolean("online"))
        }, (0 until pending.length()).map {
            val row = pending.getJSONObject(it)
            TerminalPairing(id(row.getString("id")), row.optString("name", "新电脑"), row.getString("state"), row.getLong("expires"))
        })
    }
    override suspend fun createPairing(): PairingCode {
        val result = request("/control-api/pairings", JSONObject())
        val code = result.getString("code")
        check(code.matches(Regex("[0-9A-F]{12}"))) { "配对响应无效，请刷新核对。" }
        return PairingCode(code, result.getLong("expiresAt"))
    }
    override suspend fun confirm(id: String, name: String) { request("/control-api/pairings/${id(id)}/confirm", JSONObject().put("name", name)) }
    override suspend fun cancel(id: String) { request("/control-api/pairings/${id(id)}/cancel", JSONObject()) }
    override suspend fun change(id: String, action: String, name: String) {
        require(action in setOf("rename", "pause", "resume", "remove"))
        request("/control-api/terminals/${id(id)}/$action", JSONObject().put("name", name))
    }
}
