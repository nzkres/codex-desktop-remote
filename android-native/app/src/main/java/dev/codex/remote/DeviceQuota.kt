package dev.codex.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject

internal data class DeviceQuota(val thread: String, val snapshot: UsageSnapshot?, val unavailable: Boolean = false)
internal fun quotaLimit(value: DeviceQuota?): UsageLimit? = value?.snapshot?.limits?.let { rows -> rows.firstOrNull { it.id == "codex" } ?: rows.firstOrNull() }
internal fun quotaJson(snapshot: UsageSnapshot?): Any = snapshot?.let { JSONObject().put("accountKey",it.accountKey ?: JSONObject.NULL).put("limits", JSONArray(it.limits.map { limit ->
    JSONObject().put("id",limit.id).put("capturedAt",limit.capturedAt).put("planType",limit.planType ?: JSONObject.NULL).put("windows", JSONArray(limit.windows.map { window ->
        JSONObject().put("usedPercent",window.usedPercent).put("windowDurationMins",window.minutes).put("resetsAt",window.resetsAt)
    }))
})) } ?: JSONObject.NULL

/** Device scope is inside the existing server/account encrypted scope. No inferred countdown consumption. */
internal class DeviceQuotaStore(private val prefs: SharedPreferences) {
    var values by mutableStateOf(run {
        val rows = JSONObject(prefs.getString("values","{}").orEmpty()); require(rows.length() <= 100)
        rows.keys().asSequence().filter { it.isNotEmpty() && validTerminalId(it) }.mapNotNull { id ->
            val row = rows.optJSONObject(id) ?: return@mapNotNull null
            id to DeviceQuota(row.optString("thread"),parseUsageSnapshot(row.optJSONObject("snapshot")),true)
        }.toMap()
    }); private set
    fun update(device: String, thread: String, snapshot: UsageSnapshot?) {
        require(device.isNotEmpty() && validTerminalId(device) && thread.isNotBlank() && thread.length <= 160)
        val old = values[device]
        val incoming = snapshot?.limits?.maxOfOrNull { it.capturedAt } ?: 0L
        val previous = old?.snapshot?.limits?.maxOfOrNull { it.capturedAt } ?: 0L
        if(incoming > System.currentTimeMillis()+60000L) { failed(device); return }
        // A late response from an older task cannot overwrite a newer quota observation.
        if (snapshot!=null && incoming < previous) return
        if(incoming==previous && snapshot==old?.snapshot && old?.unavailable==false)return
        val next = values + (device to DeviceQuota(thread, snapshot ?: old?.snapshot, snapshot == null))
        if(next == values) return
        require(next.size <= 100)
        val raw = JSONObject().apply { next.forEach { (id,q) -> put(id,JSONObject().put("thread",q.thread).put("snapshot",quotaJson(q.snapshot))) } }.toString()
        require(raw.toByteArray().size <= 262144)
        check(prefs.edit().putString("values",raw).commit()) { "额度缓存未能加密保存。" }; values=next
    }
    fun failed(device: String) { values[device]?.let { values=values+(device to it.copy(unavailable=true)) } }
    companion object {
        fun open(context: Context, scope: String): DeviceQuotaStore {
            require(scope.matches(Regex("[a-f0-9]{64}")))
            return DeviceQuotaStore(EncryptedSharedPreferences.create("device-quota-$scope",MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM))
        }
    }
}

@Composable internal fun DeviceQuotaLight(value: DeviceQuota?) {
    QuotaUsageDetails(value?.snapshot,value?.unavailable==true)
}
