package dev.codex.remote

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest

internal data class UpdateOffer(val payload: UpdatePayload, val ledger: UpdateLedger)

internal class UpdateClient(private val context: Context) {
    private val prefs = context.getSharedPreferences("android-update-ledger", Context.MODE_PRIVATE)
    private fun trustBytes(): ByteArray = context.assets.open("update-trust.json").use { it.readBytes() }
    private fun source(): URI {
        val trust = JSONObject(trustBytes().toString(Charsets.UTF_8))
        val keys = trust.getJSONObject("keys")
        val sources = trust.getJSONObject("sources")
        if (keys.length() == 0 || !sources.has("android")) throw UpdateFailure("ota_unconfigured")
        return checkedUri(sources.getString("android"))
    }
    fun requireConfigured() { source() }
    private fun ledger(): UpdateLedger {
        val raw = prefs.getString("ledger", null) ?: return UpdateLedger()
        try {
            val data = JSONObject(raw)
            if (data.keySet() != setOf("sequence", "binding", "versions")) throw Exception()
            val versions = data.getJSONObject("versions")
            val map = versions.keySet().associateWith { versions.getString(it) }
            val sequence = data.getLong("sequence")
            val binding = data.getString("binding")
            if (sequence < 0 || (sequence > 0 && !binding.matches(Regex("[a-f0-9]{64}"))) ||
                map.values.any { !it.matches(Regex("[a-f0-9]{64}")) }) throw Exception()
            return UpdateLedger(sequence, binding, map)
        } catch (_: Exception) { throw UpdateFailure("ledger_invalid") }
    }
    private fun saveLedger(value: UpdateLedger) {
        val versions = JSONObject()
        value.versions.forEach { (version, hash) -> versions.put(version, hash) }
        val raw = JSONObject().put("sequence", value.sequence).put("binding", value.binding)
            .put("versions", versions).toString()
        if (!prefs.edit().putString("ledger", raw).commit()) throw UpdateFailure("ledger_write_failed")
    }
    private fun connection(uri: URI, accept: String): HttpURLConnection {
        val conn = uri.toURL().openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", accept)
        return conn
    }
    private fun fetch(): ByteArray {
        val conn = connection(source(), "application/json")
        try {
            if (conn.responseCode != 200) throw UpdateFailure("metadata_http_failed")
            conn.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    if (output.size() + count > 65536) throw UpdateFailure("metadata_size_invalid")
                    output.write(chunk, 0, count)
                }
                return output.toByteArray()
            }
        } finally { conn.disconnect() }
    }
    suspend fun check(): UpdateOffer = withContext(Dispatchers.IO) {
        val verified = verifyUpdate(fetch(), trustBytes(), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
            Build.VERSION.SDK_INT, ledger(), System.currentTimeMillis() / 1000)
        saveLedger(verified.second)
        UpdateOffer(verified.first, verified.second)
    }
    suspend fun download(offer: UpdateOffer, progress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val payload = offer.payload
        if (System.currentTimeMillis() / 1000 >= payload.expiresAt) throw UpdateFailure("metadata_expired")
        if (ledger() != offer.ledger) throw UpdateFailure("ledger_changed")
        val directory = File(context.cacheDir, "updates")
        if (!directory.isDirectory && !directory.mkdirs()) throw UpdateFailure("download_storage_failed")
        val target = File(directory, "update-${payload.sha256}.apk")
        val partial = File(directory, "update-${payload.sha256}.partial")
        if (partial.exists() && !partial.delete()) throw UpdateFailure("download_storage_failed")
        val conn = connection(payload.url, "application/vnd.android.package-archive")
        try {
            if (conn.responseCode != 200) throw UpdateFailure("download_http_failed")
            val digest = MessageDigest.getInstance("SHA-256")
            var copied = 0L
            conn.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val chunk = ByteArray(65536)
                    while (true) {
                        val count = input.read(chunk)
                        if (count < 0) break
                        copied += count
                        if (copied > payload.bytes) throw UpdateFailure("download_size_invalid")
                        digest.update(chunk, 0, count)
                        output.write(chunk, 0, count)
                        progress(copied, payload.bytes)
                    }
                    output.fd.sync()
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            if (copied != payload.bytes || hash != payload.sha256) throw UpdateFailure("download_hash_invalid")
            if (target.exists() && !target.delete()) throw UpdateFailure("download_storage_failed")
            if (!partial.renameTo(target)) throw UpdateFailure("download_storage_failed")
            verifyApk(target, payload)
            target
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally { conn.disconnect() }
    }
    fun verifyBeforeInstall(file: File, offer: UpdateOffer) {
        if (System.currentTimeMillis() / 1000 >= offer.payload.expiresAt ||
            ledger() != offer.ledger) throw UpdateFailure("metadata_expired_or_changed")
        verifyApk(file, offer.payload)
    }
    private fun signatures(info: PackageInfo): Set<String> {
        @Suppress("DEPRECATION")
        val certificates = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners
            else info.signatures
        if (certificates.isNullOrEmpty()) throw UpdateFailure("apk_signature_missing")
        return certificates.map {
            MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                .joinToString("") { byte -> "%02x".format(byte.toInt() and 255) }
        }.toSet()
    }
    private fun verifyApk(file: File, payload: UpdatePayload) {
        if (!file.isFile || file.length() != payload.bytes) throw UpdateFailure("apk_size_invalid")
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val chunk = ByteArray(65536)
            while (true) {
                val count = input.read(chunk)
                if (count < 0) break
                digest.update(chunk, 0, count)
            }
        }
        if (digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } != payload.sha256)
            throw UpdateFailure("apk_hash_invalid")
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES
            else PackageManager.GET_SIGNATURES
        val manager = context.packageManager
        @Suppress("DEPRECATION")
        val archive = manager.getPackageArchiveInfo(file.absolutePath, flags) ?: throw UpdateFailure("apk_invalid")
        @Suppress("DEPRECATION")
        val installed = manager.getPackageInfo(context.packageName, flags)
        @Suppress("DEPRECATION")
        val archiveCode = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        if (archive.packageName != context.packageName || archive.versionName != payload.version ||
            archiveCode != payload.versionCode.toLong() || archiveCode <= BuildConfig.VERSION_CODE ||
            signatures(archive) != signatures(installed)) throw UpdateFailure("apk_identity_invalid")
    }
    fun installUri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
