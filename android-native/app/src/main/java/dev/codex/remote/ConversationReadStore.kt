package dev.codex.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONObject
import java.security.MessageDigest

internal data class ReadWatermark(val revision: String, val seen: String = "", val at: Long = 0) {
    val unread get() = revision.isNotEmpty() && revision != seen
    fun observe(next: String, time: Long) = if (time < at || !next.matches(Regex("[a-f0-9]{64}"))) this else copy(revision=next,at=time)
    fun acknowledge(expected: String, visible: Boolean, foreground: Boolean) =
        if (visible && foreground && expected == revision) copy(seen=expected) else this
}
/** Local encrypted reading positions, scoped by login/server and then device/thread. No transcript content. */
internal class ConversationReadStore(private val prefs: SharedPreferences) {
    var rows by mutableStateOf(run {
        val raw=JSONObject(prefs.getString("rows","{}").orEmpty());require(raw.length()<=2000)
        raw.keys().asSequence().associateWith { key -> val v=raw.getJSONObject(key)
            ReadWatermark(v.optString("revision"),v.optString("seen"),v.optLong("at")) }
    }); private set
    private fun key(device:String,thread:String)=MessageDigest.getInstance("SHA-256").digest((device+"\n"+thread).toByteArray()).joinToString("") { "%02x".format(it) }
    fun value(device:String,thread:String)=rows[key(device,thread)] ?: ReadWatermark("")
    private fun save(id:String,value:ReadWatermark) {
        if(rows[id]==value)return
        val next=(rows+ (id to value)).entries.sortedByDescending { it.value.at }.take(2000).associate { it.toPair() }
        if(rows[id]?.revision==value.revision && rows[id]?.seen==value.seen) { rows=next;return }
        val json=JSONObject().apply { next.forEach { (k,v)->put(k,JSONObject().put("revision",v.revision).put("seen",v.seen).put("at",v.at)) } }
        check(prefs.edit().putString("rows",json.toString()).commit()) { "阅读位置保存失败" };rows=next
    }
    fun observe(device:String,thread:String,revision:String,at:Long) { if(revision.matches(Regex("[a-f0-9]{64}"))) save(key(device,thread),value(device,thread).observe(revision,at)) }
    fun acknowledge(device:String,thread:String,revision:String) { if(value(device,thread).revision==revision) save(key(device,thread),value(device,thread).acknowledge(revision,true,true)) }
    companion object {
        fun open(context:Context,scope:String):ConversationReadStore {
            require(scope.matches(Regex("[a-f0-9]{64}")))
            return ConversationReadStore(EncryptedSharedPreferences.create("conversation-read-$scope",MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM))
        }
    }
}
