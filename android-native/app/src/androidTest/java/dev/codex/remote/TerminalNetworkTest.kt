package dev.codex.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL

/** Real Android network client -> adb reverse -> real isolated Python relay/auth.
 * The connector protocol is driven synthetically; this is not Windows DPAPI QA.
 */
@RunWith(AndroidJUnit4::class)
class TerminalNetworkTest {
    @Test fun realRelayPairingAndManagementLifecycle() = runBlocking {
        val port = InstrumentationRegistry.getArguments().getString("terminalRelayPort")
        assumeTrue("Requires isolated CI relay", port != null)
        require(port == "18766")
        val base = "http://127.0.0.1:$port"
        val client = TerminalControlClient(base, "a".repeat(64))
        val deviceToken = "b".repeat(64)
        fun device(path: String, body: JSONObject = JSONObject()): Pair<Int, JSONObject> {
            val connection = URL(base + path).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = false
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $deviceToken")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val raw = (if (status < 400) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
                return status to if (raw.isBlank()) JSONObject() else JSONObject(raw)
            } finally { connection.disconnect() }
        }
        assertTrue(client.snapshot().terminals.isEmpty())
        val code = client.createPairing()
        val claimed = device("/connector-api/claim", JSONObject().put("code", code.code).put("name", "合成网络电脑").put("platform", "windows"))
        assertEquals(200, claimed.first)
        val pending = client.snapshot().pairings.single()
        assertEquals("claimed", pending.state)
        assertEquals("合成网络电脑", pending.name)
        client.confirm(pending.id, "Android 确认电脑")
        val confirmed = device("/connector-api/pairings/${pending.id}")
        assertEquals(200, confirmed.first)
        val id = confirmed.second.getString("terminalId")
        val heartbeat = "/connector-api/terminals/$id/heartbeat"
        val heartbeatBody = JSONObject().put("executor", "unknown").put("version", "fixture")
        assertEquals(204, device(heartbeat, heartbeatBody).first)
        assertTrue(client.snapshot().terminals.single().online)
        client.change(id, "rename", "网络重命名")
        assertEquals("网络重命名", client.snapshot().terminals.single().name)
        client.change(id, "pause", "")
        assertEquals("paused", client.snapshot().terminals.single().state)
        assertEquals(403, device(heartbeat, heartbeatBody).first)
        client.change(id, "resume", "")
        assertEquals("active", client.snapshot().terminals.single().state)
        client.change(id, "remove", "")
        assertTrue(client.snapshot().terminals.isEmpty())
        assertEquals(403, device(heartbeat, heartbeatBody).first)
        client.createPairing()
        client.cancel(client.snapshot().pairings.single().id)
        assertTrue(client.snapshot().pairings.isEmpty())
        try {
            TerminalControlClient(base, "c".repeat(64)).snapshot()
            fail("Invalid login must not access management")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().startsWith("登录"))
        }
    }
}
