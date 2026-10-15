package dev.codex.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject

internal data class DeviceQuota(val thread: String, val snapshot: UsageSnapshot?, val unavailable: Boolean = false)
internal fun quotaLimit(value: DeviceQuota?): UsageLimit? = value?.snapshot?.limits?.let { rows -> rows.firstOrNull { it.id == "codex" } ?: rows.firstOrNull() }
internal fun quotaJson(snapshot: UsageSnapshot?): Any = snapshot?.let { JSONObject().put("limits", JSONArray(it.limits.map { limit ->
    JSONObject().put("id",limit.id).put("capturedAt",limit.capturedAt).put("windows", JSONArray(limit.windows.map { window ->
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
        if (incoming > 0 && incoming < previous) return
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

@Composable internal fun DeviceQuotaLight(value: DeviceQuota?, now: Long, compact: Boolean = false) {
    val palette=LocalAppPalette.current; val limit=quotaLimit(value)
    val stale=value?.unavailable==true || limit?.let { usageStale(it,now) } != false
    val motion=rememberChatMotion()
    // Keyed by caller/device: switching computers immediately shows that computer's number.
    Column(Modifier.fillMaxWidth()) {
        if (limit==null) Text(if(compact) "额度 —" else "额度未同步 · 需电脑提供可读取的任务快照",color=palette.muted,fontSize=if(compact) 10.sp else 11.sp)
        else {
            val windows=if(compact) limit.windows.take(1) else limit.windows
            windows.forEach { window ->
                val fraction by animateFloatAsState(window.remaining/100f,if(motion)tween(650)else snap(),label="observed-quota")
                Box(Modifier.fillMaxWidth().heightIn(min=if(compact)18.dp else 27.dp)) {
                    Canvas(Modifier.matchParentSize()) {
                        val width=size.width*fraction.coerceIn(0f,1f)
                        if(width>0) {
                            drawRect(Brush.horizontalGradient(listOf(palette.green.copy(alpha=if(stale).035f else .14f),palette.green.copy(alpha=if(stale).06f else .26f),palette.green.copy(alpha=0f)),0f,width))
                            drawLine(palette.green.copy(alpha=if(stale).15f else .5f),Offset(0f,size.height-1),Offset(width,size.height-1),1.5f)
                        }
                    }
                    Text((if(stale)"上次 "else "")+if(compact) "${window.remaining}%" else "${window.label} · 剩余 ${window.remaining}%",Modifier.padding(horizontal=3.dp,vertical=2.dp),color=if(stale)palette.muted else palette.ink,fontSize=if(compact)10.sp else 12.sp)
                }
            }
            if(!compact) Text("账号共享额度 · ${android.text.format.DateFormat.format("MM-dd HH:mm",limit.capturedAt)} 采样"+if(stale)" · 待核对"else"",color=palette.muted,fontSize=10.sp)
        }
    }
}
