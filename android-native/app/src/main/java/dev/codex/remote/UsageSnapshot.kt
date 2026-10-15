package dev.codex.remote

import org.json.JSONObject
import kotlin.math.roundToInt

data class UsageWindow(val usedPercent:Double,val minutes:Int,val resetsAt:Long) {
    val remaining:Int get()=(100-usedPercent).coerceIn(0.0,100.0).roundToInt()
    val label:String get()=when { minutes%1440==0 -> "${minutes/1440}天";minutes%60==0 -> "${minutes/60}小时";else -> "${minutes}分钟" }
}
data class UsageLimit(val id:String,val capturedAt:Long,val windows:List<UsageWindow>)
data class UsageSnapshot(val limits:List<UsageLimit>,val supported:Boolean=true)
internal fun parseUsageSnapshot(value:JSONObject?):UsageSnapshot? {
    if(value==null) return null
    val limits=value.optJSONArray("limits") ?: return UsageSnapshot(emptyList())
    val parsed=(0 until minOf(limits.length(),8)).mapNotNull { i ->
        val row=limits.optJSONObject(i) ?: return@mapNotNull null
        val id=row.optString("id");val at=row.optLong("capturedAt",-1)
        if(!id.matches(Regex("[A-Za-z0-9_.-]{1,80}")) || at<=0) return@mapNotNull null
        val windows=row.optJSONArray("windows") ?: return@mapNotNull null
        val valid=(0 until minOf(windows.length(),2)).mapNotNull window@{ j ->
            val w=windows.optJSONObject(j) ?: return@window null
            val used=w.optDouble("usedPercent",Double.NaN);val minutes=w.optInt("windowDurationMins",0);val reset=w.optLong("resetsAt",0)
            if(!used.isFinite() || used !in 0.0..100.0 || minutes !in 1..525600 || reset<=0) null else UsageWindow(used,minutes,reset)
        }
        if(valid.isEmpty()) null else UsageLimit(id,at,valid)
    }
    return UsageSnapshot(parsed)
}
internal fun usageStale(limit:UsageLimit,now:Long):Boolean =
    now<limit.capturedAt-60000 || now-limit.capturedAt>10*60*1000 || limit.windows.any { it.resetsAt*1000<=now }
internal fun usageSummary(snapshot:UsageSnapshot?,now:Long):String {
    if(snapshot==null) return "额度未同步"
    val core=snapshot.limits.find { it.id=="codex" } ?: snapshot.limits.firstOrNull() ?: return "额度暂不可用"
    val prefix=if(usageStale(core,now)) "上次额度 · " else "额度 · "
    return prefix+core.windows.joinToString("  ") { "${it.label}剩余 ${it.remaining}%" }
}
