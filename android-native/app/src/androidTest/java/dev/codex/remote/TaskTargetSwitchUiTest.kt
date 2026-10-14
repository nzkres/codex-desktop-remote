package dev.codex.remote

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** Real root navigation against synthetic same-ID computers; no live relay or account. */
@RunWith(AndroidJUnit4::class)
class TaskTargetSwitchUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun delayedPreviousComputerCannotReplaceSelectedTaskList() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val credentials = Credentials(context)
        MultiComputerFixture().use { server ->
            TaskNotifications.setEnabled(context, false)
            TaskNotifications.stop(context, clear = true)
            credentials.clear()
            credentials.url = "http://127.0.0.1:${server.port}"
            credentials.token = "a".repeat(64)
            credentials.account = "synthetic-switch"
            credentials.selectTarget(TaskTarget(server.a, "电脑 A"))
            TaskNotifications.prefs(context).edit().putBoolean("introduced", true).commit()
            try {
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                    compose.waitUntil(15_000) { server.aStarted.count == 0L }
                    compose.onNodeWithContentDescription("远程终端页面").performClick()
                    compose.waitUntil(15_000) { compose.onAllNodesWithTag("terminal-open-${server.b}").fetchSemanticsNodes().isNotEmpty() }
                    compose.onNodeWithTag("terminal-open-${server.b}").performScrollTo().performClick()
                    compose.waitUntil(15_000) { compose.onAllNodesWithText("B 的对话").fetchSemanticsNodes().isNotEmpty() }
                    server.releaseA.countDown()
                    compose.waitUntil(10_000) { server.aFinished.count == 0L }
                    compose.waitForIdle()
                    compose.onNodeWithText("B 的对话").assertIsDisplayed()
                    compose.onNodeWithText("A 的对话").assertDoesNotExist()
                    assertEquals(server.b, credentials.taskTarget().id)
                    assertFalse(server.paths.any { it == "/api/projects" || it == "/api/sessions" })
                    captureTestScreen("multi-computer-selected-b")
                    // This connector has no create route. Verify project isolation on the
                    // selected task row and the disabled entry instead of opening an unsupported form.
                    compose.onNodeWithText("B 项目", substring = true).assertIsDisplayed()
                    compose.onNodeWithText("A 项目", substring = true).assertDoesNotExist()
                    compose.onNodeWithTag("new-conversation").assertIsNotEnabled().performClick()
                    compose.onNodeWithTag("confirm-new-conversation").assertDoesNotExist()
                    compose.onNodeWithText("新建对话请在电脑处理；已有任务可在手机继续。").assertIsDisplayed()
                    captureTestScreen("multi-computer-selected-project-create-unavailable")
                }
            } finally {
                server.releaseA.countDown()
                credentials.clear()
                TaskNotifications.stop(context, clear = true)
            }
        }
    }
}

private class MultiComputerFixture : java.io.Closeable {
    val a = "a".repeat(32)
    val b = "b".repeat(32)
    val aStarted = CountDownLatch(1)
    val aFinished = CountDownLatch(1)
    val releaseA = CountDownLatch(1)
    val paths = CopyOnWriteArrayList<String>()
    private val server = ServerSocket(0)
    val port: Int get() = server.localPort
    private val worker = thread(isDaemon = true) {
        while (!server.isClosed) try {
            val socket = server.accept()
            thread(isDaemon = true) {
                socket.use {
                    try {
                        socket.soTimeout = 5000
                        val input = socket.getInputStream().bufferedReader()
                        val path = input.readLine().split(' ')[1].substringBefore('?')
                        paths.add(path)
                        while (!input.readLine().isNullOrEmpty()) { }
                        if (path == "/terminal-api/$a/api/projects") {
                            aStarted.countDown()
                            releaseA.await(20, TimeUnit.SECONDS)
                        }
                        val payload = response(path).toByteArray(Charsets.UTF_8)
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(payload); flush()
                        }
                        if (path == "/terminal-api/$a/api/projects") aFinished.countDown()
                    } catch (_: Exception) { aFinished.countDown() }
                }
            }
        } catch (_: Exception) { }
    }
    private fun response(path: String): String {
        val name = if (path.contains("/terminal-api/$b/")) "B" else "A"
        return when {
            path == "/control-api/terminals" -> """{"terminals":[{"id":"$a","name":"电脑 A","state":"active","online":true},{"id":"$b","name":"电脑 B","state":"active","online":true}]}"""
            path == "/control-api/pairings" -> """{"pairings":[]}"""
            path.endsWith("/api/projects") -> """{"projects":[{"id":"same-project","name":"$name 项目"}]}"""
            path.endsWith("/api/sessions") -> """{"sessions":[{"id":"00000000-0000-4000-8000-000000000001","project_id":"same-project","title":"$name 的对话","status":"COMPLETED","mode":"default","updated_at":1}]}"""
            path.endsWith("/api/activity") -> """{"activity":[]}"""
            else -> "{}"
        }
    }
    override fun close() { releaseA.countDown(); server.close(); worker.join(3000) }
}
