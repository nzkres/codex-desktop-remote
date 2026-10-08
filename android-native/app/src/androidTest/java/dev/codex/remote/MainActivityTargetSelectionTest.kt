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
import kotlin.concurrent.thread

/** Real MainActivity against a loopback fixture; an empty target must never use old task routes. */
@RunWith(AndroidJUnit4::class)
class MainActivityTargetSelectionTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun noTaskRequestsUntilExplicitComputerSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val credentials = Credentials(context)
        TargetSelectionGateway().use { server ->
            TaskNotifications.setEnabled(context, false)
            TaskNotifications.stop(context, clear = true)
            TaskNotifications.prefs(context).edit().putBoolean("introduced", true).commit()
            credentials.clear()
            credentials.url = "http://127.0.0.1:${server.port}"
            credentials.token = "a".repeat(64)
            credentials.account = "synthetic-selection"
            assertFalse(credentials.taskTarget().isSelected)
            try {
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                    compose.waitUntil(15_000) {
                        compose.onAllNodesWithText("请先选择电脑").fetchSemanticsNodes().isNotEmpty()
                    }
                    compose.onNodeWithContentDescription("动态页面").performClick()
                    compose.onNodeWithText("请先选择电脑").assertIsDisplayed()
                    compose.onNodeWithContentDescription("任务页面").performClick()
                    compose.onNodeWithTag("choose-task-target").assertIsDisplayed()
                    compose.onNodeWithTag("choose-task-target").performClick()
                    compose.waitUntil(15_000) {
                        server.paths.contains("/control-api/terminals") &&
                            compose.onAllNodesWithTag("terminal-open-${server.terminal}").fetchSemanticsNodes().isNotEmpty()
                    }
                    assertFalse("No selection must not poll old task routes",
                        server.paths.any { it == "/api/projects" || it == "/api/sessions" || it == "/api/activity" })
                    assertFalse("No selection must not poll any task route",
                        server.paths.any { it.startsWith("/terminal-api/") })

                    compose.onNodeWithTag("terminal-open-${server.terminal}").performScrollTo().performClick()
                    compose.waitUntil(15_000) {
                        server.paths.contains("/terminal-api/${server.terminal}/api/projects") &&
                            server.paths.contains("/terminal-api/${server.terminal}/api/sessions") &&
                            server.paths.contains("/terminal-api/${server.terminal}/api/activity")
                    }
                    assertEquals(server.terminal, credentials.taskTarget().id)
                    assertFalse(server.paths.any { it == "/api/projects" || it == "/api/sessions" || it == "/api/activity" })
                }
            } finally {
                credentials.clear()
                TaskNotifications.stop(context, clear = true)
            }
        }
    }

    @Test fun terminalDiscovery401ReturnsToLoginAndPreservesSelectionMetadata() = controlFailure(401)
    @Test fun terminalDiscovery403ShowsPermissionErrorWithoutLoggingOut() = controlFailure(403)

    private fun controlFailure(status: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val credentials = Credentials(context)
        TargetSelectionGateway(controlStatus = status).use { server ->
            TaskNotifications.setEnabled(context, false)
            TaskNotifications.stop(context, clear = true)
            TaskNotifications.prefs(context).edit().putBoolean("introduced", true).commit()
            credentials.clear()
            credentials.url = "http://127.0.0.1:${server.port}"
            credentials.token = "a".repeat(64)
            credentials.account = "synthetic-control-failure"
            credentials.selectTarget(TaskTarget(server.terminal, "合成电脑"))
            try {
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                    compose.onNodeWithContentDescription("远程终端页面").performClick()
                    compose.waitUntil(15_000) { server.paths.contains("/control-api/terminals") }
                    if (status == 401) {
                        compose.waitUntil(15_000) {
                            compose.onAllNodesWithText("登录统一账号，查看已连接的电脑").fetchSemanticsNodes().isNotEmpty()
                        }
                        assertEquals("", credentials.token)
                    } else {
                        compose.waitUntil(15_000) {
                            compose.onAllNodesWithText("终端管理权限不足", substring = true).fetchSemanticsNodes().isNotEmpty()
                        }
                        assertEquals("a".repeat(64), credentials.token)
                        compose.onNodeWithText("登录统一账号，查看已连接的电脑").assertDoesNotExist()
                    }
                    assertEquals("synthetic-control-failure", credentials.account)
                    assertEquals(server.terminal, credentials.taskTarget().id)
                    assertFalse(server.paths.any { it.startsWith("/api/") })
                }
            } finally {
                credentials.clear()
                TaskNotifications.stop(context, clear = true)
            }
        }
    }

    @Test fun selectedTask404DoesNotClearLogin() = selectedTaskFailure(404)
    @Test fun selectedTask401ReturnsToLogin() = selectedTaskFailure(401)

    private fun selectedTaskFailure(status: Int) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val credentials = Credentials(context)
        TargetSelectionGateway(taskStatus = status).use { server ->
            TaskNotifications.setEnabled(context, false)
            TaskNotifications.stop(context, clear = true)
            TaskNotifications.prefs(context).edit().putBoolean("introduced", true).commit()
            credentials.clear()
            credentials.url = "http://127.0.0.1:${server.port}"
            credentials.token = "b".repeat(64)
            credentials.account = "synthetic-selected-failure"
            credentials.selectTarget(TaskTarget(server.terminal, "合成电脑"))
            try {
                ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                    compose.waitUntil(15_000) {
                        server.paths.contains("/terminal-api/${server.terminal}/api/projects")
                    }
                    if (status == 401) {
                        compose.waitUntil(15_000) {
                            compose.onAllNodesWithText("登录统一账号，查看已连接的电脑").fetchSemanticsNodes().isNotEmpty()
                        }
                        assertEquals("", credentials.token)
                    } else {
                        compose.waitUntil(15_000) {
                            compose.onAllNodesWithText("这台电脑已移除或不属于当前账号", substring = true).fetchSemanticsNodes().isNotEmpty()
                        }
                        compose.onNodeWithContentDescription("任务页面").assertIsDisplayed()
                        compose.onNodeWithText("登录统一账号，查看已连接的电脑").assertDoesNotExist()
                        assertEquals("b".repeat(64), credentials.token)
                    }
                    assertEquals("synthetic-selected-failure", credentials.account)
                    assertEquals(server.terminal, credentials.taskTarget().id)
                    assertFalse(server.paths.any { it.startsWith("/api/") })
                }
            } finally {
                credentials.clear()
                TaskNotifications.stop(context, clear = true)
            }
        }
    }
}

private class TargetSelectionGateway(private val controlStatus: Int = 200, private val taskStatus: Int = 200) : java.io.Closeable {
    val terminal = "c".repeat(32)
    val paths = CopyOnWriteArrayList<String>()
    private val server = ServerSocket(0)
    val port: Int get() = server.localPort
    private val worker = thread(isDaemon = true, name = "synthetic-target-selection") {
        while (!server.isClosed) try {
            server.accept().use { socket ->
                socket.soTimeout = 5_000
                val input = socket.getInputStream().bufferedReader()
                val path = input.readLine().split(' ')[1].substringBefore('?')
                paths.add(path)
                while (!input.readLine().isNullOrEmpty()) { }
                val (status, json) = when (path) {
                    "/control-api/terminals" -> if (controlStatus == 200) 200 to
                        """{"terminals":[{"id":"$terminal","name":"合成电脑","state":"active","online":true}]}"""
                        else controlStatus to """{"error":"fixture_control_failure"}"""
                    "/control-api/pairings" -> 200 to """{"pairings":[]}"""
                    "/terminal-api/$terminal/api/projects" -> if (taskStatus == 200) 200 to
                        """{"projects":[{"id":"fixture","name":"合成项目"}]}"""
                        else taskStatus to """{"error":"${if (taskStatus == 404) "terminal_not_found" else "login_required"}"}"""
                    "/terminal-api/$terminal/api/sessions" -> 200 to """{"sessions":[]}"""
                    "/terminal-api/$terminal/api/activity" -> 200 to """{"activity":[]}"""
                    else -> 404 to """{"error":"fixture_unknown_path"}"""
                }
                val body = json.toByteArray(Charsets.UTF_8)
                socket.getOutputStream().apply {
                    write("HTTP/1.1 $status ${if (status == 200) "OK" else "Fixture Failure"}\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    write(body); flush()
                }
            }
        } catch (_: Exception) { /* Socket closure ends the fixture. */ }
    }
    override fun close() { server.close(); worker.join(3_000) }
}
