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

@Composable internal fun RemotePreviewDialog(client: PreviewFeedClient?, onClose: () -> Unit, onBuiltIn: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val palette = LocalAppPalette.current
        val scope = rememberCoroutineScope()
        var result by remember(client) { mutableStateOf<PreviewFeedResult?>(null) }
        var busy by remember(client) { mutableStateOf(false) }
        var notice by remember(client) { mutableStateOf("正在获取最新预览…") }
        suspend fun refresh() {
            if (busy) return
            busy = true
            try {
                val loaded = client?.load() ?: throw PreviewFailure("preview_unconfigured")
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
            Text(notice, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), color = palette.muted, fontSize = 11.sp)
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            val doc = result?.document
            if (doc != null) IsolatedPreviewView(doc, Modifier.weight(1f).fillMaxWidth())
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
@Composable internal fun IsolatedPreviewView(document: PreviewDocument, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val view = remember(context) { WebView(context).apply {
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
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?) = WebResourceResponse(
                "text/plain", "utf-8", 403, "Preview isolated", emptyMap(), ByteArrayInputStream(ByteArray(0)))
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
        onDispose { owner.lifecycle.removeObserver(observer); view.resumeTimers(); view.stopLoading(); view.destroy() }
    }
    AndroidView(factory = { view }, modifier = modifier.testTag("isolated-preview-view"), update = {
        if (it.tag != document.binding) {
            it.tag = document.binding
            it.loadDataWithBaseURL("https://preview.invalid/", isolatedPreviewHtml(document.html), "text/html", "utf-8", null)
        }
    })
}
