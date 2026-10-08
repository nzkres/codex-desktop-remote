package dev.codex.remote

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal class UpdateFailure(val code: String) : Exception(code)

internal data class UpdatePayload(
    val version: String, val versionCode: Int, val sequence: Long, val url: URI,
    val bytes: Long, val sha256: String, val notes: String, val expiresAt: Long,
    val binding: String, val channel: String, val minSdk: Int,
)

internal data class UpdateLedger(
    val sequence: Long = 0, val binding: String = "", val versions: Map<String, String> = emptyMap(),
)

private val versionPattern = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-(alpha)\\.(0|[1-9][0-9]*))?")
private val shaPattern = Regex("[a-f0-9]{64}")

private fun fail(code: String): Nothing = throw UpdateFailure(code)
private fun JSONObject.exact(vararg names: String) {
    if (keys().asSequence().toSet() != names.toSet()) fail("metadata_fields_invalid")
}
private fun JSONObject.integer(name: String): Long = when (val value = opt(name)) {
    is Int -> value.toLong()
    is Long -> value
    else -> fail("metadata_fields_invalid")
}
private fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 255) }

/** org.json implementations differ in duplicate-key behavior; reject duplicates before parsing. */
internal fun rejectDuplicateKeys(json: String) {
    class Scan {
        var at = 0
        fun space() { while (at < json.length && json[at].isWhitespace()) at++ }
        fun string(): String {
            if (at >= json.length || json[at++] != '"') fail("metadata_json_invalid")
            val start = at - 1
            var escaped = false
            while (at < json.length) {
                val c = json[at++]
                if (escaped) escaped = false
                else if (c == '\\') escaped = true
                else if (c == '"') {
                    return JSONObject("{\"v\":" + json.substring(start, at) + "}").getString("v")
                }
            }
            fail("metadata_json_invalid")
        }
        fun value() {
            space()
            if (at >= json.length) fail("metadata_json_invalid")
            when (json[at]) {
                '{' -> {
                    at++; space()
                    val keys = HashSet<String>()
                    if (at < json.length && json[at] == '}') { at++; return }
                    while (true) {
                        space(); val key = string()
                        if (!keys.add(key)) fail("metadata_duplicate_key")
                        space(); if (at >= json.length || json[at++] != ':') fail("metadata_json_invalid")
                        value(); space()
                        if (at >= json.length) fail("metadata_json_invalid")
                        val delimiter = json[at++]
                        if (delimiter == '}') return
                        if (delimiter != ',') fail("metadata_json_invalid")
                    }
                }
                '[' -> {
                    at++; space()
                    if (at < json.length && json[at] == ']') { at++; return }
                    while (true) {
                        value(); space()
                        if (at >= json.length) fail("metadata_json_invalid")
                        val delimiter = json[at++]
                        if (delimiter == ']') return
                        if (delimiter != ',') fail("metadata_json_invalid")
                    }
                }
                '"' -> { string() }
                else -> {
                    val start = at
                    while (at < json.length && json[at] !in ",]} \r\n\t") at++
                    if (at == start) fail("metadata_json_invalid")
                }
            }
        }
    }
    val scan = Scan()
    scan.value(); scan.space()
    if (scan.at != json.length) fail("metadata_json_invalid")
}

private fun parseObject(bytes: ByteArray): JSONObject {
    val text = bytes.toString(Charsets.UTF_8)
    if (!text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) fail("metadata_encoding_invalid")
    try {
        rejectDuplicateKeys(text)
        return JSONObject(text)
    } catch (error: UpdateFailure) { throw error }
    catch (_: Exception) { fail("metadata_json_invalid") }
}

internal fun verifyUpdate(
    envelopeBytes: ByteArray, trustBytes: ByteArray, installedVersion: String,
    installedCode: Int, deviceSdk: Int, ledger: UpdateLedger, now: Long,
): Pair<UpdatePayload, UpdateLedger> {
    if (envelopeBytes.isEmpty() || envelopeBytes.size > 65536) fail("metadata_size_invalid")
    val trust = parseObject(trustBytes)
    trust.exact("schema", "product", "channel", "keys", "sources")
    if (trust.integer("schema") != 1L || trust.optString("product") != "codex-desktop-remote") fail("trust_invalid")
    val channel = trust.optString("channel")
    if (channel !in setOf("test", "stable")) fail("trust_invalid")
    val keys = trust.getJSONObject("keys")
    val sources = trust.getJSONObject("sources")
    if (keys.length() == 0 || !sources.has("android")) fail("ota_unconfigured")
    val source = checkedUri(sources.getString("android"))
    val envelope = parseObject(envelopeBytes)
    envelope.exact("schema", "keyId", "payload", "signature")
    if (envelope.integer("schema") != 1L) fail("metadata_fields_invalid")
    val keyId = envelope.getString("keyId")
    if (!keys.has(keyId) || keys.getString(keyId).isBlank()) fail("metadata_key_unknown")
    val raw = try { Base64.getDecoder().decode(envelope.getString("payload")) } catch (_: Exception) { fail("metadata_encoding_invalid") }
    val signed = try { Base64.getDecoder().decode(envelope.getString("signature")) } catch (_: Exception) { fail("metadata_encoding_invalid") }
    if (raw.isEmpty() || raw.size > 65536 ||
        Base64.getEncoder().encodeToString(raw) != envelope.getString("payload") ||
        Base64.getEncoder().encodeToString(signed) != envelope.getString("signature")) fail("metadata_encoding_invalid")
    val publicKey = try {
        val pem = keys.getString(keyId).replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "").replace(Regex("\\s"), "")
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pem))) as RSAPublicKey
    } catch (_: Exception) { fail("metadata_key_invalid") }
    if (publicKey.modulus.bitLength() < 3072) fail("metadata_key_invalid")
    val verified = try {
        Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey); update(raw); verify(signed)
        }
    } catch (_: Exception) { false }
    if (!verified) fail("metadata_signature_invalid")
    val payload = parseObject(raw)
    payload.exact("schema", "product", "component", "platform", "channel", "version", "sequence",
        "issuedAt", "expiresAt", "url", "bytes", "sha256", "notes", "compatibility",
        "dataSchema", "rollbackCompatible", "versionCode", "packageId", "minSdk")
    if (payload.integer("schema") != 1L || payload.optString("product") != "codex-desktop-remote" ||
        payload.optString("component") != "android" || payload.optString("platform") != "android" ||
        payload.optString("channel") != channel || payload.optString("packageId") != "dev.codex.desktopremote.lite" ||
        payload.integer("dataSchema") != 1L || payload.opt("rollbackCompatible") != true) fail("metadata_target_invalid")
    val version = payload.getString("version")
    if (!versionPattern.matches(version) || (channel == "stable" && '-' in version)) fail("metadata_version_invalid")
    val installed = versionPattern.matchEntire(installedVersion) ?: fail("installed_version_invalid")
    val candidate = versionPattern.matchEntire(version) ?: fail("metadata_version_invalid")
    fun rank(match: MatchResult): List<Long> = listOf(match.groupValues[1].toLong(), match.groupValues[2].toLong(),
        match.groupValues[3].toLong(), if (match.groupValues[4] == "alpha") 0L else 1L,
        match.groupValues[5].toLongOrNull() ?: 0L)
    val oldRank = rank(installed); val newRank = rank(candidate)
    val newer = newRank.zip(oldRank).firstOrNull { it.first != it.second }?.let { it.first > it.second } ?: false
    val codeValue = payload.integer("versionCode")
    if (codeValue <= 0 || codeValue > Int.MAX_VALUE) fail("metadata_fields_invalid")
    val code = codeValue.toInt()
    val minSdk = payload.integer("minSdk")
    if (minSdk < 26 || minSdk > deviceSdk) fail("metadata_incompatible")
    val compatible = payload.getJSONArray("compatibility")
    if (compatible.length() == 0 || (0 until compatible.length()).any { compatible.get(it) !is String || compatible.getString(it).isBlank() } ||
        (0 until compatible.length()).none { compatible.getString(it) == "desktop-remote-v1" }) fail("metadata_incompatible")
    val issued = payload.integer("issuedAt"); val expires = payload.integer("expiresAt")
    if (issued > now + 300 || expires <= now || expires <= issued || expires - issued > 31L * 86400) fail("metadata_expired")
    val sequence = payload.integer("sequence")
    if (sequence <= 0 || sequence < ledger.sequence) fail("metadata_replayed")
    val artifactHash = payload.getString("sha256")
    val bytes = payload.integer("bytes")
    val notes = payload.getString("notes")
    if (!shaPattern.matches(artifactHash) || bytes !in 1..600L * 1024 * 1024 || notes.length > 8192) fail("metadata_fields_invalid")
    val url = checkedUri(payload.getString("url"))
    if (url.scheme != source.scheme || url.host != source.host || url.port != source.port ||
        !url.path.startsWith(source.path.substringBeforeLast('/') + "/")) fail("metadata_url_invalid")
    val binding = sha(raw)
    if (sequence == ledger.sequence && binding != ledger.binding) fail("metadata_sequence_conflict")
    if (ledger.versions[version]?.let { it != artifactHash } == true) fail("metadata_version_conflict")
    if (code <= installedCode || !newer) fail("update_not_newer")
    val updated = UpdateLedger(sequence, binding, ledger.versions + (version to artifactHash))
    return UpdatePayload(version, code, sequence, url, bytes, artifactHash, notes, expires, binding, channel, minSdk.toInt()) to updated
}

internal fun checkedUri(text: String): URI {
    val uri = try { URI(text) } catch (_: Exception) { fail("metadata_url_invalid") }
    if (uri.scheme != "https" || uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
        uri.rawQuery != null || uri.rawFragment != null || uri.path.isNullOrBlank() ||
        uri.path.contains("..") || uri.path.contains("\\") || uri.path.contains("%") ||
        uri.path.contains("//")) fail("metadata_url_invalid")
    return uri
}
