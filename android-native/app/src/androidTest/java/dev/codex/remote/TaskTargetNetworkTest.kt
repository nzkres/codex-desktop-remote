package dev.codex.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class TaskTargetNetworkTest {
    @Test fun upgradeKeepsUnacknowledgedLegacyReceiptWithoutGivingItToAnotherComputerOrAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val previous = Credentials(context)
        try {
            previous.clear()
            previous.url = "http://127.0.0.1:18999"
            previous.account = "upgrade-owner"
            previous.token = "a".repeat(64)
            val oldReceipt = previous.pendingMessageId(previous.url, "same-thread", "same-message")
            val upgraded = Credentials(context)
            val ownerScope = draftScope(upgraded.url, upgraded.account, upgraded.token)
            val otherComputer = draftScope(TaskTarget("b".repeat(32)).endpoint(upgraded.url), upgraded.account, upgraded.token)
            assertNotEquals(oldReceipt, upgraded.pendingMessageId(otherComputer, "same-thread", "same-message"))
            upgraded.account = "another-account"
            val otherAccount = draftScope(upgraded.url, upgraded.account, upgraded.token)
            assertNotEquals(oldReceipt, upgraded.pendingMessageId(otherAccount, "same-thread", "same-message"))
            upgraded.account = "upgrade-owner"
            assertEquals(oldReceipt, upgraded.pendingMessageId(ownerScope, "same-thread", "same-message"))
            assertEquals(oldReceipt, Credentials(context).pendingMessageId(ownerScope, "same-thread", "same-message"))
            upgraded.acknowledgeMessage(ownerScope, "same-thread")
            assertNotEquals(oldReceipt, upgraded.pendingMessageId(ownerScope, "same-thread", "same-message"))
        } finally { previous.clear() }
    }

    @Test fun twoTargetsUseExactPathsAndFailureNeverFallsBackToLegacy() = runBlocking {
        val paths = CopyOnWriteArrayList<String>()
        ServerSocket(0).use { server ->
            val worker = thread(isDaemon = true) {
                while (!server.isClosed) try {
                    server.accept().use { socket ->
                        socket.soTimeout = 3000
                        val input = socket.getInputStream().bufferedReader()
                        val path = input.readLine().split(' ')[1]
                        paths.add(path)
                        var length = 0
                        while (true) {
                            val header = input.readLine()
                            if (header.isNullOrEmpty()) break
                            if (header.startsWith("Content-Length:", true)) length = header.substringAfter(':').trim().toInt()
                        }
                        repeat(length) { input.read() } // This fixture sends ASCII request bodies only.
                        val unavailable = path.contains("b".repeat(32))
                        val response = when {
                            unavailable -> """{"error":"terminal_offline"}"""
                            else -> """{"projects":[{"id":"same-project","name":"Computer A"}]}"""
                        }.toByteArray()
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 ${if (unavailable) "503 Unavailable" else "200 OK"}\r\nContent-Type: application/json\r\nContent-Length: ${response.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(response); flush()
                        }
                    }
                } catch (_: Exception) { }
            }
            val base = "http://127.0.0.1:${server.localPort}"
            val a = Gateway(base, "a".repeat(64), TaskTarget("a".repeat(32)))
            val b = Gateway(base, "a".repeat(64), TaskTarget("b".repeat(32)))
            assertEquals("Computer A", a.projects().single().name)
            try { b.projects(); fail("Offline terminal must fail") } catch (expected: GatewayException) {
                assertEquals(503, expected.status)
                assertTrue(networkError(expected).contains("不会切到其他电脑"))
            }
            try { b.create("same-project", "fixture", "default"); fail("Creation must not use old host") }
            catch (expected: GatewayException) { assertEquals(503, expected.status) }
            assertEquals(listOf("/terminal-api/${a.target.id}/api/projects", "/terminal-api/${b.target.id}/api/projects",
                "/terminal-api/${b.target.id}/api/projects/same-project/sessions"), paths.toList())
            server.close()
            worker.join(3000)
        }
    }

    @Test fun savedSelectionAndNotificationsAreAccountAndTerminalScoped() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val credentials = Credentials(context)
        try {
            credentials.clear()
            credentials.url = "http://127.0.0.1:18999"
            credentials.account = "synthetic-a"
            credentials.token = "a".repeat(64)
            val target = TaskTarget("a".repeat(32), "合成电脑 A")
            credentials.selectTarget(target)
            assertEquals(target, credentials.taskTarget())
            assertNotEquals(TaskNotifications.scope(credentials), TaskNotifications.scope(credentials, target))
            credentials.account = "synthetic-b"
            assertEquals(TaskTarget(), credentials.taskTarget())
            credentials.account = "synthetic-a"
            assertEquals(target, credentials.taskTarget())
            val aScope = draftScope(target.endpoint(credentials.url), credentials.account, credentials.token)
            val bScope = draftScope(TaskTarget("b".repeat(32)).endpoint(credentials.url), credentials.account, credentials.token)
            val receipt = credentials.pendingMessageId(aScope, "same-thread", "same-message")
            assertEquals(receipt, credentials.pendingMessageId(aScope, "same-thread", "same-message"))
            assertNotEquals(receipt, credentials.pendingMessageId(bScope, "same-thread", "same-message"))
            credentials.acknowledgeMessage(bScope, "same-thread")
            assertEquals(receipt, credentials.pendingMessageId(aScope, "same-thread", "same-message"))
        } finally { credentials.clear() }
    }
}
