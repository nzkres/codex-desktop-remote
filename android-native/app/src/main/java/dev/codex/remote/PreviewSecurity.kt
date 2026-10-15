package dev.codex.remote

import org.json.JSONObject
import java.net.URI
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

internal class PreviewFailure(val code: String) : Exception(code)
internal data class PreviewLedger(val sequence: Long = 0, val binding: String = "")
internal data class PreviewDocument(val id: String, val title: String, val sequence: Long,
    val html: String, val binding: String, val expiresAt: Long)
internal const val PREVIEW_MAX_ENVELOPE = 512 * 1024
internal const val PREVIEW_MAX_HTML = 256 * 1024
internal fun previewSha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it.toInt() and 255) }
private fun badPreview(code: String): Nothing = throw PreviewFailure(code)
private fun previewObject(bytes: ByteArray): JSONObject {
    val text = bytes.toString(Charsets.UTF_8)
    if (!text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) badPreview("preview_encoding_invalid")
    var depth = 0; var quoted = false; var escaped = false
    for (c in text) {
        if (quoted) {
            if (escaped) escaped = false else if (c == '\\') escaped = true else if (c == '"') quoted = false
        } else when(c) {
            '"' -> quoted = true
            '{', '[' -> { depth++; if (depth > 32) badPreview("preview_document_invalid") }
            '}', ']' -> { depth--; if (depth < 0) badPreview("preview_document_invalid") }
        }
    }
    rejectDuplicateKeys(text)
    return JSONObject(text)
}
private fun JSONObject.only(vararg names: String) {
    if (keys().asSequence().toSet() != names.toSet()) badPreview("preview_fields_invalid")
}
private fun JSONObject.number(name: String): Long = when (val n = opt(name)) {
    is Int -> n.toLong(); is Long -> n; else -> badPreview("preview_fields_invalid")
}
private fun samePreviewOrigin(a: URI, b: URI): Boolean = a.scheme == "https" && b.scheme == "https" &&
    a.host.equals(b.host, ignoreCase = true) && (if(a.port == -1) 443 else a.port) == (if(b.port == -1) 443 else b.port)

/** The installed OTA trust pins the preview origin too. No arbitrary URL/token forwarding. */
internal fun previewSource(base: String, trustBytes: ByteArray): URI {
    try {
        val trust = previewObject(trustBytes)
        trust.only("schema", "product", "channel", "keys", "sources")
        if (trust.number("schema") != 1L || trust.getString("product") != "codex-desktop-remote" ||
            trust.getJSONObject("keys").length() == 0) badPreview("preview_unconfigured")
        val channel = trust.getString("channel")
        if (channel !in setOf("test", "stable")) badPreview("preview_unconfigured")
        val source = checkedUri(trust.getJSONObject("sources").getString("android"))
        if (source.path != "/desktop-remote-updates/$channel/android/latest.json") badPreview("preview_source_mismatch")
        val current = URI(base)
        if (current.rawUserInfo != null || current.rawQuery != null || current.rawFragment != null ||
            current.path !in listOf("", "/") || !samePreviewOrigin(current, source)) badPreview("preview_source_mismatch")
        return URI(source.scheme, null, source.host, source.port, "/desktop-remote-previews/$channel/latest.json", null, null)
    } catch (e: PreviewFailure) { throw e }
    catch (_: Exception) { badPreview("preview_unconfigured") }
}

internal fun verifyPreview(raw: ByteArray, trust: ByteArray, source: URI, ledger: PreviewLedger, now: Long): PreviewDocument {
    try {
        if (raw.isEmpty() || raw.size > PREVIEW_MAX_ENVELOPE) badPreview("preview_size_invalid")
        if (previewSource(source.resolve("/").toString(), trust) != source) badPreview("preview_source_mismatch")
        val envelope = previewObject(raw)
        envelope.only("schema", "keyId", "payload", "signature")
        if (envelope.number("schema") != 1L) badPreview("preview_fields_invalid")
        fun decode(name: String): ByteArray {
            val text = envelope.getString(name)
            val bytes = Base64.getDecoder().decode(text)
            if (Base64.getEncoder().encodeToString(bytes) != text) badPreview("preview_encoding_invalid")
            return bytes
        }
        val payload = decode("payload"); val signed = decode("signature")
        if (payload.isEmpty() || payload.size > 384 * 1024) badPreview("preview_size_invalid")
        val keys = previewObject(trust).getJSONObject("keys")
        val keyId = envelope.getString("keyId")
        if (!keys.has(keyId)) badPreview("preview_key_unknown")
        val pem = keys.getString(keyId).replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "").replace(Regex("\\s"), "")
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pem))) as RSAPublicKey
        if (key.modulus.bitLength() < 3072 || !Signature.getInstance("SHA256withRSA").run {
                initVerify(key); update(payload); verify(signed)
            }) badPreview("preview_signature_invalid")
        val p = previewObject(payload)
        p.only("schema", "product", "source", "renderer", "sequence", "id", "title", "issuedAt", "expiresAt", "htmlSha256", "html")
        if (p.number("schema") != 1L || p.getString("product") != "desktop-remote-ui-preview" ||
            p.getString("source") != source.toString()) badPreview("preview_target_invalid")
        if (p.number("renderer") != 1L) badPreview("preview_renderer_unsupported")
        val id = p.getString("id"); val title = p.getString("title")
        if (!id.matches(Regex("[a-z0-9-]{1,64}")) || title.isBlank() || title.length > 100) badPreview("preview_fields_invalid")
        val issued = p.number("issuedAt"); val expires = p.number("expiresAt")
        if (issued > now + 300 || expires <= now || expires <= issued || expires - issued > 31L * 86400) badPreview("preview_expired")
        val sequence = p.number("sequence"); val binding = previewSha(payload)
        if (sequence <= 0 || sequence < ledger.sequence) badPreview("preview_replayed")
        if (sequence == ledger.sequence && binding != ledger.binding) badPreview("preview_sequence_conflict")
        val htmlText = p.getString("html"); val htmlBytes = Base64.getDecoder().decode(htmlText)
        if (htmlBytes.isEmpty() || htmlBytes.size > PREVIEW_MAX_HTML) badPreview("preview_size_invalid")
        if (Base64.getEncoder().encodeToString(htmlBytes) != htmlText || previewSha(htmlBytes) != p.getString("htmlSha256")) badPreview("preview_hash_invalid")
        val html = htmlBytes.toString(Charsets.UTF_8)
        if (!html.toByteArray(Charsets.UTF_8).contentEquals(htmlBytes)) badPreview("preview_encoding_invalid")
        return PreviewDocument(id, title, sequence, html, binding, expires)
    } catch (e: PreviewFailure) { throw e }
    catch (_: Exception) { badPreview("preview_document_invalid") }
}

/** Signed content still has no native bridge, network, forms, frames or local-file access. */
internal fun isolatedPreviewHtml(fragment: String): String = """<!doctype html><html><head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; img-src data:; font-src data:; connect-src 'none'; frame-src 'none'; worker-src 'none'; object-src 'none'; media-src 'none'; form-action 'none'; base-uri 'none'">
<style>html,body{margin:0;padding:0;background:#151516;color:#e1e8f0;font-family:system-ui,sans-serif}*{box-sizing:border-box}</style>
</head><body>$fragment</body></html>"""
