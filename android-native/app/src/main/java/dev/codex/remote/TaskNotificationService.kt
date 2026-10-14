package dev.codex.remote

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.*

/** User-started, visible cross-device conversation continuity. No boot receiver or wake lock. */
class TaskNotificationService : Service() {
    companion object { const val ACTION_PAUSE = "dev.codex.remote.PAUSE_NOTIFICATIONS" }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var poll: Job? = null
    private var currentScope: String? = null
    private var lastStatus = ""
    private val credentials by lazy { Credentials(this) }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) {
            TaskNotifications.setEnabled(this, false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!TaskNotifications.enabled(this) || !TaskNotifications.allowed(this)) {
            stopSelf(); return START_NOT_STICKY
        }
        TaskNotifications.createChannels(this)
        ServiceCompat.startForeground(this, TaskNotifications.CONNECTION_ID, TaskNotifications.connection(this, lastStatus.ifBlank { "正在连接你的电脑…" }),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING else 0)
        if (credentials.url.isBlank() || credentials.token.isBlank()) {
            stopSelf(); return START_NOT_STICKY
        }
        val identity = TaskNotifications.scope(credentials)
        if (poll?.isActive == true && identity == currentScope) return START_STICKY
        poll?.cancel()
        currentScope = identity
        // Remove only obsolete legacy-route notifications; keep all device scopes and ledgers.
        TaskNotifications.clearScope(this, TaskNotifications.scope(credentials, TaskTarget()))
        val client = Gateway(credentials.url, credentials.token)
        poll = serviceScope.launch { monitorHosts(client, identity) }
        return START_STICKY
    }

    private fun stillCurrent(identity: String): Boolean {
        return TaskNotifications.enabled(this) && credentials.token.isNotBlank() && TaskNotifications.scope(credentials) == identity
    }

    /** Each host has its own cursor/ledger/backoff. One offline host cannot starve the others. */
    private suspend fun monitorHosts(root: Gateway, accountIdentity: String) = supervisorScope {
        val jobs = linkedMapOf<String, Job>()
        val targets = linkedMapOf<String, TaskTarget>()
        val states = linkedMapOf<String, String>()
        var discoveryFailed = false
        fun report() {
            val connected = states.values.count { it == "connected" }
            updateStatus(if (jobs.isEmpty() && !discoveryFailed) "暂无已接入电脑 · 请在远程终端添加"
                else if (jobs.size == 1 && !discoveryFailed && connected == 1) "已连接 · 等待任务消息"
                else "$connected/${jobs.size} 台电脑通知已连接" + if (discoveryFailed) " · 终端列表暂不可用" else " · 离线电脑独立重试")
        }
        fun add(target: TaskTarget) {
            if (!target.isSelected) return
            if (target.id in jobs) return
            targets[target.id] = target
            val identity = TaskNotifications.scope(credentials, target)
            states[target.id] = "connecting"
            jobs[target.id] = launch {
                monitor(root.forTarget(target), identity, accountIdentity) { state -> states[target.id] = state; report() }
            }
        }
        try {
            while (isActive && stillCurrent(accountIdentity)) {
                try {
                    val available = root.terminalControl().snapshot().terminals.filter { it.state == "active" }
                    ensureActive()
                    if (!stillCurrent(accountIdentity)) break
                    val ids = available.map { it.id }.toSet()
                    for (id in jobs.keys.toList().filter { it !in ids }) {
                        jobs.remove(id)?.cancelAndJoin()
                        targets.remove(id)?.let { TaskNotifications.clearScope(this@TaskNotificationService, TaskNotifications.scope(credentials, it)) }
                        states.remove(id)
                    }
                    available.forEach { add(TaskTarget(it.id, it.name)) }
                    discoveryFailed = false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (failure: Exception) {
                    if (failure is GatewayException && failure.status == 401 && stillCurrent(accountIdentity)) {
                        updateStatus("登录已过期，请打开 App 重新登录")
                        stopSelf()
                        break
                    }
                    discoveryFailed = true // Retain known hosts; their own requests remain authenticated.
                }
                report()
                delay(30_000)
            }
        } finally {
            jobs.values.forEach { it.cancel() }
            jobs.values.joinAll()
        }
    }

    private suspend fun monitor(client: Gateway, identity: String, accountIdentity: String, status: (String) -> Unit) {
        val ledger = SignalLedger(this, identity)
        val tracker = ledger.read()
        var failures = 0
        var roundRobin = 0
        while (currentCoroutineContext().isActive && stillCurrent(accountIdentity)) {
            try {
                if (!TaskNotifications.allowed(this)) { stopSelf(); break }
                client.sessions() // Imports new desktop threads before polling observations.
                val entries = client.activity()
                val activities = entries.map { entry ->
                    val observation = entry.observation
                    SignalActivity(entry.session.id, "${observation.state}:${observation.lastActivityAt}",
                        observation.state in setOf("running", "quiet") || entry.session.status in setOf("WAITING_USER", "WAITING_APPROVAL", "RUNNING"),
                        observation.lastActivityAt)
                }
                val candidates = tracker.candidates(activities)
                // Rotate the bounded detail batch so a busy task cannot starve others.
                val offset = if (candidates.isEmpty()) 0 else roundRobin % candidates.size
                val batch = (candidates.drop(offset) + candidates.take(offset)).take(8)
                roundRobin += batch.size
                var missed = 0
                for (candidate in batch) {
                    try {
                        val snapshot = client.signals(candidate.id)
                        currentCoroutineContext().ensureActive()
                        if (!stillCurrent(accountIdentity)) return
                        val session = entries.first { it.session.id == candidate.id }.session
                        val signals = tracker.preview(candidate, snapshot)
                        // A new running turn supersedes the previous completion. Keeping both
                        // would misrepresent the task and needlessly trigger system auto-grouping.
                        if (!snapshot.completed) TaskNotifications.clearCompletion(this@TaskNotificationService, identity, candidate.id)
                        if (snapshot.questions.isEmpty() && snapshot.approvals.isEmpty() && !(snapshot.plan && snapshot.completed)) {
                            TaskNotifications.clearAttention(this@TaskNotificationService, identity, candidate.id)
                        }
                        if (signals.isEmpty() || TaskNotifications.post(this@TaskNotificationService, identity, session, signals, client.target)) {
                            tracker.acknowledge(candidate, signals)
                            ledger.save(tracker)
                        }
                    } catch (exception: CancellationException) { throw exception }
                    catch (exception: GatewayException) { if (exception.status == 401) throw exception else missed++ }
                    catch (_: Exception) { missed++ }
                }
                ledger.save(tracker)
                failures = 0
                status(if (missed == 0) "connected" else "retrying")
            } catch (exception: CancellationException) { throw exception }
            catch (exception: GatewayException) {
                if (exception.status == 401) {
                    updateStatus("登录已过期，请打开 App 重新登录")
                    stopSelf()
                    break
                }
                failures++
                status("retrying")
            } catch (_: Exception) {
                failures++
                status("retrying")
            }
            delay(if (failures == 0) 10_000L else (10_000L * (1L shl failures.coerceAtMost(4))).coerceAtMost(120_000L))
        }
    }

    @SuppressLint("MissingPermission") // Runtime permission is checked immediately before notify().
    private fun updateStatus(status: String) {
        TaskNotifications.connectionState.value = status
        if (lastStatus != status && TaskNotifications.allowed(this)) {
            try { NotificationManagerCompat.from(this).notify(TaskNotifications.CONNECTION_ID, TaskNotifications.connection(this, status)) }
            catch (_: SecurityException) { /* Permission can change while a request is in flight. */ }
        }
        lastStatus = status
    }

    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }
    override fun onDestroy() {
        serviceScope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        if (!TaskNotifications.enabled(this)) TaskNotifications.connectionState.value = "后台通知已暂停"
        super.onDestroy()
    }
}
