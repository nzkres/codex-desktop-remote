package dev.codex.remote

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

@Composable internal fun RemotePreviewDialog(client: PreviewFeedClient?, onClose: () -> Unit, onBuiltIn: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val palette = LocalAppPalette.current
        val scope = rememberCoroutineScope()
        var result by remember(client) { mutableStateOf<PreviewFeedResult?>(null) }
        var busy by remember(client) { mutableStateOf(false) }
        var notice by remember(client) { mutableStateOf("正在获取最新预览…") }
        var renderStatus by remember(client) { mutableStateOf(PreviewRenderStatus(PreviewRenderPhase.Loading)) }
        var renderAttempt by remember(client) { mutableIntStateOf(0) }
        suspend fun refresh() {
            if (busy) return
            busy = true
            try {
                val loaded = client?.load() ?: throw PreviewFailure("preview_unconfigured")
                if (renderStatus.phase == PreviewRenderPhase.Failed) renderAttempt++
                if (result?.document?.binding != loaded.document.binding || renderStatus.phase == PreviewRenderPhase.Failed)
                    renderStatus = PreviewRenderStatus(PreviewRenderPhase.Loading)
                result = loaded; notice = loaded.notice
            } catch (cancel: CancellationException) { throw cancel }
              catch (error: Exception) {
                notice = previewNotice(error)
                if ((error as? PreviewFailure)?.code == "preview_login_required") result = null
            } finally { busy = false }
        }
        LaunchedEffect(client) { refresh() }
        Column(Modifier.fillMaxSize().background(palette.background).statusBarsPadding().navigationBarsPadding().testTag("remote-preview-dialog")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onClose) { Text("关闭") }
                Text(result?.document?.let { "预览 · R${it.sequence}" } ?: "界面预览", Modifier.weight(1f), color = palette.ink, fontSize = 15.sp)
                TextButton(onClick = { scope.launch { refresh() } }, enabled = !busy) { Text(if(busy) "加载中" else "刷新预览", fontSize = 12.sp) }
            }
            val displayNotice = result?.let {
                val rendered = renderStatus.notice(it.document.sequence)
                if (it.cached || notice != "已获取最新预览 · R${it.document.sequence}") "$notice；$rendered" else rendered
            } ?: notice
            Text(displayNotice, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = palette.muted, fontSize = 11.sp)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val doc = result?.document
            if (doc != null) IsolatedPreviewView(doc, Modifier.weight(1f).fillMaxWidth(), renderAttempt) { renderStatus = it }
            else Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(if (busy) "正在加载预览…" else "暂时没有可显示的远程预览，可刷新或查看内置示例。", color = palette.muted, fontSize = 13.sp)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("仅演示 · 不操作真实对话", Modifier.weight(1f).padding(start = 8.dp), color = palette.muted, fontSize = 11.sp)
                TextButton(onClick = onBuiltIn) { Text("内置示例", fontSize = 12.sp) }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable internal fun IsolatedPreviewView(document: PreviewDocument, modifier: Modifier = Modifier,
    attempt: Int = 0, onStatus: (PreviewRenderStatus) -> Unit = {}) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val currentStatus by rememberUpdatedState(onStatus)
    var failure by remember(document.binding, attempt) { mutableStateOf<PreviewRenderStatus?>(null) }
    val active = remember(document.binding, attempt) { AtomicBoolean(true) }
    val view = remember(context, document.binding, attempt) { WebView(context).apply {
        setBackgroundColor(android.graphics.Color.rgb(21, 21, 22))
        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.domStorageEnabled = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.blockNetworkLoads = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.setGeolocationEnabled(false)
        settings.safeBrowsingEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?) = true
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                if (previewLocalResource(request?.url?.scheme)) return null
                return WebResourceResponse("text/plain", "utf-8", 403, "Preview isolated", emptyMap(), ByteArrayInputStream(ByteArray(0)))
            }
            override fun onPageCommitVisible(view: WebView?, url: String?) {
                if (active.get() && failure == null) currentStatus(PreviewRenderStatus(PreviewRenderPhase.Visible))
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (active.get() && request?.isForMainFrame == true) {
                    val status = PreviewRenderStatus(PreviewRenderPhase.Failed, error?.errorCode)
                    failure = status; currentStatus(status)
                }
            }
            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                if (active.get() && request?.isForMainFrame == true) {
                    val status = PreviewRenderStatus(PreviewRenderPhase.Failed, response?.statusCode)
                    failure = status; currentStatus(status)
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) { request?.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) { callback?.invoke(origin, false, false) }
        }
        setDownloadListener { _, _, _, _, _ -> }
        // No addJavascriptInterface, no account token, task API or app origin in this view.
    } }
    DisposableEffect(view, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { view.onPause(); view.pauseTimers() }
            if (event == Lifecycle.Event.ON_RESUME) { view.resumeTimers(); view.onResume() }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { active.set(false); owner.lifecycle.removeObserver(observer); view.resumeTimers(); view.stopLoading(); view.destroy() }
    }
    Box(modifier) {
        key(view) {
            AndroidView(factory = { view }, modifier = Modifier.fillMaxSize().testTag("isolated-preview-view"), update = {
                if (it.tag != document.binding) {
                    it.tag = document.binding
                    it.loadDataWithBaseURL("https://preview.invalid/", isolatedPreviewHtml(document.html), "text/html", "utf-8", null)
                }
            })
        }
        if (failure != null) Box(Modifier.fillMaxSize().background(LocalAppPalette.current.background).padding(24.dp), contentAlignment = Alignment.Center) {
            Text(failure!!.notice(document.sequence), color = LocalAppPalette.current.ink, fontSize = 13.sp)
        }
    }
}
