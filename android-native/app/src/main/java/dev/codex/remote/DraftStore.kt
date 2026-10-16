package dev.codex.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject
import java.security.MessageDigest

internal data class DraftRecord(val text: String, val revision: Long, val nonce: String)

/** A known account survives re-login. Legacy installs have no account identity: isolate their
 * drafts to that login, never guess that the next username owns them. No token is used as a key. */
internal fun draftScope(endpoint: String, account: String, token: String): String {
    val principal = if (account.isNotBlank()) "account:${account.trim()}" else "login:$token"
    val input = listOf(endpoint.trim().trimEnd('/'), principal).joinToString("") { "${it.length}:$it" }
    return MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}

/** Preview only. The saved/sent string is never trimmed or flattened. Bound scanning for long drafts. */
internal fun draftPreview(text: String): String = text.take(512)
    .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }
    .replace(Regex("[\\s\\p{Z}]+"), " ").trim()
    .let { if (it.codePointCount(0, it.length) <= 80) it else it.substring(0, it.offsetByCodePoints(0, 80)) + "…" }

/** Private encrypted on-device drafts. apply() updates memory immediately and queues the disk write;
 * Android flushes queued SharedPreferences writes during normal activity lifecycle transitions. */
class DraftStore internal constructor(private val prefs: SharedPreferences) {
    private fun decode(raw: String?): DraftRecord? = raw?.let { runCatching {
        val value = JSONObject(it)
        DraftRecord(value.getString("text"), value.getLong("revision"), value.getString("nonce"))
    }.getOrNull() }
    private var records by mutableStateOf(prefs.all.mapNotNull { (id, raw) -> decode(raw as? String)?.let { id to it } }.toMap())
    private var sequence = records.values.maxOfOrNull { it.revision } ?: 0L

    internal fun read(id: String): DraftRecord? = records[id]
    internal fun latest(allowedIds: Set<String>): String? = records.entries
        .filter { it.key in allowedIds && it.value.text.isNotBlank() }.maxByOrNull { it.value.revision }?.key

    internal fun write(id: String, text: String, durable: Boolean = false) {
        if (records[id]?.text == text || text.isEmpty() && id !in records) return
        sequence = maxOf(sequence + 1, System.currentTimeMillis())
        val record = DraftRecord(text, sequence, java.util.UUID.randomUUID().toString())
        val editor = prefs.edit()
        if (text.isEmpty()) editor.remove(id)
        else editor.putString(id, JSONObject().put("text", text).put("revision", record.revision).put("nonce", record.nonce).toString())
        if(durable)check(editor.commit()) { "无法保存新对话草稿，请保留创建请求重试核对。" } else editor.apply()
        records = if (text.isEmpty()) records - id else records + (id to record)
    }

    /** A delayed receipt must not erase a replacement draft, even if it contains the same text. */
    internal fun clearIfUnchanged(id: String, sent: DraftRecord?) {
        if (sent == null || decode(prefs.getString(id, null)) != sent) return
        prefs.edit().remove(id).apply()
        records = records - id
    }

    companion object {
        internal fun open(context: Context, scope: String): DraftStore {
            require(scope.matches(Regex("[a-f0-9]{64}")))
            return DraftStore(EncryptedSharedPreferences.create("conversation-drafts-$scope",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM))
        }
    }
}
