package dev.codex.remote

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap

internal data class PreviewFeedResult(val document: PreviewDocument, val cached: Boolean, val notice: String)

class PreviewFeedClient internal constructor(context: Context, private val base: String, private val token: String) {
    companion object { private val scopeLocks = ConcurrentHashMap<String, Mutex>() }
    private val app = context.applicationContext
    // Scope is login-specific; a new server/login can never inherit another principal's cache.
    private val scope = previewSha("$base\n$token".toByteArray())
    private val prefs = app.getSharedPreferences("preview-ledger-v1", Context.MODE_PRIVATE)
    private val cache = AtomicFile(File(File(app.filesDir, "preview-cache"), "$scope.json"))
    private fun ledger() = PreviewLedger(prefs.getLong("$scope.sequence", 0), prefs.getString("$scope.binding", "").orEmpty())
    private fun persist(raw: ByteArray, doc: PreviewDocument) {
        check(cache.baseFile.parentFile!!.let { it.isDirectory || it.mkdirs() })
        val output = cache.startWrite()
        try { output.write(raw); cache.finishWrite(output) }
        catch (error: Exception) { cache.failWrite(output); throw error }
        check(prefs.edit().putLong("$scope.sequence", doc.sequence).putString("$scope.binding", doc.binding).commit())
    }
    internal suspend fun load(): PreviewFeedResult = withContext(Dispatchers.IO) {
        scopeLocks.computeIfAbsent(scope) { Mutex() }.withLock { loadBlocking() }
    }
    private fun loadBlocking(): PreviewFeedResult {
        val trust = app.assets.open("update-trust.json").use { it.readBytes() }
        val source = previewSource(base, trust)
        val previous = ledger()
        return try {
            val deadline = System.nanoTime() + 20_000_000_000L
            val connection = (source.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false; connectTimeout = 10000; readTimeout = 10000
                requestMethod = "GET"; useCaches = false
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
            }
            val raw = try {
                when (connection.responseCode) {
                    200 -> Unit
                    401, 403 -> throw PreviewFailure("preview_login_required")
                    404 -> throw PreviewFailure("preview_not_published")
                    in 300..399 -> throw PreviewFailure("preview_redirect_rejected")
                    else -> throw PreviewFailure("preview_server_unavailable")
                }
                if (connection.contentType?.substringBefore(';')?.trim() != "application/json") throw PreviewFailure("preview_type_invalid")
                if (connection.contentLengthLong > PREVIEW_MAX_ENVELOPE) throw PreviewFailure("preview_size_invalid")
                connection.inputStream.use { input ->
                    val bytes = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                    while (true) {
                        if (System.nanoTime() > deadline) throw PreviewFailure("preview_network_unavailable")
                        val n = input.read(buffer); if (n < 0) break
                        if (bytes.size() + n > PREVIEW_MAX_ENVELOPE) throw PreviewFailure("preview_size_invalid")
                        bytes.write(buffer, 0, n)
                    }
                    bytes.toByteArray()
                }
            } finally { connection.disconnect() }
            val doc = verifyPreview(raw, trust, source, previous, System.currentTimeMillis() / 1000)
            persist(raw, doc)
            PreviewFeedResult(doc, false, "已获取最新预览 · R${doc.sequence}")
        } catch (cancel: CancellationException) { throw cancel }
          catch (error: Exception) {
            val failure = error as? PreviewFailure
            if (failure?.code == "preview_login_required") throw failure
            val cached = try {
                // openRead restores an interrupted AtomicFile backup before checking its size.
                val bytes = cache.openRead().use {
                    if (it.channel.size() !in 1..PREVIEW_MAX_ENVELOPE.toLong()) throw PreviewFailure("preview_size_invalid")
                    it.readBytes()
                }
                verifyPreview(bytes, trust, source, ledger(), System.currentTimeMillis() / 1000)
            } catch (_: Exception) { null }
            if (cached != null) PreviewFeedResult(cached, true, "${previewNotice(error)} · 显示上次预览 R${cached.sequence}")
            else throw (failure ?: PreviewFailure("preview_network_unavailable"))
        }
    }
}

internal fun previewNotice(error: Throwable): String = when ((error as? PreviewFailure)?.code) {
    "preview_login_required" -> "登录已失效，请重新登录后刷新"
    "preview_unconfigured", "preview_source_mismatch" -> "当前服务器尚未配置可信预览源"
    "preview_not_published" -> "服务器尚未发布预览"
    "preview_renderer_unsupported" -> "此预览需要更新APP，原功能仍可使用"
    "preview_expired" -> "预览已过期，请刷新获取新版本"
    "preview_signature_invalid", "preview_key_unknown", "preview_hash_invalid", "preview_document_invalid",
    "preview_replayed", "preview_sequence_conflict", "preview_target_invalid", "preview_fields_invalid",
    "preview_type_invalid", "preview_size_invalid", "preview_redirect_rejected", "preview_encoding_invalid" -> "预览校验未通过，未载入新内容"
    else -> "预览暂时无法加载，请检查网络后刷新"
}
