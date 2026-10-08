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

internal data class UpdateOffer(val payload: UpdatePayload, val ledger: UpdateLedger,
                                val loginBinding: String)

/** A request credential lives only in memory; never include its token in an exception or toString. */
internal class UpdateIdentity internal constructor(
    private val loginUrl: String, val account: String, private val token: String,
) {
    fun matches(url: String, principal: String, loginToken: String): Boolean =
        loginUrl == url && account == principal && token == loginToken

    fun binding(): String = MessageDigest.getInstance("SHA-256")
        .digest("$loginUrl\n$account\n$token".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun authorize(connection: HttpURLConnection) {
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("X-Desktop-Remote-Principal", "account")
    }

    override fun toString(): String = "UpdateIdentity(redacted)"
}

/** Fail before opening a connection, including on different ports of the same host. */
internal fun requireUpdateScope(loginUrl: String, account: String, token: String,
                                source: URI, request: URI, packageRequest: Boolean = false): UpdateIdentity {
    if (loginUrl.isBlank() || account.isBlank() || token.isBlank()) throw UpdateFailure("ota_login_required")
    if (!account.matches(Regex("[A-Za-z0-9_.-]{1,64}")) || !token.matches(Regex("[a-f0-9]{64}")))
        throw UpdateFailure("ota_login_required")
    val relay = try { URI(loginUrl) } catch (_: Exception) { throw UpdateFailure("ota_source_mismatch") }
    if (relay.scheme != "https" || relay.host.isNullOrBlank() || relay.rawUserInfo != null ||
        relay.rawQuery != null || relay.rawFragment != null || relay.port !in -1..65535 || relay.port == 0 ||
        relay.rawPath !in setOf("", "/"))
        throw UpdateFailure("ota_source_mismatch")
    val trusted = try { checkedUri(source.toString()) } catch (_: UpdateFailure) {
        throw UpdateFailure("ota_source_mismatch")
    }
    val target = try { checkedUri(request.toString()) } catch (_: UpdateFailure) {
        throw UpdateFailure("ota_source_mismatch")
    }
    fun port(uri: URI): Int = if (uri.port == -1) 443 else uri.port
    fun sameOrigin(a: URI, b: URI): Boolean = a.scheme == b.scheme &&
        a.host.equals(b.host, ignoreCase = true) && port(a) == port(b)
    val sourcePath = Regex("/desktop-remote-updates/(test|stable)/android/latest\\.json")
    val directory = trusted.path.substringBeforeLast('/') + "/"
    val packagePath = Regex(Regex.escape(directory) +
        "android-[0-9]{1,9}\\.[0-9]{1,9}\\.[0-9]{1,9}(?:-alpha\\.[0-9]{1,9})?\\.apk")
    if (trusted.rawPath.contains('%') || target.rawPath.contains('%') ||
        trusted.port !in -1..65535 || target.port !in -1..65535 ||
        trusted.port == 0 || target.port == 0 ||
        !sourcePath.matches(trusted.path) ||
        !sameOrigin(relay, trusted) || !sameOrigin(trusted, target) ||
        (if (packageRequest) !packagePath.matches(target.path) else target.path != trusted.path))
        throw UpdateFailure("ota_source_mismatch")
    return UpdateIdentity(loginUrl, account, token)
}

internal data class PendingInstall(
    val oldVersion: String, val oldCode: Int, val version: String, val versionCode: Int,
    val sha256: String, val sequence: Long, val binding: String,
)

internal enum class PendingInstallState { RETRY, TASK_PROOF_REQUIRED, VERIFIED, NEEDS_REVIEW }

/** A system installer handoff is pending until both the installed package and live task are checked. */
internal fun assessPendingInstall(
    pending: PendingInstall, ledger: UpdateLedger, installedVersion: String, installedCode: Int,
    installedApkSha256: String?, freshTaskProof: Boolean,
): PendingInstallState {
    if (ledger.sequence != pending.sequence || ledger.binding != pending.binding ||
        ledger.versions[pending.version] != pending.sha256) return PendingInstallState.NEEDS_REVIEW
    if (installedVersion == pending.oldVersion && installedCode == pending.oldCode)
        return PendingInstallState.RETRY
    if (installedVersion != pending.version || installedCode != pending.versionCode ||
        installedApkSha256 != pending.sha256) return PendingInstallState.NEEDS_REVIEW
    return if (freshTaskProof) PendingInstallState.VERIFIED else PendingInstallState.TASK_PROOF_REQUIRED
}

internal fun hasDownloadSpace(usableBytes: Long, packageBytes: Long): Boolean =
    packageBytes in 1..600L * 1024 * 1024 && usableBytes >= packageBytes * 2 + 64L * 1024 * 1024

internal class UpdateClient(private val context: Context) {
    private val prefs = context.getSharedPreferences("android-update-ledger", Context.MODE_PRIVATE)
    private val credentials = Credentials(context)
    private fun trustBytes(): ByteArray = context.assets.open("update-trust.json").use { it.readBytes() }
    private fun source(): URI {
        val trust = JSONObject(trustBytes().toString(Charsets.UTF_8))
        val keys = trust.getJSONObject("keys")
        val sources = trust.getJSONObject("sources")
        if (keys.length() == 0 || !sources.has("android")) throw UpdateFailure("ota_unconfigured")
        return try { checkedUri(sources.getString("android")) }
            catch (_: UpdateFailure) { throw UpdateFailure("ota_source_mismatch") }
    }
    private fun login(source: URI, request: URI = source, packageRequest: Boolean = false): UpdateIdentity {
        val url = credentials.url
        val account = credentials.account
        val token = credentials.token
        val identity = requireUpdateScope(url, account, token, source, request, packageRequest)
        assertCurrent(identity)
        return identity
    }
    private fun assertCurrent(identity: UpdateIdentity) {
        if (!identity.matches(credentials.url, credentials.account, credentials.token))
            throw UpdateFailure("ota_login_changed")
    }
    fun requireConfigured() {
        val trusted = source()
        login(trusted)
    }
    private fun ledger(): UpdateLedger {
        val raw = prefs.getString("ledger", null) ?: return UpdateLedger()
        try {
            val data = JSONObject(raw)
            if (data.keys().asSequence().toSet() != setOf("sequence", "binding", "versions")) throw Exception()
            val versions = data.getJSONObject("versions")
            val map = versions.keys().asSequence().associateWith { versions.getString(it) }
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
    fun hasPendingInstall(): Boolean = prefs.contains("pending-install")
    private fun pending(): PendingInstall? {
        val raw = prefs.getString("pending-install", null) ?: return null
        try {
            val value = JSONObject(raw)
            if (value.keys().asSequence().toSet() != setOf("oldVersion", "oldCode", "version", "versionCode", "sha256", "sequence", "binding"))
                throw Exception()
            fun int(name: String): Int {
                val number = value.opt(name)
                if (number !is Int || number <= 0) throw Exception()
                return number
            }
            val sequenceValue = value.opt("sequence")
            if (sequenceValue !is Int && sequenceValue !is Long) throw Exception()
            val result = PendingInstall(value.getString("oldVersion"), int("oldCode"),
                value.getString("version"), int("versionCode"), value.getString("sha256"),
                (sequenceValue as Number).toLong(), value.getString("binding"))
            if (!result.oldVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-alpha\\.[0-9]+)?")) ||
                !result.version.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-alpha\\.[0-9]+)?")) ||
                result.versionCode <= result.oldCode || result.sequence <= 0 ||
                !result.sha256.matches(Regex("[a-f0-9]{64}")) ||
                !result.binding.matches(Regex("[a-f0-9]{64}"))) throw Exception()
            return result
        } catch (_: Exception) { throw UpdateFailure("pending_install_invalid") }
    }
    private fun savePending(value: PendingInstall) {
        val raw = JSONObject().put("oldVersion", value.oldVersion).put("oldCode", value.oldCode)
            .put("version", value.version).put("versionCode", value.versionCode)
            .put("sha256", value.sha256).put("sequence", value.sequence)
            .put("binding", value.binding).toString()
        if (!prefs.edit().putString("pending-install", raw).commit()) throw UpdateFailure("pending_install_write_failed")
    }
    /** Call immediately before ACTION_INSTALL_PACKAGE; a cancelled dialog leaves this receipt for retry. */
    fun recordPendingInstall(file: File, offer: UpdateOffer) {
        verifyBeforeInstall(file, offer)
        savePending(PendingInstall(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
            offer.payload.version, offer.payload.versionCode, offer.payload.sha256,
            offer.ledger.sequence, offer.ledger.binding))
    }
    /** Caller supplies a newly fetched connector proof; no cached task status is accepted here. */
    fun reconcilePendingInstall(targetId: String?, busy: Boolean, pendingSend: Boolean,
                                freshProof: ConnectorUpdateProof?): PendingInstallState? {
        val receipt = pending() ?: return null
        val installed = try { context.packageManager.getPackageInfo(context.packageName, 0) }
            catch (_: Exception) { return PendingInstallState.NEEDS_REVIEW }
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode.toInt() else installed.versionCode
        val version = installed.versionName ?: return PendingInstallState.NEEDS_REVIEW
        val apkHash = if (version == receipt.version && code == receipt.versionCode) {
            try {
                val file = File(context.applicationInfo.sourceDir)
                if (!file.isFile) return PendingInstallState.NEEDS_REVIEW
                val sha = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val chunk = ByteArray(65536)
                    while (true) {
                        val count = input.read(chunk)
                        if (count < 0) break
                        sha.update(chunk, 0, count)
                    }
                }
                sha.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            } catch (_: Exception) { return PendingInstallState.NEEDS_REVIEW }
        } else null
        val taskReady = targetId != null && updateMayInstall(targetId, busy, pendingSend, freshProof)
        val result = assessPendingInstall(receipt, ledger(), version, code, apkHash, taskReady)
        if (result == PendingInstallState.VERIFIED && !prefs.edit().remove("pending-install").commit())
            throw UpdateFailure("pending_install_write_failed")
        return result
    }
    private fun connection(uri: URI, source: URI, identity: UpdateIdentity, accept: String,
                           packageRequest: Boolean = false): HttpURLConnection {
        requireUpdateScope(credentials.url, credentials.account, credentials.token, source, uri, packageRequest)
        assertCurrent(identity)
        val conn = uri.toURL().openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.requestMethod = "GET"
        conn.connectTimeout = 10_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("Accept", accept)
        identity.authorize(conn)
        return conn
    }
    private fun fetch(source: URI, identity: UpdateIdentity): ByteArray {
        val conn = connection(source, source, identity, "application/json")
        try {
            when (conn.responseCode) {
                401 -> throw UpdateFailure("ota_login_expired")
                403 -> throw UpdateFailure("ota_access_denied")
                200 -> Unit
                else -> throw UpdateFailure("metadata_http_failed")
            }
            conn.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    if (output.size() + count > 65536) throw UpdateFailure("metadata_size_invalid")
                    output.write(chunk, 0, count)
                }
                assertCurrent(identity)
                return output.toByteArray()
            }
        } finally { conn.disconnect() }
    }
    suspend fun check(): UpdateOffer = withContext(Dispatchers.IO) {
        val trusted = source()
        val identity = login(trusted)
        val verified = verifyUpdate(fetch(trusted, identity), trustBytes(), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE,
            Build.VERSION.SDK_INT, ledger(), System.currentTimeMillis() / 1000)
        requireUpdateScope(credentials.url, credentials.account, credentials.token, trusted, verified.first.url, true)
        assertCurrent(identity)
        saveLedger(verified.second)
        UpdateOffer(verified.first, verified.second, identity.binding())
    }
    suspend fun download(offer: UpdateOffer, progress: (Long, Long) -> Unit): File = withContext(Dispatchers.IO) {
        val payload = offer.payload
        val trusted = source()
        val identity = login(trusted, payload.url, true)
        if (identity.binding() != offer.loginBinding) throw UpdateFailure("ota_login_changed")
        if (System.currentTimeMillis() / 1000 >= payload.expiresAt) throw UpdateFailure("metadata_expired")
        if (ledger() != offer.ledger) throw UpdateFailure("ledger_changed")
        val directory = File(context.cacheDir, "updates")
        if (!directory.isDirectory && !directory.mkdirs()) throw UpdateFailure("download_storage_failed")
        if (!hasDownloadSpace(directory.usableSpace, payload.bytes)) throw UpdateFailure("download_space_insufficient")
        val target = File(directory, "update-${payload.sha256}.apk")
        val partial = File(directory, "update-${payload.sha256}.partial")
        if (partial.exists() && !partial.delete()) throw UpdateFailure("download_storage_failed")
        val conn = connection(payload.url, trusted, identity, "application/vnd.android.package-archive", true)
        try {
            when (conn.responseCode) {
                401 -> throw UpdateFailure("ota_login_expired")
                403 -> throw UpdateFailure("ota_access_denied")
                200 -> Unit
                else -> throw UpdateFailure("download_http_failed")
            }
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
            assertCurrent(identity)
            if (target.exists() && !target.delete()) throw UpdateFailure("download_storage_failed")
            if (!partial.renameTo(target)) throw UpdateFailure("download_storage_failed")
            verifyApk(target, payload)
            assertCurrent(identity)
            target
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally { conn.disconnect() }
    }
    fun verifyBeforeInstall(file: File, offer: UpdateOffer) {
        val trusted = source()
        val identity = login(trusted, offer.payload.url, true)
        if (identity.binding() != offer.loginBinding) throw UpdateFailure("ota_login_changed")
        if (System.currentTimeMillis() / 1000 >= offer.payload.expiresAt ||
            ledger() != offer.ledger) throw UpdateFailure("metadata_expired_or_changed")
        verifyApk(file, offer.payload)
        assertCurrent(identity)
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
        if (archive.applicationInfo?.minSdkVersion != payload.minSdk || archive.packageName != context.packageName || archive.versionName != payload.version ||
            archiveCode != payload.versionCode.toLong() || archiveCode <= BuildConfig.VERSION_CODE ||
            signatures(archive) != signatures(installed)) throw UpdateFailure("apk_identity_invalid")
    }
    fun installUri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
