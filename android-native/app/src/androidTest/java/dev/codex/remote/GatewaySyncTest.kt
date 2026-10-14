package dev.codex.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import kotlin.concurrent.thread

/** Actual Android HTTP/JSON decoding. Only loopback synthetic content, no real account. */
@RunWith(AndroidJUnit4::class)
class GatewaySyncTest {
    private val meta = """"session":{"id":"t","status":"RUNNING"},"observation":{"state":"running"},"pendingQuestions":[],"pendingApprovals":[]"""
    private fun item(key: String, value: String, truncated: Boolean = false) = """{"key":"$key","revision":"r1","turnId":"turn","turnStatus":"inProgress","item":{"id":"$key","type":"agentMessage","text":"$value","truncated":$truncated,"fullBytes":9999}}"""
    private fun page(cursor: String, order: String, items: String, reset: Boolean = false) = """{"protocol":1,"cursor":"$cursor","reset":$reset,"hasOlder":true,"order":[$order],"items":[$items],$meta}"""

    @Test fun sendReturnsTurnReceiptWithoutWaitingForTranscriptRefresh() = runBlocking {
        val requests = CopyOnWriteArrayList<String>()
        SyncHttpFixture { path, _ ->
            requests.add(path)
            200 to """{"result":{"turnId":"accepted-turn","steered":false}}"""
        }.use { http ->
            assertEquals("accepted-turn", Gateway(http.url, "synthetic").send("t", "hello", "synthetic-message"))
            assertEquals(listOf("/api/sessions/t/messages"), requests.toList())
        }
    }

    @Test fun questionMetadataAndRevisionedAnswerUseExactContract() = runBlocking {
        val revision = "b".repeat(64); val questionId = "a".repeat(64)
        val question = """{"id":"$questionId","revision":"$revision","turnId":"turn","kind":"async","status":"pending","notice":"核对原题","questions":[{"id":"choice","header":"方式","question":"选哪个？","options":[{"label":"A","description":"推荐说明"}],"isSecret":false,"isOther":true}]}"""
        SyncHttpFixture { path, _ ->
            if (path.contains("/answer")) 200 to """{"status":"accepted","notice":"已接受"}"""
            else 200 to """{"protocol":1,"cursor":"${"c".repeat(64)}","reset":true,"hasOlder":false,"order":[],"items":[],"session":{"id":"t"},"observation":{"state":"waiting"},"pendingQuestions":[$question],"pendingApprovals":[],"controlNotice":"可回答","capabilities":{"questions":true,"approvals":false,"mode":false,"implementPlan":false,"create":false}}"""
        }.use { http ->
            val gateway = Gateway(http.url, "synthetic")
            val conversation = gateway.conversation("t")
            val parsed = conversation.questions.single()
            assertEquals("async", parsed.kind)
            assertEquals("推荐说明", parsed.questions.single().options.single().description)
            assertTrue(conversation.capabilities.questions)
            assertFalse(conversation.capabilities.approvals)
            assertEquals("可回答", conversation.controlNotice)
            assertEquals("accepted", gateway.answer("t", parsed, mapOf("choice" to "A")).status)
            assertTrue(http.bodies.single().let { body ->
                val json = JSONObject(body)
                json.getString("revision") == revision && json.getJSONObject("answers").getString("choice") == "A"
            })
        }
    }

    @Test fun networkUsesDeltaOlderFullAndSignalsWithoutLegacyTranscriptRequests() = runBlocking {
        val requests = CopyOnWriteArrayList<String>()
        val a = "a".repeat(64); val b = "b".repeat(64); val c = "c".repeat(64)
        SyncHttpFixture { path, _ ->
            requests.add(path)
            200 to when {
                path.contains("/signals") -> """{"turnId":"turn","completed":true,"plan":false,"questions":["q"],"approvals":[]}"""
                path.contains("/mobile-items/new") -> item("new", "FULL")
                path.contains("older=1") -> page(c, "\"old\",\"new\"", item("old", "older"))
                path.contains("cursor=$b") || path.contains("cursor=$c") -> """{"protocol":1,"cursor":"${if (path.contains(c)) c else b}","unchanged":true}"""
                path.contains("cursor=$a") -> page(b, "\"new\"", "")
                path.contains("/mobile-sync") -> page(a, "\"new\"", item("new", "preview", true), true)
                else -> "{}"
            }
        }.use { http ->
            val gateway = Gateway(http.url, "synthetic")
            assertEquals("preview", gateway.conversation("t").turns.single().items.single().text)
            gateway.conversation("t") // Metadata-only delta.
            assertEquals("FULL", gateway.fullItem("t", "new").turns.single().items.single().text)
            assertEquals("FULL", gateway.conversation("t").turns.single().items.single().text) // Unchanged.
            val older = gateway.conversation("t", older = true)
            assertEquals(listOf("older", "FULL"), older.turns.single().items.map { it.text })
            assertEquals(setOf("q"), gateway.signals("t").questions)
            assertFalse(requests.any { it == "/api/sessions/t" })
        }
    }

    @Test fun desktopOpenQueryIsExplicitAndNeverInheritedByPollingOrOlderHistory() = runBlocking {
        val requests = CopyOnWriteArrayList<String>()
        val cursor = "a".repeat(64)
        SyncHttpFixture { path, _ ->
            requests.add(path)
            200 to if (path.contains("cursor="))
                """{"protocol":1,"cursor":"$cursor","unchanged":true}"""
            else page(cursor, "\"one\"", item("one", "synthetic"), true)
        }.use { http ->
            val gateway = Gateway(http.url, "synthetic")
            gateway.conversation("t", openDesktop = true)
            gateway.conversation("t")
            gateway.conversation("t", older = true)
            assertTrue(requests[0].contains("open=1"))
            assertFalse(requests[1].contains("open="))
            assertFalse(requests[2].contains("open="))
            assertTrue(requests[2].contains("older=1"))
            try { gateway.conversation("t", older = true, openDesktop = true); fail("Older history must not open Desktop") }
            catch (_: IllegalArgumentException) { /* Explicitly incompatible request. */ }
            assertEquals(3, requests.size)
        }
    }

    @Test fun pendingDesktopOpenUsesResponseCodeAndFollowingReadNeverReopens() = runBlocking {
        val requests = CopyOnWriteArrayList<String>()
        val cursor = "b".repeat(64)
        SyncHttpFixture { path, _ ->
            requests.add(path)
            if (path.contains("open=1")) 503 to """{"error":"loading","code":"desktop_open_pending"}"""
            else 200 to page(cursor, "\"one\"", item("one", "synthetic"), true)
        }.use { http ->
            val gateway = Gateway(http.url, "synthetic")
            try { gateway.conversation("t", openDesktop = true); fail("Expected pending code") }
            catch (failure: GatewayException) {
                assertEquals(503, failure.status)
                assertEquals("desktop_open_pending", failure.code)
            }
            assertEquals("synthetic", gateway.conversation("t").turns.single().items.single().text)
            assertTrue(requests[0].contains("open=1"))
            assertFalse(requests[1].contains("open="))
        }
    }

    @Test fun summary304ReusesScopedCacheAndOldGatewayDoesNotTriggerHeavyFallback() = runBlocking {
        var summaries = 0; var conditional = false
        SyncHttpFixture { path, headers ->
            when (path) {
                "/api/sessions" -> {
                    summaries++
                    conditional = headers.any { it.equals("If-None-Match: \"test-etag\"", ignoreCase = true) }
                    (if (conditional) 304 else 200) to """{"sessions":[{"id":"t","title":"cached"}]}"""
                }
                else -> 404 to """{"error":"Not found"}"""
            }
        }.use { http ->
            val gateway = Gateway(http.url, "synthetic")
            assertEquals("cached", gateway.sessions().single().title)
            assertEquals("cached", gateway.sessions().single().title)
            assertEquals(2, summaries); assertTrue(conditional)
            try { gateway.conversation("t"); fail("Must require upgraded Gateway") }
            catch (failure: GatewayException) { assertEquals(426, failure.status) }
        }
    }
}

private class SyncHttpFixture(private val respond: (String, List<String>) -> Pair<Int, String>) : java.io.Closeable {
    private val running = AtomicBoolean(true)
    private val socket = ServerSocket(0)
    val url = "http://127.0.0.1:${socket.localPort}"
    val bodies = CopyOnWriteArrayList<String>()
    private val worker = thread(isDaemon = true, name = "mobile-sync-fixture") {
        while (running.get()) try {
            socket.accept().use { client ->
                client.soTimeout = 5000
                val reader = client.getInputStream().bufferedReader()
                val path = reader.readLine().split(' ')[1]
                val headers = mutableListOf<String>()
                while (true) { val line = reader.readLine(); if (line.isNullOrEmpty()) break; headers.add(line) }
                val length = headers.firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
                if (length > 0) {
                    val body = CharArray(length)
                    var read = 0
                    while (read < length) { val count = reader.read(body, read, length - read); if (count < 0) break; read += count }
                    bodies.add(String(body, 0, read))
                }
                val (status, payload) = respond(path, headers)
                val bytes = if (status == 304) byteArrayOf() else payload.toByteArray(Charsets.UTF_8)
                client.getOutputStream().apply {
                    write("HTTP/1.1 $status Test\r\nContent-Type: application/json\r\nETag: \"test-etag\"\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(bytes); flush()
                }
            }
        } catch (_: Exception) { /* Closing the test socket is expected. */ }
    }
    override fun close() { running.set(false); socket.close(); worker.join(5000) }
}
