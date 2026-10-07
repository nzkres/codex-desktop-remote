package dev.codex.remote

import android.annotation.SuppressLint
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class NotificationDestination(val scope: String, val threadId: String, val terminalId: String = "", val terminalName: String = "原电脑 · 旧通道")

object TaskNotifications {
    const val CONNECTION_ID = 1
    const val CONNECTION_CHANNEL = "task_connection"
    const val DONE_CHANNEL = "task_completed"
    const val ATTENTION_CHANNEL = "task_attention"
    val connectionState = MutableStateFlow("后台通知未开启")
    private const val EXTRA_THREAD = "notification_thread"
    private const val EXTRA_SCOPE = "notification_scope"
    private const val EXTRA_TERMINAL = "notification_terminal"
    private const val EXTRA_TERMINAL_NAME = "notification_terminal_name"

    fun prefs(context: Context) = context.getSharedPreferences("task-notifications", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("enabled", enabled).apply()
    }
    fun scope(credentials: Credentials, target: TaskTarget = TaskTarget()): String = digest(target.endpoint(credentials.url) + "\n" + credentials.token)
    private fun digest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun allowed(context: Context): Boolean = (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(
        context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(listOf(
            NotificationChannel(CONNECTION_CHANNEL, "后台连接状态", NotificationManager.IMPORTANCE_LOW).apply {
                description = "跨设备对话同步的常驻状态，可随时暂停"; setShowBadge(false)
            },
            NotificationChannel(DONE_CHANNEL, "任务完成", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "新的任务回合完成时提醒"; lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
            NotificationChannel(ATTENTION_CHANNEL, "需要你处理", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "需要回答问题、选择选项、审批或决定是否实施计划"; lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            },
        ))
    }

    private fun openIntent(context: Context, scope: String = "", threadId: String = "", target: TaskTarget = TaskTarget()): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            // Only an opaque destination; never put a token, question or answer in a URI.
            data = Uri.Builder().scheme("codex-remote").authority("notification").appendPath(scope).appendPath(threadId).build()
            putExtra(EXTRA_SCOPE, scope); putExtra(EXTRA_THREAD, threadId)
            putExtra(EXTRA_TERMINAL, target.id); putExtra(EXTRA_TERMINAL_NAME, target.name)
        }
        return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun destination(intent: Intent?): NotificationDestination? {
        val scope = intent?.getStringExtra(EXTRA_SCOPE).orEmpty()
        val thread = intent?.getStringExtra(EXTRA_THREAD).orEmpty()
        val terminal = intent?.getStringExtra(EXTRA_TERMINAL).orEmpty()
        val name = intent?.getStringExtra(EXTRA_TERMINAL_NAME) ?: "原电脑 · 旧通道"
        return if (validTerminalId(terminal) && scope.matches(Regex("[a-f0-9]{64}")) && thread.matches(Regex("[a-f0-9-]{36}")))
            NotificationDestination(scope, thread, terminal, name.take(80)) else null
    }

    fun connection(context: Context, status: String): Notification {
        val pause = PendingIntent.getService(context, 1, Intent(context, TaskNotificationService::class.java)
            .setAction(TaskNotificationService.ACTION_PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(context, CONNECTION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle("Desktop Remote Lite · 后台通知")
            .setContentText(status).setContentIntent(openIntent(context)).setOngoing(true)
            .setOnlyAlertOnce(true).setShowWhen(false).setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "暂停通知", pause).build()
    }

    @SuppressLint("MissingPermission") // allowed() checks both runtime permission and notification settings.
    fun post(context: Context, scope: String, session: Session, signals: List<TaskSignal>, target: TaskTarget = TaskTarget()): Boolean {
        if (!allowed(context)) return false
        val manager = NotificationManagerCompat.from(context)
        // One notification per task/category, even when one request contains many questions.
        signals.groupBy { if (it.kind == "completed") DONE_CHANNEL else ATTENTION_CHANNEL }.forEach { (channel, events) ->
            val title = when {
                events.any { it.kind == "approval" } -> "任务需要你的批准"
                events.any { it.kind == "question" } -> "任务需要你的回答或选择"
                events.any { it.kind == "plan" } -> "规划完成，下一步由你决定"
                else -> "任务已完成"
            }
            val public = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Desktop Remote Lite").setContentText("有新的任务消息，解锁后查看").build()
            val notification = NotificationCompat.Builder(context, channel).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title).setContentText(session.title.take(100))
                .setSubText(target.name).setContentIntent(openIntent(context, scope, session.id, target))
                .setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setPublicVersion(public)
                .setCategory(if (channel == DONE_CHANNEL) NotificationCompat.CATEGORY_STATUS else NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(if (channel == DONE_CHANNEL) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
                .build()
            try { manager.notify(tag(scope, session.id, channel), 2, notification) }
            catch (_: SecurityException) { return false }
        }
        return true
    }

    private fun tag(scope: String, id: String, channel: String) = "$scope:$id:$channel"
    fun clearAttention(context: Context, scope: String, thread: String) {
        NotificationManagerCompat.from(context).cancel(tag(scope, thread, ATTENTION_CHANNEL), 2)
    }
    fun clearCompletion(context: Context, scope: String, thread: String) {
        NotificationManagerCompat.from(context).cancel(tag(scope, thread, DONE_CHANNEL), 2)
    }
    fun clearThread(context: Context, scope: String, thread: String) {
        listOf(DONE_CHANNEL, ATTENTION_CHANNEL).forEach { NotificationManagerCompat.from(context).cancel(tag(scope, thread, it), 2) }
    }
    fun clearScope(context: Context, scope: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag?.startsWith("$scope:") == true }.forEach { manager.cancel(it.tag, it.id) }
    }
    fun stop(context: Context, clear: Boolean = false) {
        context.stopService(Intent(context, TaskNotificationService::class.java))
        connectionState.value = "后台通知已暂停"
        if (clear) NotificationManagerCompat.from(context).cancelAll()
    }
    fun start(context: Context) {
        if (!enabled(context) || !allowed(context)) return
        try { ContextCompat.startForegroundService(context, Intent(context, TaskNotificationService::class.java)) }
        catch (_: Exception) { connectionState.value = "系统未允许启动，请回到 App 重试" }
    }
}

/** The ledger contains only opaque IDs/statuses, never conversation text or credentials. */
class SignalLedger(context: Context, scope: String) {
    private val prefs = context.getSharedPreferences("task-signal-ledger", Context.MODE_PRIVATE)
    private val key = scope
    fun read(): TaskSignalTracker {
        return try {
            val json = JSONObject(prefs.getString(key, "{}") ?: "{}")
            val saved = json.optJSONObject("checkpoints") ?: JSONObject()
            val checkpoints = linkedMapOf<String, SignalCheckpoint>()
            saved.keys().forEach { id ->
                val item = saved.getJSONObject(id)
                val keys = item.optJSONArray("delivered") ?: JSONArray()
                checkpoints[id] = SignalCheckpoint(item.getString("fingerprint"), (0 until keys.length()).map { keys.getString(it) }.toSet())
            }
            TaskSignalTracker(json.optBoolean("initialized"), json.optLong("startedAt", System.currentTimeMillis()), checkpoints)
        } catch (_: Exception) { TaskSignalTracker(startedAt = System.currentTimeMillis()) }
    }
    fun save(tracker: TaskSignalTracker) {
        val saved = JSONObject()
        tracker.checkpoints.forEach { (id, checkpoint) ->
            saved.put(id, JSONObject().put("fingerprint", checkpoint.fingerprint).put("delivered", JSONArray(checkpoint.delivered.toList())))
        }
        prefs.edit().putString(key, JSONObject().put("initialized", tracker.initialized).put("startedAt", tracker.startedAt)
            .put("checkpoints", saved).toString()).apply()
    }
    fun clearAll() { prefs.edit().clear().apply() }
}
