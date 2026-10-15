package dev.codex.remote

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable internal fun QuotaUsageDetails(snapshot:UsageSnapshot?, unavailable:Boolean=false) {
    val palette=LocalAppPalette.current
    var open by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(snapshot) { while(true) { now=System.currentTimeMillis();delay(30000) } }
    val core=snapshot?.limits?.find { it.id=="codex" } ?: snapshot?.limits?.firstOrNull()
    val windows=quotaWindows(core)
    val warning=core!=null && !usageStale(core,now) && windows.any { it.remaining<=10 }
    val summary=if(core==null) "额度未同步" else if(windows.isEmpty()) "Pro 周额度未同步" else (if(unavailable||usageStale(core,now))"上次 · "else "剩余 · ")+windows.joinToString("  │  ") { "${it.label} ${it.remaining}%" }
    Text(summary,color=if(warning&&!unavailable)palette.errorInk else palette.muted,fontSize=11.sp,
        maxLines=1,overflow=TextOverflow.Ellipsis,
        modifier=Modifier.fillMaxWidth().testTag("device-quota-details").clickable {open=true}.padding(vertical=10.dp))
    if(open) AlertDialog(onDismissRequest={open=false},title={Text("我的额度")},
        containerColor=palette.card.copy(alpha=.94f),
        text={Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("电脑当前任务记录的账号额度快照；额度在同一账号间共享，不是本轮消耗。",color=palette.muted,fontSize=12.sp)
            if(snapshot==null) Text("当前连接器尚未同步额度，请更新电脑连接器后刷新对话。",modifier=Modifier.padding(top=12.dp),color=palette.ink)
            else if(snapshot.limits.isEmpty()) Text("尚未读到有效额度；请保持 Desktop 登录。任务产生新的额度记录后会随对话同步。",modifier=Modifier.padding(top=12.dp),color=palette.ink)
            snapshot?.limits?.forEach { limit ->
                Text((if(limit.id=="codex") "Codex" else limit.id)+" · "+(limit.planType?.uppercase() ?: "套餐未同步"),color=palette.ink,modifier=Modifier.padding(top=16.dp))
                if(limit.planType=="pro") Text("Pro 显示周额度；五小时限制已取消。没有周快照时等待电脑同步，不推算额度。",color=palette.muted,fontSize=12.sp)
                quotaWindows(limit).forEach { window ->
                    Text("${window.label} · 剩余 ${window.remaining}%",color=palette.ink,fontSize=14.sp)
                    LinearProgressIndicator(progress={window.remaining/100f},modifier=Modifier.fillMaxWidth().padding(vertical=8.dp))
                    Text("重置：${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(window.resetsAt*1000))}",color=palette.muted,fontSize=12.sp)
                }
                Text("采样：${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(limit.capturedAt))}"+
                    if(unavailable||usageStale(limit,now)) "\n上次快照待核对，等待电脑更新；不会推算为满额。" else "",color=palette.muted,fontSize=12.sp)
            }
        }},confirmButton={TextButton(onClick={open=false}) { Text("关闭") }})
}
