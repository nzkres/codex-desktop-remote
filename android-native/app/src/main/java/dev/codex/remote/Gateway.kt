package dev.codex.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class Project(val id: String, val name: String)
data class Session(
    val id: String,
    val projectId: String,
    val title: String,
    val status: String,
    val activeTurnId: String?,
    val mode: String,
    val updatedAt: Long,
)
data class Change(val path: String, val diff: String)
data class MessageItem(
    val id: String,
    val type: String,
    val text: String,
    val command: String,
    val output: String,
    val cwd: String,
    val status: String,
    val exitCode: Int?,
    val changes: List<Change>,
    val truncated: Boolean = false,
    val fullBytes: Long = 0,
    val timestampMs: Long? = null,
    val timestampKind: String = "recorded",
    val imageCount: Int = 0,
    val roundNumber: Int = 0,
    val turnStartedAtMs: Long? = null,
    val turnEndedAtMs: Long? = null,
    val turnDurationMs: Long? = null,
    val turnCompleted: Boolean = false,
    val turnLastItem: Boolean = false,
    val contentRevision: String = "",
)
data class Turn(val id: String, val status: String, val items: List<MessageItem>)
data class Observation(val state: String, val lastActivityAt: Long, val source: String, val lastItemType: String)
data class ActivityEntry(val session: Session, val observation: Observation)
data class QuestionOption(val label: String, val description: String)
data class Question(val id: String, val title: String, val text: String, val options: List<QuestionOption>, val secret: Boolean, val allowOther: Boolean)
data class QuestionRequest(val id: String, val questions: List<Question>, val revision: String = "", val turnId: String = "",
    val kind: String = "user-input", val status: String = "pending", val notice: String = "")
data class QuestionReceipt(val status: String, val notice: String)
data class ConversationCapabilities(val questions: Boolean = false, val approvals: Boolean = false, val mode: Boolean = false,
    val implementPlan: Boolean = false, val create: Boolean = false)
data class Approval(val id: String, val turnId: String, val kind: String, val details: String, val approvable: Boolean)
data class Conversation(
    val turns: List<Turn>,
    val observation: Observation,
    val questions: List<QuestionRequest>,
    val approvals: List<Approval>,
    val hasOlder: Boolean = false,
    val session: Session? = null,
    val latestHasPlan: Boolean = false,
    val historyReset: Boolean = false,
    val latestUserMessage: SyncEntry? = null,
    val taskReferenceSupported: Boolean = false,
    val userMessageCount: Int? = null,
    val controlNotice: String? = null,
    val capabilities: ConversationCapabilities = ConversationCapabilities(),
    val usage: UsageSnapshot? = null,
)
data class TaskHistoryPage(val items: List<MessageItem>, val nextBefore: String?, val total: Int)

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
private fun JSONObject.array(key: String): List<JSONObject> = optJSONArray(key)?.objects() ?: emptyList()
private fun JSONObject.string(key: String): String = if (isNull(key)) "" else optString(key, "")
private fun JSONObject.optionalLong(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key).takeIf { it >= 0 }
private fun JSONObject.observation(): Observation = Observation(string("state"), optLong("lastActivityAt"), string("source"), string("lastItemType"))
private fun JSONObject.session(): Session = Session(
    string("id"), string("project_id"), string("title"), string("status"),
    optString("active_turn_id").takeUnless { it.isBlank() || it == "null" }, string("mode"), optLong("updated_at"),
)
private fun JSONObject.syncEntry(): SyncEntry {
    val item = getJSONObject("item")
    return SyncEntry(string("key"), string("revision"), string("turnId"), string("turnStatus"),
        MessageItem(item.string("id"), item.string("type"), item.string("text"), item.string("command"),
            item.string("aggregatedOutput"), item.string("cwd"), item.string("status"),
            if (item.isNull("exitCode")) null else item.optInt("exitCode"),
            item.array("changes").map { Change(it.string("path"), it.string("diff")) }, item.optBoolean("truncated"), item.optLong("fullBytes"),
            item.optionalLong("timestampMs"), item.string("timestampKind"), item.optInt("imageCount"), item.optInt("roundNumber"),
            item.optionalLong("turnStartedAtMs"), item.optionalLong("turnEndedAtMs"), item.optionalLong("turnDurationMs"),
            item.optBoolean("turnCompleted"), item.optBoolean("turnLastItem"), item.string("contentRevision")))
}

class GatewayException(message: String, val status: Int, val code: String? = null) : Exception(message)

/** These codes are emitted before the native answer is forwarded. All other failures are uncertain. */
internal fun questionAnswerRejectedBeforeSend(error: GatewayException): Boolean =
    when (error.code) {
        "invalid_answer" -> error.status == 400
        "question_changed", "operation_busy", "permissions_changed" -> error.status == 409
        "question_unsupported" -> error.status == 409 || error.status == 501
        "outside_scope" -> error.status == 403
        "update_quarantine", "disconnect_preparing", "runtime_status_unknown" -> error.status == 503
        else -> false
    }

class Credentials(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        "gateway-credentials",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    init {
        // Before a re-login can replace the principal, bind pre-multicomputer
        // unacknowledged sends to the account that was present during upgrade.
        // Their keys are one-way hashes, so migrate each receipt lazily by thread.
        if (!prefs.contains("pending-send-legacy-owner")) {
            val endpoint = prefs.getString("url", "").orEmpty()
            val principal = prefs.getString("account", "").orEmpty()
            val login = prefs.getString("token", "").orEmpty()
            if (endpoint.isNotBlank() && login.isNotBlank()) {
                check(prefs.edit().putString("pending-send-legacy-owner", draftScope(endpoint, principal, login)).commit()) {
                    "无法保存旧版发送回执的所属账号，请重试。"
                }
            }
        }
    }
    var url: String
        get() = prefs.getString("url", "") ?: ""
        set(value) { prefs.edit().putString("url", value).apply() }
    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(value) { prefs.edit().putString("token", value).apply() }
    var account: String
        get() = prefs.getString("account", "") ?: ""
        set(value) { prefs.edit().putString("account", value).apply() }
    private fun targetKey(): String = "target-" + draftScope(url, account, token)
    fun taskTarget(): TaskTarget {
        val saved = prefs.getString(targetKey(), null) ?: return TaskTarget()
        val value = JSONObject(saved)
        return TaskTarget(value.getString("id"), value.getString("name"))
    }
    fun selectTarget(target: TaskTarget) {
        check(prefs.edit().putString(targetKey(), JSONObject().put("id", target.id).put("name", target.name).toString()).commit()) {
            "无法保存电脑选择，原连接已保留。"
        }
    }
    private fun messageKey(endpoint: String, id: String): String = "pending-send-" + MessageDigest.getInstance("SHA-256")
        .digest("$endpoint\n$id".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun pendingMessageId(endpoint: String, id: String, text: String): String {
        val key = messageKey(endpoint, id)
        if (!prefs.contains(key) && endpoint == prefs.getString("pending-send-legacy-owner", null)) {
            val legacyKey = messageKey(url, id)
            prefs.getString(legacyKey, null)?.let { receipt ->
                check(prefs.edit().putString(key, receipt).remove(legacyKey).commit()) {
                    "无法保留旧版发送编号，消息未发送"
                }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val saved = prefs.getString(key, null)?.split(":", limit = 2)
        if (saved?.size == 2 && saved[0] == digest) return saved[1]
        val messageId = java.util.UUID.randomUUID().toString()
        check(prefs.edit().putString(key, "$digest:$messageId").commit()) { "无法保存发送编号，消息未发送" }
        return messageId
    }
    fun acknowledgeMessage(endpoint: String, id: String) { prefs.edit().remove(messageKey(endpoint, id)).commit() }
    fun hasUnconfirmedMessages(): Boolean = prefs.all.keys.any {
        it.startsWith("pending-send-") && it != "pending-send-legacy-owner"
    }
    fun clear() { prefs.edit().clear().apply() }
}

class Gateway(private val baseUrl: String, private val token: String, val target: TaskTarget = TaskTarget()) {
    internal fun previewFeed(context: Context) = PreviewFeedClient(context, baseUrl, token)
    val taskEndpoint: String get() = target.endpoint(baseUrl)
    fun forTarget(target: TaskTarget): Gateway = Gateway(baseUrl, token, target)
    private val conversationLock = Mutex()
    private val windows = linkedMapOf<String, ConversationWindow>()
    private val summaries = java.util.concurrent.ConcurrentHashMap<String, Pair<String, JSONObject>>()
    companion object {
        suspend fun login(baseUrl: String, username: String, password: String): String = withContext(Dispatchers.IO) {
            val url = URL(baseUrl.trimEnd('/') + "/auth/login")
            require(url.protocol == "https" || (BuildConfig.DEBUG && url.protocol == "http")) {
                "正式版只允许 HTTPS；本地 HTTP 仅限调试版。"
            }
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            try {
                val body = JSONObject().put("username", username).put("password", password)
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val payload = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val result = if (payload.isBlank()) JSONObject() else JSONObject(payload)
                if (status !in 200..299) throw GatewayException(result.optString("error", "登录失败：HTTP $status"), status)
                result.string("token").also {
                    if (!it.matches(Regex("[a-f0-9]{64}"))) throw GatewayException("登录响应无效", 0)
                }
            } finally {
                connection.disconnect()
            }
        }
    }

    init {
        val url = URL(baseUrl)
        require(url.protocol == "https" || (BuildConfig.DEBUG && url.protocol == "http")) {
            "正式版只允许 HTTPS；本地 HTTP 仅限调试版。"
        }
        require(url.host.isNotBlank() && token.isNotBlank()) { "请输入中继地址并登录。" }
    }

    private suspend fun request(path: String, body: JSONObject? = null): JSONObject = withContext(Dispatchers.IO) {
        val cacheable = body == null && path in setOf("/api/projects", "/api/sessions", "/api/activity")
        val cached = if (cacheable) summaries[path] else null
        val connection = (URL(baseUrl.trimEnd('/') + target.route(path)).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            requestMethod = if (body == null) "GET" else "POST"
            connectTimeout = 10_000
            readTimeout = 25_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            if (cached != null) setRequestProperty("If-None-Match", cached.first)
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        try {
            if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NOT_MODIFIED && cached != null) return@withContext cached.second
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val payload = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            val json = if (payload.isBlank()) JSONObject() else try { JSONObject(payload) }
                catch (_: Exception) { throw GatewayException("服务响应异常（HTTP $status），请核对当前电脑连接。", status) }
            if (status !in 200..299) throw GatewayException(json.optString("error", "HTTP $status"), status,
                gatewayErrorCode(json))
            if (cacheable) connection.getHeaderField("ETag")?.let { summaries[path] = it to json }
            json
        } finally {
            connection.disconnect()
        }
    }

    suspend fun projects(): List<Project> = request("/api/projects").array("projects").map {
        Project(it.string("id"), it.string("name"))
    }

    fun terminalControl(): TerminalControl = TerminalControlClient(baseUrl, token)

    suspend fun logout() { request("/auth/logout", JSONObject()) }

    suspend fun sessions(): List<Session> = request("/api/sessions").array("sessions").map { it.session() }
    suspend fun activity(): List<ActivityEntry> = request("/api/activity").array("activity").mapNotNull {
        val session = it.optJSONObject("session") ?: return@mapNotNull null
        val observation = it.optJSONObject("observation") ?: return@mapNotNull null
        ActivityEntry(session.session(), observation.observation())
    }
    internal suspend fun updateStatus(): ConnectorUpdateProof = parseConnectorUpdateProof(request("/api/connector/update-status"))
    suspend fun conversation(id: String, older: Boolean = false, openDesktop: Boolean = false): Conversation = conversationLock.withLock {
        require(!older || !openDesktop) { "加载更早历史不能打开 Desktop 对话" }
        val window = windows.remove(id) ?: ConversationWindow()
        windows[id] = window
        while (windows.size > 2) windows.remove(windows.keys.first())
        val params = buildList {
            if (window.cursor.isNotBlank()) add("cursor=${window.cursor.encode()}")
            if (older) add("older=1")
            if (openDesktop) add("open=1")
        }.joinToString("&")
        val result = try { request("/api/sessions/${id.encode()}/mobile-sync?$params") }
        catch (exception: GatewayException) {
            if (exception.status == 404) throw GatewayException("电脑 Gateway 需要升级，暂不回退到耗流量的完整历史同步。", 426)
            throw exception
        }
        require(result.optInt("protocol") == 1 && result.string("cursor").isNotBlank()) { "同步响应无效，请稍后重试" }
        if (result.optBoolean("unchanged")) return@withLock window.current ?: error("缺少本地同步记录，请重新打开对话")
        val questions = result.array("pendingQuestions").map { request ->
            QuestionRequest(request.string("id"), request.array("questions").map { question ->
                Question(question.string("id"), question.string("header"), question.string("question"),
                    question.array("options").map { QuestionOption(it.string("label"), it.string("description")) }, question.optBoolean("isSecret"), question.optBoolean("isOther"))
            }, request.string("revision"), request.string("turnId"), request.string("kind"), request.string("status"), request.string("notice"))
        }
        val approvals = result.array("pendingApprovals").map {
            Approval(it.string("id"), it.string("turnId"), it.string("kind"), it.optJSONObject("details")?.toString(2) ?: "", it.optBoolean("approvable"))
        }
        val metadata = Conversation(emptyList(), result.optJSONObject("observation")?.observation() ?: Observation("idle", 0, "none", ""), questions, approvals,
            result.optBoolean("hasOlder"), result.optJSONObject("session")?.session(),
            result.optJSONObject("latestTurn")?.optBoolean("hasPlan") == true,
            result.optBoolean("reset") && window.cursor.isNotBlank(),
            result.optJSONObject("latestUserMessage")?.syncEntry(), result.has("latestUserMessage"),
            if (result.has("userMessageCount")) result.optInt("userMessageCount") else null,
            result.string("controlNotice").takeIf { it.isNotBlank() },
            result.optJSONObject("capabilities")?.let { caps -> ConversationCapabilities(caps.optBoolean("questions"),
                caps.optBoolean("approvals"), caps.optBoolean("mode"), caps.optBoolean("implementPlan"), caps.optBoolean("create")) }
                ?: ConversationCapabilities(),
            parseUsageSnapshot(result.optJSONObject("usage")))
        val order = result.optJSONArray("order") ?: error("缺少消息顺序，请稍后重试")
        window.applyPage(result.string("cursor"), result.optBoolean("reset"),
            (0 until order.length()).map { order.getString(it) }, result.array("items").map { it.syncEntry() }, metadata)
    }

    suspend fun fullItem(id: String, key: String): Conversation = conversationLock.withLock {
        val window = windows[id] ?: error("请先重新打开对话")
        val entry = request("/api/sessions/${id.encode()}/mobile-items/${key.encode()}").syncEntry()
        require(entry.key == key) { "消息响应不匹配" }
        window.expand(entry)
    }

    // Reading a task outside the timeline window must not insert/reorder history.
    suspend fun taskItem(id: String, key: String): MessageItem {
        val entry = request("/api/sessions/${id.encode()}/mobile-items/${key.encode()}").syncEntry()
        require(entry.key == key && entry.item.type == "userMessage") { "任务已变化，请刷新后再试" }
        return entry.item
    }

    suspend fun taskHistory(id: String, before: String?): TaskHistoryPage {
        val result = request("/api/sessions/${id.encode()}/mobile-tasks" + (before?.let { "?before=${it.encode()}" } ?: ""))
        return TaskHistoryPage(result.array("items").map { it.syncEntry().item }, result.string("nextBefore").takeIf { it.isNotBlank() }, result.optInt("total"))
    }

    suspend fun signals(id: String): SignalSnapshot {
        val result = request("/api/sessions/${id.encode()}/signals")
        fun ids(key: String): Set<String> {
            val values = result.optJSONArray(key) ?: error("任务通知接口需要升级")
            return (0 until values.length()).map { values.getString(it) }.toSet()
        }
        return SignalSnapshot(result.string("turnId").ifBlank { null }, result.optBoolean("completed"), result.optBoolean("plan"), ids("questions"), ids("approvals"))
    }

    suspend fun create(projectId: String, title: String, mode: String): Session {
        val json = request("/api/projects/${projectId.encode()}/sessions", JSONObject().put("title", title).put("mode", mode))
        val item = json.getJSONObject("session")
        return Session(item.string("id"), item.string("project_id"), item.string("title"), item.string("status"), null, item.string("mode"), item.optLong("updated_at"))
    }
    suspend fun send(id: String, text: String, clientMessageId: String): String? {
        return request("/api/sessions/${id.encode()}/messages", JSONObject().put("text", text).put("clientMessageId", clientMessageId))
            .optJSONObject("result")?.string("turnId")?.takeIf { it.isNotBlank() }
    }
    suspend fun stop(id: String, expectedTurnId: String) {
        request("/api/sessions/${id.encode()}/stop", JSONObject().put("expectedTurnId", expectedTurnId))
    }
    suspend fun mode(id: String, value: String) { request("/api/sessions/${id.encode()}/mode", JSONObject().put("mode", value)) }
    suspend fun implementPlan(id: String) { request("/api/sessions/${id.encode()}/implement-plan", JSONObject()) }
    suspend fun decide(id: String, approvalId: String, approve: Boolean) {
        request("/api/sessions/${id.encode()}/approvals/${approvalId.encode()}/decision", JSONObject().put("decision", if (approve) "approve" else "deny"))
    }
    suspend fun answer(id: String, question: QuestionRequest, answers: Map<String, String>): QuestionReceipt {
        require(question.id.matches(Regex("[a-f0-9]{64}")) && question.revision.matches(Regex("[a-f0-9]{64}"))) { "问题编号或版本无效，请刷新后再试" }
        val response = request("/api/sessions/${id.encode()}/questions/${question.id.encode()}/answer",
            JSONObject().put("revision", question.revision).put("answers", JSONObject(answers)))
        val status = response.string("status")
        require(status in setOf("accepted", "unknown", "closed")) { "回答回执无效，请刷新核对" }
        return QuestionReceipt(status, response.string("notice"))
    }
}

private fun String.encode(): String = java.net.URLEncoder.encode(this, "UTF-8")
