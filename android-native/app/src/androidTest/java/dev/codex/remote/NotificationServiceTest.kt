package dev.codex.remote

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.filters.SdkSuppress
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/** Real Activity + FGS + HTTP + system notifications against a loopback fake, never the user's relay. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 33)
class NotificationServiceTest {
    @get:Rule val permission = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @Test fun backgroundCompletionQuestionAndPause() = verifyNotifications(false)
    @Test fun offlineOriginalComputerDoesNotBlockRemoteNotificationsOrTheirDestination() = verifyNotifications(true)

    private fun verifyNotifications(remoteOnly: Boolean) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FakeTaskGateway(remoteOnly).use { server ->
            val credentials = Credentials(context)
            credentials.url = "http://127.0.0.1:${server.port}"
            credentials.token = "b".repeat(64) // Synthetic local test login only.
            val target = if (remoteOnly) TaskTarget(server.terminal, "合成第二台电脑") else TaskTarget()
            SignalLedger(context, "").clearAll()
            TaskNotifications.prefs(context).edit().putBoolean("introduced", true).commit()
            TaskNotifications.setEnabled(context, true)
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.cancelAll()
            // ActivityScenario stops tracking lifecycle events when onNewIntent changes the
            // Activity's routing URI. Keep its launch filter equal to the future notification,
            // but omit destination extras: the conversation must open only after the real tap.
            val bootstrap = Intent(context, MainActivity::class.java).apply {
                data = Uri.parse("codex-remote://notification/${TaskNotifications.scope(credentials, target)}/${server.id}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ActivityScenario.launch<MainActivity>(bootstrap).use { activity ->
                try {
                    await { server.detailReads.get() > 0 && manager.activeNotifications.any { it.id == TaskNotifications.CONNECTION_ID } }
                    // Stop the Activity but leave the process/foreground service running.
                    activity.moveToState(Lifecycle.State.CREATED)
                    server.stage.set(1)
                    await { manager.activeNotifications.any { it.notification.channelId == TaskNotifications.DONE_CHANNEL } }
                    val completion = manager.activeNotifications.single { it.notification.channelId == TaskNotifications.DONE_CHANNEL }
                    assertNotNull(completion.notification.contentIntent)
                    assertTrue(completion.tag.startsWith(TaskNotifications.scope(credentials, target) + ":"))
                    server.stage.set(2)
                    await { manager.activeNotifications.any { it.notification.channelId == TaskNotifications.ATTENTION_CHANNEL } }
                    await { manager.activeNotifications.none { it.key == completion.key } }
                    val attention = manager.activeNotifications.single { it.notification.channelId == TaskNotifications.ATTENTION_CHANNEL }
                    assertEquals("任务需要你的回答或选择", attention.notification.extras.getString(android.app.Notification.EXTRA_TITLE))
                    val postedAt = attention.postTime
                    val reads = server.detailReads.get()
                    await { server.detailReads.get() > reads }
                    assertEquals(postedAt, manager.activeNotifications.single { it.notification.channelId == TaskNotifications.ATTENTION_CHANNEL }.postTime)
                    // The pending intent resumes the same app and carries the exact read-only destination.
                    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                    device.openNotification()
                    device.waitForIdle()
                    captureTestScreen("05-question-notification")
                    fun taskRow(): UiObject2 {
                        var node = requireNotNull(device.wait(Until.findObject(By.text("任务需要你的回答或选择")), 10_000)) {
                            "Task notification should be visible in the system shade"
                        }
                        while (node.resourceName != "com.android.systemui:id/expandableNotificationRow") {
                            node = requireNotNull(node.parent)
                        }
                        return node
                    }
                    val notification = taskRow()
                    if (!notification.isClickable) {
                        // Android 15 can show this task as a non-clickable preview inside an
                        // automatic group. Tapping the summary opens the launcher, not this task.
                        var group = requireNotNull(notification.parent)
                        while (group.resourceName != "com.android.systemui:id/expandableNotificationRow") {
                            group = requireNotNull(group.parent)
                        }
                        requireNotNull(group.findObject(By.res("android", "expand_button"))).click()
                        device.waitForIdle()
                    }
                    // UiObject resolves the selector again for every operation. UiObject2 handles
                    // from the collapsed preview become stale when SystemUI reinflates the group.
                    val taskBody = device.findObject(UiSelector().resourceId("android:id/text").text("后台通知测试"))
                    assertTrue("The expanded task child must be visible", taskBody.waitForExists(10_000))
                    taskBody.click()
                    // ActivityScenario.getState() throws during its transient STARTED stage.
                    // Wait for the actual destination UI, then inspect the resumed Activity.
                    assertTrue(device.wait(Until.hasObject(By.text("要继续吗？")), 20_000))
                    activity.onActivity {
                        assertEquals(server.id, TaskNotifications.destination(it.intent)?.threadId)
                        assertEquals(target.id, TaskNotifications.destination(it.intent)?.terminalId)
                    }
                    // Match the real alert, not Android's optional automatic group summary.
                    await { manager.activeNotifications.none { it.key == attention.key } }
                    captureTestScreen("06-notification-opened-question")
                    val connection = manager.activeNotifications.single { it.id == TaskNotifications.CONNECTION_ID }
                    connection.notification.actions.single().actionIntent.send()
                    await { !TaskNotifications.enabled(context) && manager.activeNotifications.none { it.id == TaskNotifications.CONNECTION_ID } }
                } catch (failure: Throwable) {
                    captureTestScreen("07-notification-failure")
                    throw failure
                } finally {
                    TaskNotifications.setEnabled(context, false)
                    TaskNotifications.stop(context, clear = true)
                    credentials.clear()
                    SignalLedger(context, "").clearAll()
                }
            }
        }
    }

    private fun await(check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 45_000
        while (System.currentTimeMillis() < deadline) {
            if (check()) return
            Thread.sleep(200)
        }
        fail("Timed out waiting for background notification integration")
    }
}

private class FakeTaskGateway(private val remoteOnly: Boolean = false) : java.io.Closeable {
    val terminal = "d".repeat(32)
    val id = "00000000-0000-4000-8000-000000000002"
    val stage = AtomicInteger(0)
    val detailReads = AtomicInteger(0)
    private val observedStart = System.currentTimeMillis()
    private val running = AtomicBoolean(true)
    private val socket = ServerSocket(0)
    val port: Int get() = socket.localPort
    private val worker = thread(name = "synthetic-task-gateway", isDaemon = true) {
        while (running.get()) {
            try {
                socket.accept().use { client ->
                    client.soTimeout = 5_000
                    val input = client.getInputStream().bufferedReader()
                    val path = input.readLine()?.split(' ')?.getOrNull(1).orEmpty()
                    while (!input.readLine().isNullOrBlank()) { }
                    val offline = remoteOnly && path.startsWith("/api/")
                    val body = (if (offline) """{"error":"terminal_offline"}""" else response(path)).toByteArray(Charsets.UTF_8)
                    client.getOutputStream().apply {
                        write("HTTP/1.1 ${if (offline) "502 Unavailable" else "200 OK"}\r\nContent-Type: application/json\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(body); flush()
                    }
                }
            } catch (_: Exception) { /* Socket closure ends the test server. */ }
        }
    }
    private fun response(path: String): String {
        val n = stage.get()
        val completed = n == 1
        val now = System.currentTimeMillis()
        val status = if (completed) "COMPLETED" else if (n == 2) "WAITING_USER" else "RUNNING"
        val session = """{"id":"$id","project_id":"fixture","title":"后台通知测试","status":"$status","active_turn_id":${if (completed) "null" else "\"turn-$n\""},"mode":"default","updated_at":$now}"""
        val observation = """{"state":"${if (completed) "completed" else "running"}","lastActivityAt":${observedStart + n},"source":"gateway","lastItemType":"agentMessage"}"""
        return when (path.removePrefix("/terminal-api/$terminal").substringBefore('?')) {
            "/control-api/terminals" -> if (remoteOnly) """{"terminals":[{"id":"$terminal","name":"合成第二台电脑","state":"active","online":true}]}""" else """{"terminals":[]}"""
            "/control-api/pairings" -> """{"pairings":[]}"""
            "/api/projects" -> """{"projects":[{"id":"fixture","name":"测试工作区"}]}"""
            "/api/sessions" -> """{"sessions":[$session]}"""
            "/api/activity" -> """{"activity":[{"session":$session,"observation":$observation}]}"""
            "/api/sessions/$id/signals" -> {
                detailReads.incrementAndGet()
                """{"turnId":"turn-$n","completed":$completed,"plan":false,"questions":${if (n == 2) "[\"q1\"]" else "[]"},"approvals":[]}"""
            }
            "/api/sessions/$id/mobile-sync" -> {
                val questions = if (n == 2) """[{"id":"q1","questions":[{"id":"answer","header":"下一步","question":"要继续吗？","options":[],"isOther":true}]}]""" else "[]"
                """{"protocol":1,"cursor":"${"a".repeat(64)}","reset":true,"hasOlder":false,"order":[],"items":[],"session":$session,"observation":$observation,"pendingQuestions":$questions,"pendingApprovals":[]}"""
            }
            else -> "{}"
        }
    }
    override fun close() { running.set(false); socket.close(); worker.join(5_000) }
}
