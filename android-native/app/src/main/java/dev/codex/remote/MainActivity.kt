package dev.codex.remote

import android.content.Intent
import android.content.ClipData
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivity
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.view.WindowCompat
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class MainActivity : ComponentActivity() {
    private var notificationDestination by mutableStateOf<NotificationDestination?>(null)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationDestination = TaskNotifications.destination(intent)
    }
    override fun onResume() {
        super.onResume()
        TaskNotifications.start(this)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationDestination = TaskNotifications.destination(intent)
        TaskNotifications.createChannels(this)
        val appearance = getSharedPreferences("appearance", MODE_PRIVATE)
        setContent {
            var night by remember { mutableStateOf(appearance.getBoolean("night", true)) }
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = !night
                WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !night
            }
            HapiStyle(night) {
                RemoteApp(night, notificationDestination, onDestinationHandled = { notificationDestination = null }, onToggleNight = {
                    night = !night
                    appearance.edit().putBoolean("night", night).apply()
                })
            }
        }
    }
}

@Composable
private fun RemoteApp(night: Boolean, destination: NotificationDestination?, onDestinationHandled: () -> Unit, onToggleNight: () -> Unit) {
    val context = LocalContext.current
    val credentials = remember { Credentials(context) }
    val activity = LocalActivity.current as ComponentActivity
    val scope = remember(activity) { activity.lifecycleScope }
    val lifecycleOwner = LocalLifecycleOwner.current
    var gateway by remember { mutableStateOf<Gateway?>(null) }
    var projects by remember { mutableStateOf<List<Project>>(emptyList()) }
    var sessions by remember { mutableStateOf<List<Session>>(emptyList()) }
    var activityEntries by remember { mutableStateOf<List<ActivityEntry>>(emptyList()) }
    var rootTab by rememberSaveable(key = "hub-navigation-v12") { mutableStateOf(RootTab.Tasks) }
    var terminalManagerOpen by remember { mutableStateOf(false) }
    var focusPendingQuestion by remember { mutableStateOf(false) }
    val hubStatuses = remember(gateway) { mutableStateMapOf<String, HubStatus>() }
    val repositoryScope = remember(credentials.url, credentials.account, credentials.token) { draftScope(credentials.url, credentials.account, credentials.token) }
    val repositoryStore = remember(repositoryScope) { runCatching { ProjectRepositoryStore.open(context, repositoryScope) }.getOrNull() }
    val quotaStore = remember(repositoryScope) { runCatching { DeviceQuotaStore.open(context, repositoryScope) }.getOrNull() }
    val targetSummaries = remember(repositoryScope) { mutableMapOf<String, Triple<List<Session>, List<Project>, Long>>() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    // Only a direct tap may request Desktop navigation. Consume before the network call;
    // lifecycle restarts, polling and automatic retries must never replay it.
    val desktopOpenIntent = remember { DesktopOpenIntent() }
    var desktopOpenGeneration by remember { mutableIntStateOf(0) }
    var conversation by remember { mutableStateOf<Conversation?>(null) }
    var conversationError by remember { mutableStateOf("") }
    var desktopOpening by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var createOpen by remember { mutableStateOf(false) }
    var endpointOpen by remember { mutableStateOf(false) }
    val updater = remember(context) { UpdateClient(context) }
    var updateOffer by remember { mutableStateOf<UpdateOffer?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf("未检查更新") }
    var updateProgress by remember { mutableStateOf("") }
    val draftStorage = remember(gateway) { runCatching {
        gateway?.let { DraftStore.open(context, draftScope(it.taskEndpoint, credentials.account, credentials.token)) }
    } }
    val draftStore = draftStorage.getOrNull()
    val questionStorage = remember(gateway) { runCatching {
        gateway?.let { QuestionDraftStore.open(context, draftScope(it.taskEndpoint, credentials.account, credentials.token)) }
    } }
    val questionDraftStore = questionStorage.getOrNull()

    var syncError by remember { mutableStateOf("") }
    var lastMetadataAt by remember { mutableLongStateOf(0L) }
    var lastMetadataClient by remember { mutableStateOf<Gateway?>(null) }
    fun openSelected(id: String, question: Boolean = false) {
        focusPendingQuestion = question
        val client = gateway ?: return
        if (!client.target.isSelected) return
        desktopOpenIntent.request(client, id)
        selectedId = id
        conversation = null
        conversationError = ""
    }
    fun selectTarget(target: TaskTarget) {
        val client = gateway ?: return
        if (busy) return
        try {
            credentials.selectTarget(target)
            gateway = client.forTarget(target)
            desktopOpenIntent.clear()
            desktopOpening = false
            selectedId = null
            conversation = null
            conversationError = ""
            val cached = targetSummaries[target.id]
            sessions = cached?.first.orEmpty()
            projects = cached?.second.orEmpty()
            lastMetadataAt = cached?.third ?: 0L
            activityEntries = emptyList()
            error = ""
            syncError = ""
            lastMetadataClient = null
            createOpen = false
            rootTab = RootTab.Tasks
        } catch (failure: Exception) { error = networkError(failure) }
    }
    suspend fun refresh(client: Gateway, id: String? = selectedId, openDesktop: Boolean = false) {
        if (client !== gateway || !client.target.isSelected) return
        // Show the requested transcript first; list metadata can follow without a blank chat.
        if (id != null) {
            if (openDesktop && id != selectedId) return
            val fresh = try { client.conversation(id, openDesktop = openDesktop) }
            catch (failure: GatewayException) {
                if (!openDesktop || failure.code != "desktop_open_pending") throw failure
                // Desktop may finish loading after the one visible navigation request.
                // Every follow-up is a plain read; cancellation on task/target switch stops waiting.
                withTimeoutOrNull(15_000L) {
                    var loaded: Conversation? = null
                    while (loaded == null) {
                        delay(1_000L)
                        if (client !== gateway || id != selectedId) throw CancellationException("对话已切换")
                        try { loaded = client.conversation(id) }
                        catch (readFailure: GatewayException) {
                            if (!desktopOpenMayStillLoad(readFailure.code)) throw readFailure
                        }
                    }
                    loaded
                } ?: throw GatewayException("电脑仍在打开这条对话，请稍后点击重试。", 503, "desktop_open_pending")
            }
            if (client === gateway && id == selectedId) {
                val resolved = retainQuestionLifecycle(conversation, fresh, questionDraftStore, id)
                questionDraftStore?.reconcile(id, resolved.questions)
                hubStatuses[id] = HubStatus(resolved.questions.any { it.status in setOf("pending", "desktop_required") },
                    resolved.observation.state == "running" && resolved.questions.none { it.status in setOf("pending", "desktop_required") }, System.currentTimeMillis())
                runCatching { quotaStore?.update(client.target.id, id, resolved.usage) }
                conversation = resolved
                conversationError = ""
                fresh.session?.let { current -> sessions = sessions.filterNot { it.id == current.id } + current }
            }
        }
        // A selected conversation already carries its current status. Other
        // task/project summaries do not need to be re-downloaded every 3 seconds.
        if (id == null || lastMetadataClient !== client || System.currentTimeMillis() - lastMetadataAt >= 30_000) {
            val nextProjects = client.projects()
            val nextSessions = client.sessions()
            val nextActivity = if (id == null) client.activity() else null
            if (client !== gateway) return
            projects = nextProjects
            sessions = nextSessions
            if (nextActivity != null) activityEntries = nextActivity
            lastMetadataClient = client
            lastMetadataAt = System.currentTimeMillis()
            targetSummaries[client.target.id] = Triple(sessions, projects, lastMetadataAt)
        }
        if (client === gateway) syncError = ""
    }

    LaunchedEffect(Unit) {
        if (credentials.url.isNotBlank() && credentials.token.isNotBlank()) {
            try {
                val client = Gateway(credentials.url, credentials.token, credentials.taskTarget())
                gateway = client
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                error = networkError(exception)
            }
        }
    }

    LaunchedEffect(gateway, destination, busy) {
        if (gateway != null && destination != null) {
            // Do not change execution target while a user action is awaiting its receipt.
            if (busy) return@LaunchedEffect
            val target = TaskTarget(destination.terminalId, destination.terminalName)
            if (target.isSelected && destination.scope == TaskNotifications.scope(credentials, target)) {
                if (gateway!!.target.id != target.id) selectTarget(target)
                if (gateway!!.target.id != target.id) return@LaunchedEffect
                desktopOpenIntent.clear() // Notification navigation never opens Desktop.
                desktopOpening = false
                selectedId = destination.threadId
                conversation = null
                conversationError = ""
                TaskNotifications.clearThread(context, destination.scope, destination.threadId)
            }
            onDestinationHandled()
        }
    }

    LaunchedEffect(gateway, selectedId, lifecycleOwner, desktopOpenGeneration) {
        val client = gateway ?: return@LaunchedEffect
        if (!client.target.isSelected) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        var failures = 0
        while (true) {
            val openDesktop = desktopOpenIntent.consume(client, selectedId)
            if (openDesktop) desktopOpening = true
            val requestedId = selectedId
            try { refresh(client, requestedId, openDesktop); failures = 0 } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (client !== gateway) return@repeatOnLifecycle
                failures++
                if (exception is GatewayException && exception.status == 401) {
                    credentials.token = ""
                    TaskNotifications.stop(context, clear = true)
                    gateway = null
                    desktopOpenIntent.clear()
                    desktopOpening = false
                    selectedId = null
                    conversation = null
                    error = "登录已过期，请重新登录"
                } else if (selectedId != null) conversationError = networkError(exception)
                else syncError = networkError(exception)
            } finally {
                if (openDesktop && client === gateway && selectedId == requestedId) desktopOpening = false
            }
            val normal = if (selectedId == null) 8_000L else if (conversation?.observation?.state in setOf("running", "quiet")) 3_000L else 10_000L
            delay(if (failures == 0) normal else (normal * (1L shl failures.coerceAtMost(4))).coerceAtMost(60_000L))
        }
        }
    }

    fun perform(action: suspend (Gateway) -> Unit) {
        if (busy) return
        val client = gateway ?: return
        busy = true
        scope.launch {
            error = ""
            try {
                action(client)
                refresh(client)
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                if (client === gateway) error = networkError(exception, sending = true)
            } finally { busy = false }
        }
    }
    suspend fun requireSafeUpdateHandoff(ignoreLocalBusy: Boolean = false): ConnectorUpdateProof {
        val client = gateway ?: throw UpdateFailure("update_not_connected")
        if (client.target.id.isBlank()) throw UpdateFailure("update_target_required")
        val proof = try { client.updateStatus() } catch (error: CancellationException) { throw error }
            catch (error: Exception) { throw updateStatusFailure(error) }
        if (client !== gateway) throw UpdateFailure("update_target_changed")
        requireLocalUpdateSafety(client.target.id, busy && !ignoreLocalBusy, credentials.hasUnconfirmedMessages())
        return proof
    }

    LaunchedEffect(gateway, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!updater.hasPendingInstall()) return@repeatOnLifecycle
            val client = gateway
            try {
                val proof = try { client?.updateStatus() }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) { null }
                if (client !== gateway || updateBusy) return@repeatOnLifecycle
                val localBusy = busy
                val pendingSend = credentials.hasUnconfirmedMessages()
                val result = withContext(Dispatchers.IO) {
                    updater.reconcilePendingInstall(client?.target?.id, localBusy, pendingSend, proof)
                }
                if (client !== gateway) return@repeatOnLifecycle
                when (result) {
                    PendingInstallState.RETRY -> updateStatus = "上次安装尚未完成或已取消；旧版仍在，可重新检查更新"
                    PendingInstallState.TASK_PROOF_REQUIRED -> updateStatus = "新版已启动、安装包已核验；任务通道待核对，返回此页可重试"
                    PendingInstallState.VERIFIED -> updateStatus = "新版实际安装与启动已核验，更新记录保留，任务通道检查通过；手机任务完成仍需单独确认"
                    PendingInstallState.NEEDS_REVIEW -> updateStatus = "安装版本或更新记录不匹配；请保留应用数据，联系维护者"
                    null -> Unit
                }
            } catch (cancel: CancellationException) { throw cancel }
              catch (_: Exception) { updateStatus = "更新安装结果待核对；请保留应用数据" }
        }
    }

    val palette = LocalAppPalette.current
    Box(Modifier.fillMaxSize().background(if(palette.isNight) Color(LocalChatAppearance.current.skin.palette.dark) else palette.background)) {
    if (selectedId == null) TerminalGrid(sessions.any { hubStatuses[it.id]?.running == true }, Modifier.matchParentSize())
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (gateway != null && draftStorage.isFailure) Text("本机草稿存储暂不可用，请复制输入内容后再离开对话。",
            color = palette.errorInk, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
        if (error.isNotBlank() || syncError.isNotBlank()) {
            Surface(color = palette.errorSurface) {
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error.ifBlank { syncError }, Modifier.weight(1f), color = palette.errorInk, fontSize = 12.sp)
                    TextButton(onClick = { error = ""; syncError = "" }) { Text("关闭") }
                }
            }
        }
        when {
            gateway == null -> ConnectScreen(
                initialUrl = credentials.url,
                busy = busy,
                night = night,
                onToggleNight = onToggleNight,
                onConnect = { url, username, password ->
                    scope.launch {
                        busy = true
                        error = ""
                        try {
                            val endpoint = url.trim().trimEnd('/')
                            val token = Gateway.login(endpoint, username.trim(), password)
                            val client = Gateway(endpoint, token)
                            // A fresh login may be a different account. Do not leave its predecessor's alerts visible.
                            TaskNotifications.stop(context, clear = true)
                            SignalLedger(context, "").clearAll()
                            credentials.url = endpoint
                            credentials.token = token
                            credentials.account = username.trim()
                            gateway = client.forTarget(credentials.taskTarget())
                            desktopOpenIntent.clear()
                            desktopOpening = false
                            selectedId = null
                            conversation = null
                            sessions = emptyList()
                            projects = emptyList()
                            activityEntries = emptyList()
                            rootTab = RootTab.Tasks
                            TaskNotifications.start(context)
                        } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            error = networkError(exception)
                        } finally { busy = false }
                    }
                },
            )
            selectedId == null || !gateway!!.target.isSelected -> Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    when (rootTab) {
                        RootTab.Tasks -> ConversationHub(gateway!!, sessions, projects, activityEntries, busy, hubStatuses, lastMetadataAt, draftStore, quotaStore,
                            onSelect = ::selectTarget, onOpen = { id, question -> openSelected(id, question) }, onManage = { terminalManagerOpen = true })
                        RootTab.Projects -> ProjectRepositoryScreen(repositoryStore)
                        RootTab.Me -> MeScreen(
                            gatewayUrl = credentials.url,
                            onManageDevices = { terminalManagerOpen = true },
                            night = night,
                            onToggleNight = onToggleNight,
                            updateStatus = updateStatus,
                            updateProgress = updateProgress,
                            updateBusy = updateBusy,
                            updateAvailable = updateOffer != null,
                            onCheckUpdate = {
                                if (!updateBusy) scope.launch {
                                    updateBusy = true
                                    updateOffer = null
                                    updateProgress = ""
                                    updateStatus = "正在核对更新…"
                                    try {
                                        updater.requireConfigured()
                                        requireSafeUpdateHandoff()
                                        val offer = updater.check()
                                        updateOffer = offer
                                        updateStatus = "发现候选版本 ${offer.payload.version} · ${offer.payload.channel} 渠道\n${offer.payload.notes}"
                                    } catch (error: CancellationException) { throw error }
                                    catch (error: UpdateFailure) {
                                        if (error.code.startsWith("ota_login_") || error.code in setOf("ota_access_denied", "ota_source_mismatch"))
                                            updateOffer = null
                                        updateStatus = when (error.code) {
                                            "ota_unconfigured" -> "OTA 尚未配置可信更新源与公钥"
                                            "ota_login_required", "ota_login_expired" -> "请重新登录测试中继后检查更新"
                                            "ota_access_denied" -> "当前账号没有更新文件权限，请联系测试中继管理员"
                                            "ota_login_changed" -> "登录身份已变化，请重新检查更新"
                                            "ota_source_mismatch" -> "更新源与当前登录的 HTTPS 中继不一致，已拒绝请求"
                                            "update_not_newer" -> "当前已是该渠道最新版本"
                                            else -> updateBlockReason(error.code) ?: "检查失败：${error.code}"
                                        }
                                    } catch (_: Exception) { updateStatus = "检查失败：更新源或状态不可用" }
                                    finally { updateBusy = false }
                                }
                            },
                            onInstallUpdate = {
                                val offer = updateOffer
                                if (offer != null && !updateBusy && !busy) scope.launch {
                                    updateBusy = true
                                    busy = true
                                    try {
                                        val before = requireSafeUpdateHandoff(ignoreLocalBusy = true)
                                        if (!context.packageManager.canRequestPackageInstalls()) {
                                            updateStatus = "请允许此 App 安装测试包，返回后再次点击安装"
                                            activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                                Uri.parse("package:${context.packageName}")))
                                        } else {
                                            updateStatus = "正在下载并校验安装包…"
                                            val file = updater.download(offer) { copied, total ->
                                                scope.launch { updateProgress = "${copied / 1024} / ${total / 1024} KiB" }
                                            }
                                            val after = requireSafeUpdateHandoff(ignoreLocalBusy = true)
                                            if (after.version != before.version) throw UpdateFailure("update_connector_changed")
                                            updater.verifyBeforeInstall(file, offer)
                                            val uri = updater.installUri(file)
                                            val install = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                                                data = uri
                                                clipData = ClipData.newRawUri("Desktop Remote update", uri)
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            withContext(Dispatchers.IO) { updater.recordPendingInstall(file, offer) }
                                            activity.startActivity(install)
                                            updateStatus = "已交给 Android 系统；请确认安装并重新打开核对版本"
                                        }
                                    } catch (error: CancellationException) { throw error }
                                    catch (error: UpdateFailure) {
                                        if (error.code.startsWith("ota_login_") || error.code in setOf("ota_access_denied", "ota_source_mismatch"))
                                            updateOffer = null
                                        updateStatus = when (error.code) {
                                            "ota_login_required", "ota_login_expired" -> "安装已延期：请重新登录测试中继"
                                            "ota_access_denied" -> "安装已延期：当前账号没有更新文件权限"
                                            "ota_login_changed" -> "安装已延期：登录身份已变化，请重新检查更新"
                                            "ota_source_mismatch" -> "安装已延期：更新源与登录中继不一致"
                                            else -> "安装已延期：${updateBlockReason(error.code) ?: error.code}"
                                        }
                                    }
                                    catch (_: Exception) { updateStatus = "安装已延期：下载或任务状态不可用" }
                                    finally { busy = false; updateBusy = false }
                                }
                            },
                            onChangeGateway = { endpointOpen = true },
                            onDisconnect = {
                                val client = gateway
                                scope.launch { try { client?.logout() } catch (_: Exception) { /* local logout still succeeds */ } }
                                credentials.clear()
                                TaskNotifications.setEnabled(context, false)
                                TaskNotifications.stop(context, clear = true)
                                SignalLedger(context, "").clearAll()
                                gateway = null
                                desktopOpenIntent.clear()
                                desktopOpening = false
                                selectedId = null
                                conversation = null
                            },
                        )
                    }
                }
                val draftId = draftStore?.latest(sessions.map { it.id }.toSet())
                val draftSession = sessions.firstOrNull { it.id == draftId }
                if (draftId != null && draftSession != null) DraftResumeBar(draftSession.title, draftStore?.read(draftId)?.text.orEmpty()) {
                    openSelected(draftId)
                }
                RootBottomBar(rootTab) { rootTab = it }
            }
            else -> {
                val id = selectedId!!
                val session = sessions.firstOrNull { it.id == id }
                key(gateway, id) { ChatScreen(
                    threadId = id,
                    terminalName = gateway!!.target.name,
                    focusPendingQuestion = focusPendingQuestion,
                    session = session,
                    projectName = "${gateway!!.target.name} · " + projects.firstOrNull { it.id == session?.projectId }?.name.orEmpty(),
                    conversation = conversation,
                    loadError = conversationError,
                    desktopOpening = desktopOpening,
                    busy = busy,
                    night = night,
                    onToggleNight = onToggleNight,
                    onBack = { desktopOpenIntent.clear(); desktopOpening = false; selectedId = null; conversation = null },
                    onRetry = {
                        val client = gateway
                        if (client != null && id == selectedId && !busy) {
                            conversationError = ""
                            desktopOpenIntent.request(client, id)
                            desktopOpenGeneration++ // Cancel an in-flight poll; restart with one explicit request.
                        }
                    },
                    onLoadOlder = {
                        val client = gateway ?: throw IllegalStateException("未连接")
                        val fresh = client.conversation(id, older = true)
                        if (selectedId != id || gateway !== client) throw kotlinx.coroutines.CancellationException("对话已切换")
                        conversation = fresh
                        fresh
                    },
                    onLoadFull = { item ->
                        val client = gateway ?: throw IllegalStateException("未连接")
                        val fresh = client.fullItem(id, item.id)
                        if (selectedId != id || gateway !== client) throw kotlinx.coroutines.CancellationException("对话已切换")
                        conversation = fresh
                        fresh.turns.flatMap { it.items }.firstOrNull { it.id == item.id }
                            ?: throw IllegalStateException("消息已变化，请重新打开对话")
                    },
                    onSend = { text ->
                        val client = gateway ?: throw IllegalStateException("未连接")
                        check(!busy) { "上一项操作尚未确认，请稍候。" }
                        busy = true
                        try {
                        val endpoint = draftScope(client.taskEndpoint, credentials.account, credentials.token)
                        val messageId = credentials.pendingMessageId(endpoint, id, text)
                        val turnId = client.send(id, text, messageId)
                        credentials.acknowledgeMessage(endpoint, id)
                        // Animate the actual POST receipt immediately, not a potentially slow follow-up read.
                        scope.launch {
                            try { refresh(client, id) }
                            catch (exception: CancellationException) { throw exception }
                            catch (_: Exception) { /* polling retries; never re-send accepted input */ }
                        }
                        turnId
                        } finally { busy = false }
                    },
                    onStop = {
                        val turnId = session?.activeTurnId
                        if (turnId != null) perform { it.stop(id, turnId) }
                    },
                    onMode = { mode -> perform { it.mode(id, mode) } },
                    onImplement = { perform { it.implementPlan(id) } },
                    onApproval = { approval, approve -> perform { it.decide(id, approval.id, approve) } },
                    onAnswer = { request, answers ->
                        val client = gateway
                        val store = questionDraftStore
                        if (client != null && store != null && !busy && selectedId == id &&
                            conversation?.capabilities?.questions == true &&
                            conversation?.questions?.any { it.id == request.id && it.revision == request.revision &&
                                it.turnId == request.turnId && it.kind == request.kind && it.status == "pending" &&
                                it.questions.none { question -> question.secret } } == true) {
                            val current = store.read(id, request)
                            if (current.state == "pending") {
                                if (runCatching { store.save(id, request, current.copy(answers = answers, state = "submitting")) }.isFailure) {
                                    error = "无法加密保存答案，回答未提交。"
                                } else {
                                    busy = true
                                    scope.launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
                                        try {
                                            val receipt = try { client.answer(id, request, answers) }
                                            catch (cancel: CancellationException) {
                                                if (runCatching { store.save(id, request, store.read(id, request).copy(state = "unknown",
                                                    notice = "提交已中断，结果待核对；请刷新，不要重复提交。")) }.isFailure)
                                                    error = "提交已中断且状态无法保存，请刷新任务核对；不要重复提交。"
                                                throw cancel
                                            } catch (failure: GatewayException) {
                                                val rejected = questionAnswerRejectedBeforeSend(failure)
                                                if (runCatching { store.save(id, request, store.read(id, request).copy(
                                                    state = if (failure.code == "question_changed") "expired" else if (rejected) "pending" else "unknown",
                                                    notice = if (rejected) failure.message.orEmpty().ifBlank { "回答未发送，请刷新核对。" }
                                                        else "提交结果待核对；请刷新确认，不要重复提交。")) }.isFailure)
                                                    error = "回答状态无法保存，请刷新任务核对；不要重复提交。"
                                                return@launch
                                            } catch (_: Exception) {
                                                if (runCatching { store.save(id, request, store.read(id, request).copy(state = "unknown",
                                                    notice = "提交结果待核对；请刷新确认，不要重复提交。")) }.isFailure)
                                                    error = "回答状态无法保存，请刷新任务核对；不要重复提交。"
                                                return@launch
                                            }
                                            if (runCatching { store.save(id, request, store.read(id, request).copy(
                                                state = receipt.status, notice = receipt.notice)) }.isFailure) {
                                                error = "电脑已返回回答回执，但本机无法保存状态；请刷新核对，不要重复提交。"
                                                return@launch
                                            }
                                            if (client === gateway && selectedId == id && receipt.status in setOf("accepted", "closed")) {
                                                conversation = conversation?.let { currentConversation -> currentConversation.copy(questions = currentConversation.questions.map {
                                                    if(it.id == request.id && it.revision == request.revision) it.copy(status=receipt.status, notice=receipt.notice) else it
                                                }) }
                                                hubStatuses[id] = HubStatus(conversation?.questions?.any { it.status in setOf("pending", "desktop_required") } == true,
                                                    false, System.currentTimeMillis())
                                            }
                                            if (client === gateway && selectedId == id) try { refresh(client, id) }
                                            catch (cancel: CancellationException) { throw cancel }
                                            catch (_: Exception) { error = "回答回执已保存，刷新失败；稍后再核对。" }
                                        } finally { busy = false }
                                    }
                                }
                            }
                        }
                    },
                    onRefreshQuestions = {
                        val client = gateway
                        if (client != null && selectedId == id && !busy) scope.launch {
                            try { refresh(client, id) }
                            catch (cancel: CancellationException) { throw cancel }
                            catch (failure: Exception) { if (client === gateway && selectedId == id) conversationError = networkError(failure) }
                        }
                    },
                    draftStore = draftStore,
                    questionDraftStore = questionDraftStore,
                    questionStorageAvailable = questionStorage.isSuccess && questionDraftStore != null,
                    onLoadTask = { item ->
                        val client = gateway ?: throw IllegalStateException("未连接")
                        val full = client.taskItem(id, item.id)
                        if (selectedId != id || gateway !== client) throw kotlinx.coroutines.CancellationException("对话已切换")
                        full
                    },
                    onLoadTaskPage = { before ->
                        val client = gateway ?: throw IllegalStateException("未连接")
                        val page = client.taskHistory(id, before)
                        if (selectedId != id || gateway !== client) throw kotlinx.coroutines.CancellationException("对话已切换")
                        page
                    },
                ) }
            }
        }
    }

    }
    if (terminalManagerOpen && gateway != null) Dialog(onDismissRequest = { if(!busy) terminalManagerOpen = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(color = palette.background.copy(alpha = .94f)) { Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { TextButton(onClick = { terminalManagerOpen = false }, enabled = !busy) { Text("返回") } }
            val client = gateway!!
            TerminalScreen(remember(client) { client.terminalControl() }, client.target, busy, { target -> if(!busy) { selectTarget(target); terminalManagerOpen=false } }, onUnauthorized = {
                if(gateway === client) { credentials.token=""; TaskNotifications.stop(context, clear=true); gateway=null;terminalManagerOpen=false }
            })
        } }
    }
    if (gateway != null) NotificationSettings(showIntroduction = true)

    if (createOpen && gateway != null) {
        NewTaskDialog(
            projects = projects,
            terminalName = gateway!!.target.name,
            busy = busy,
            onDismiss = { createOpen = false },
            onCreate = { project, title, mode, firstMessage ->
                val client = gateway ?: return@NewTaskDialog
                val targetDraftStore = draftStore
                val sendScope = draftScope(client.taskEndpoint, credentials.account, credentials.token)
                scope.launch {
                    busy = true
                    error = ""
                    var createdId: String? = null
                    try {
                        val created = client.create(project, title.ifBlank { "新任务" }, mode)
                        createdId = created.id
                        if (gateway !== client) throw CancellationException("电脑已切换")
                        // Once creation succeeds, a send/read failure must not
                        // leave the create dialog inviting a second new thread.
                        sessions = listOf(created) + sessions.filter { it.id != created.id }
                        desktopOpenIntent.clear()
                        desktopOpening = false
                        conversation = null
                        selectedId = created.id
                        createOpen = false
                        if (firstMessage.isNotBlank()) {
                            // A successful creation followed by a failed first send must not lose input.
                            targetDraftStore?.write(created.id, firstMessage)
                            val firstDraft = targetDraftStore?.read(created.id)
                            val endpoint = sendScope
                            val messageId = credentials.pendingMessageId(endpoint, created.id, firstMessage)
                            client.send(created.id, firstMessage, messageId)
                            credentials.acknowledgeMessage(endpoint, created.id)
                            targetDraftStore?.clearIfUnchanged(created.id, firstDraft)
                        }
                        try { refresh(client, created.id) } catch (exception: CancellationException) {
                            throw exception
                        } catch (exception: Exception) {
                            conversationError = exception.message ?: "任务已创建，请刷新对话"
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        error = if (createdId != null) "任务已创建，但首条消息未确认。请先核对当前对话，不要重复新建：${exception.message.orEmpty()}"
                            else exception.message ?: "创建失败"
                    } finally { busy = false }
                }
            },
        )
    }

    if (endpointOpen && gateway != null) {
        var candidate by remember(endpointOpen) { mutableStateOf(credentials.url) }
        var switching by remember { mutableStateOf(false) }
        var switchError by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { if (!switching) endpointOpen = false },
            title = { Text("更改中继地址") },
            text = { Column {
                Text("这里只更改统一服务地址，不用于选择电脑。新服务器需要重新登录；请先保存未发送内容。", color = HapiPalette.muted, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(candidate, { candidate = it }, label = { Text("Gateway 地址") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                if (switchError.isNotBlank()) Text(switchError, color = LocalAppPalette.current.errorInk, fontSize = 12.sp)
            } },
            confirmButton = { TextButton(onClick = {
                scope.launch {
                    switching = true
                    switchError = ""
                    try {
                        val url = candidate.trim().trimEnd('/')
                        Gateway(url, credentials.token) // Validate URL only; never send this server's token to another host.
                        credentials.url = url
                        credentials.token = ""
                        credentials.account = ""
                        TaskNotifications.stop(context, clear = true)
                        projects = emptyList()
                        sessions = emptyList()
                        activityEntries = emptyList()
                        selectedId = null
                        conversation = null
                        gateway = null
                        endpointOpen = false
                    } catch (exception: CancellationException) { throw exception }
                    catch (exception: Exception) { switchError = exception.message ?: "连接失败；原地址已保留" }
                    finally { switching = false }
                }
            }, enabled = !switching && candidate.isNotBlank()) { Text(if (switching) "切换中…" else "更改并重新登录") } },
            dismissButton = { TextButton(onClick = { endpointOpen = false }, enabled = !switching) { Text("取消") } },
        )
    }
}

@Composable
private fun TargetSelectionRequired(onChooseTarget: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(HapiPalette.background).padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("请先选择电脑", color = HapiPalette.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Text("选择一台已接入的电脑后，即可查看它的任务和动态。", color = HapiPalette.muted)
        Spacer(Modifier.height(20.dp))
        GlassActionButton("选择电脑", onChooseTarget, Modifier.testTag("choose-task-target"))
    }
}

@Composable
private fun ConnectScreen(initialUrl: String, busy: Boolean, night: Boolean, onToggleNight: () -> Unit, onConnect: (String, String, String) -> Unit) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.Center) {
        TextButton(onClick = onToggleNight, modifier = Modifier.align(Alignment.End).semantics { contentDescription = if (night) "切换浅色背景" else "切换深夜背景" }) {
            Text(if (night) "☼" else "☾", fontSize = 22.sp)
        }
        Text("Desktop Remote Lite", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = HapiPalette.ink)
        Spacer(Modifier.height(7.dp))
        Text("登录统一账号，查看已连接的电脑", color = HapiPalette.muted)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(url, { url = it }, label = { Text("中继地址") }, placeholder = { Text("https://your-server.example") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(username, { username = it }, label = { Text("账号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("密码") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(18.dp))
        Button(onClick = { onConnect(url, username, password) }, enabled = !busy && url.isNotBlank() && username.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "正在登录…" else "登录", Modifier.processingLight(busy))
        }
        Spacer(Modifier.height(9.dp))
        Text("正式版只接受 HTTPS；密码只用于登录，不会保存在手机上。", color = HapiPalette.muted, fontSize = 12.sp)
    }
}

@Composable
internal fun SessionList(sessions: List<Session>, activityEntries: List<ActivityEntry>, projects: List<Project>, onOpen: (String) -> Unit, onCreate: () -> Unit,
    draftStore: DraftStore? = null, targetName: String = "", onChooseTarget: (() -> Unit)? = null) {
    val observations = activityEntries.associateBy { it.session.id }
    Column(Modifier.fillMaxSize().background(HapiPalette.background)) {
        if (onChooseTarget != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(targetName, Modifier.weight(1f), color = HapiPalette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onChooseTarget) { Text("切换电脑") }
        }
        Row(Modifier.fillMaxWidth().height(68.dp).padding(start = 16.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("任务", color = HapiPalette.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("${sessions.size} 个对话 · ${projects.size} 个项目", color = HapiPalette.muted, fontSize = 11.sp)
            }
            GlassActionButton("新对话", onCreate, Modifier.testTag("new-conversation"), icon = "plus", enabled = false)
        }
        Text("新建对话请在电脑处理；已有任务可在手机继续。", Modifier.padding(start = 16.dp, bottom = 8.dp),
            color = HapiPalette.muted, fontSize = 11.sp)
        HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
        if (sessions.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无对话，请在电脑新建任务后刷新。", color = HapiPalette.muted)
        } else LazyColumn(Modifier.fillMaxSize()) {
            items(sessions.sortedByDescending { it.updatedAt }, key = { it.id }) { session ->
                val projectName = projects.firstOrNull { it.id == session.projectId }?.name ?: "Codex"
                Column(Modifier.fillMaxWidth().clickable { onOpen(session.id) }.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val active = sessionProcessing(session, observations[session.id]?.observation)
                        Box(Modifier.size(8.dp).processingLight(active).background(if (active) HapiPalette.green else HapiPalette.muted, RoundedCornerShape(50)))
                        Spacer(Modifier.width(13.dp))
                        Text(session.title, Modifier.weight(1f), color = HapiPalette.ink, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        Text(android.text.format.DateUtils.getRelativeTimeSpanString(session.updatedAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS).toString(), color = HapiPalette.muted, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(7.dp))
                    Text("◇   codex  ·  $projectName  ·  ${if (session.mode == "plan") "Plan" else "Agent"}", modifier = Modifier.padding(start = 21.dp), color = HapiPalette.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    DraftLine(draftStore?.read(session.id)?.text.orEmpty(), Modifier.padding(start = 21.dp, top = 5.dp))
                }
                HorizontalDivider(color = HapiPalette.divider, thickness = 0.5.dp)
            }
        }
    }
}

@Composable
internal fun NewTaskDialog(projects: List<Project>, busy: Boolean, onDismiss: () -> Unit, terminalName: String = "",
    onCreate: (String, String, String, String) -> Unit) {
    var projectId by remember { mutableStateOf(projects.firstOrNull()?.id.orEmpty()) }
    var title by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("default") }
    var projectsOpen by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        shape = RoundedCornerShape(28.dp), containerColor = HapiPalette.background,
        title = { Text("新建对话", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (terminalName.isNotBlank()) Text("执行电脑：$terminalName", color = HapiPalette.muted, fontSize = 12.sp)
                Box {
                    GlassActionButton(projects.firstOrNull { it.id == projectId }?.name ?: "选择项目", { projectsOpen = true },
                        Modifier.fillMaxWidth(), icon = "plan", enabled = !busy, chevron = true, menuOpen = projectsOpen)
                    DropdownMenu(projectsOpen && !busy, { projectsOpen = false }) {
                        projects.forEach { project -> DropdownMenuItem(text = { Text(project.name) }, onClick = { projectId = project.id; projectsOpen = false }) }
                    }
                }
                OutlinedTextField(title, { title = it }, enabled = !busy, label = { Text("标题（可选）") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(prompt, { prompt = it }, enabled = !busy, label = { Text("第一条消息") }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 4)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassActionButton("Agent", { mode = "default" }, icon = "agent", selected = mode == "default", enabled = !busy)
                    Spacer(Modifier.width(8.dp))
                    GlassActionButton("Plan", { mode = "plan" }, icon = "plan", selected = mode == "plan", enabled = !busy)
                }
            }
        },
        confirmButton = { GlassActionButton(if (busy) "正在创建" else "创建对话", { onCreate(projectId, title, mode, prompt) },
            Modifier.testTag("confirm-new-conversation"), icon = "plus", selected = true, busy = busy, enabled = projectId.isNotBlank()) },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("取消", color = HapiPalette.muted) } },
    )
}
