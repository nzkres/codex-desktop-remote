package dev.codex.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

internal data class HubStatus(val waiting: Boolean, val running: Boolean, val checkedAt: Long)
internal fun hubMatches(filter: String, status: HubStatus?): Boolean = when(filter) {
    "waiting" -> status?.waiting == true
    "running" -> status?.running == true && !status.waiting
    else -> true
}
internal fun hubStatus(session:Session, signal:SignalSnapshot, now:Long):HubStatus = HubStatus(signal.questions.isNotEmpty(),
    signal.questions.isEmpty() && session.status=="RUNNING" && session.activeTurnId!=null && !signal.completed,now)

@Composable internal fun ConversationHub(
    gateway: Gateway, sessions: List<Session>, projects: List<Project>, activity: List<ActivityEntry>, busy: Boolean,
    statuses: MutableMap<String,HubStatus>, lastSync:Long, draftStore: DraftStore?, quotas: DeviceQuotaStore?,
    onSelect:(TaskTarget)->Unit, onOpen:(String,Boolean)->Unit, onManage:()->Unit,
) {
    val palette=LocalAppPalette.current; val lifecycle=LocalLifecycleOwner.current
    val control=remember(gateway){gateway.terminalControl()}
    var terminals by remember(control){mutableStateOf<List<RemoteTerminal>>(emptyList())}
    var deviceError by remember(control){mutableStateOf("")};var loading by remember(control){mutableStateOf(true)}
    var filter by rememberSaveable(gateway.target.id){mutableStateOf("all")}
    var batch by remember(gateway){mutableIntStateOf(0)};var checking by remember(gateway){mutableStateOf(false)}
    var checkAgain by remember(gateway){mutableIntStateOf(0)}
    val quotaClients=remember(gateway){mutableMapOf<String,Gateway>()}
    val target=gateway.target
    LaunchedEffect(control,lifecycle){lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){
        try {terminals=control.snapshot().terminals;deviceError=""}catch(e:CancellationException){throw e}catch(_:Exception){deviceError="设备状态暂未核对"}finally{loading=false};delay(15000)
    }}}
    val selected=terminals.firstOrNull{it.id==target.id};val online=selected?.online==true && selected.state=="active"
    LaunchedEffect(gateway,sessions.map{it.id},batch,checkAgain,online,lifecycle){
        if(!online||busy)return@LaunchedEffect
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
            checking=true
            try {sessions.sortedByDescending{it.updatedAt}.take((batch+1)*12).takeLast(12).forEach{session->
                val signal=try{withTimeoutOrNull(3000){gateway.signals(session.id)}}catch(e:CancellationException){throw e}catch(_:Exception){null}
                if(signal!=null)statuses[session.id]=hubStatus(session,signal,System.currentTimeMillis())
            }}finally{checking=false}
        }
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycle){lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){now=System.currentTimeMillis();delay(30000)}}}
    LaunchedEffect(gateway,terminals.map { it.id to it.online },sessions.firstOrNull()?.id,lifecycle,quotas) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true) {
                // Only paired, active/online devices; ordinary reads never set open=1 or submit anything.
                terminals.filter { it.online && it.state == "active" }.forEach { terminal ->
                    val previous=quotas?.values?.get(terminal.id)
                    if(quotas!=null) {
                        try {
                            val result=withTimeoutOrNull(12000) {
                                val client=quotaClients.getOrPut(terminal.id){gateway.forTarget(TaskTarget(terminal.id,terminal.name))}
                                val thread=previous?.thread ?: (if(terminal.id==target.id) sessions else client.sessions()).maxByOrNull { it.updatedAt }?.id
                                thread?.let { it to client.conversation(it).usage }
                            }
                            if(result!=null) quotas.update(terminal.id,result.first,result.second) else quotas.failed(terminal.id)
                        } catch(e:CancellationException){throw e}catch(_:Exception){quotas.failed(terminal.id)}
                    }
                }
                delay(30000L)
            }
        }
    }
    val freshStatuses=statuses.filterValues{now-it.checkedAt in 0..120000}
    val waiting=sessions.count{freshStatuses[it.id]?.waiting==true}
    val running=sessions.count{freshStatuses[it.id]?.running==true && freshStatuses[it.id]?.waiting!=true}
    val known=sessions.count{it.id in freshStatuses}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            terminals.forEach { terminal ->
                val active=terminal.id==target.id
                Column(Modifier.width(IntrinsicSize.Min).heightIn(min=48.dp).clickable(enabled=!busy&&terminal.state=="active"){onSelect(TaskTarget(terminal.id,terminal.name))},verticalArrangement=Arrangement.Center){
                    Text(terminal.name,Modifier.processingLight(terminal.state=="active"&&!terminal.online&&loading),color=if(terminal.online&&terminal.state=="active")palette.green else palette.muted,fontSize=12.sp,maxLines=1)
                    key(terminal.id) { DeviceQuotaLight(quotas?.values?.get(terminal.id)?.let { if(!terminal.online || terminal.state!="active") it.copy(unavailable=true) else it },now,compact=true) }
                    Spacer(Modifier.height(4.dp));Box(Modifier.fillMaxWidth().height(1.dp).background(if(active)palette.muted else Color.Transparent))
                }
            }
            if(terminals.isEmpty())Text(if(loading)"正在连接…"else "暂无设备",Modifier.processingLight(loading).padding(vertical=14.dp),color=palette.muted,fontSize=12.sp)
        }
        Row(Modifier.fillMaxWidth().padding(start=16.dp,end=8.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                Text(when{deviceError.isNotEmpty()->deviceError;selected?.state=="paused"->"远程访问已暂停";online->"连接器在线";loading->"连接中";else->"离线 · 列表为上次同步"},Modifier.processingLight(loading),color=palette.muted,fontSize=11.sp)
                Text(if(lastSync>0)"${android.text.format.DateFormat.format("MM-dd HH:mm",lastSync)} 同步 · ${projects.size} 个项目"else "尚未同步对话列表",color=palette.muted,fontSize=10.sp)
            }
            TextButton(onClick=onManage,enabled=!busy){Text("设备",color=palette.muted,fontSize=11.sp)}
        }
        key(target.id) { Column(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=3.dp)) {
            DeviceQuotaLight(quotas?.values?.get(target.id)?.let { if(!online) it.copy(unavailable=true) else it },now)
            if(quotas==null) Text("额度加密缓存不可用",color=palette.errorInk,fontSize=10.sp)
        } }
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
            listOf(Triple("all","全部",sessions.size),Triple("running",if(online)"运行中"else"上次运行",running),Triple("waiting",if(online)"待回答"else"上次待答",waiting)).forEach{(key,label,count)->
                TextButton(onClick={filter=key},modifier=Modifier.testTag("hub-filter-$key"),contentPadding=PaddingValues(horizontal=7.dp)){
                    Text("$label $count",color=if(key=="waiting"&&count>0)palette.green else if(filter==key)palette.ink else palette.muted,fontSize=11.sp)
                }
            }
            Spacer(Modifier.weight(1f));TextButton(onClick={checkAgain++},enabled=!checking&&online&&!busy){Text(if(checking)"核对中"else"刷新",fontSize=11.sp)}
        }
        if(known<sessions.size)Row(Modifier.padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){
            Text("已核对 $known/${sessions.size} 段；未知状态不计入筛选数量",Modifier.weight(1f),color=palette.muted,fontSize=10.sp)
            if((batch+1)*12<sessions.size)TextButton(onClick={batch++},enabled=!checking&&online&&!busy){Text("继续核对",fontSize=10.sp)}
        }
        if(!online&&sessions.isNotEmpty())Text("历史详情与待答状态需连接后核对；不重发未知回执。",Modifier.padding(horizontal=16.dp,vertical=5.dp),color=palette.muted,fontSize=10.sp)
        val shown=sessions.sortedByDescending{it.updatedAt}.filter{hubMatches(filter,freshStatuses[it.id])}
        if(shown.isEmpty())Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
            Text(if(filter=="all")"选择已接入的电脑，同步它的对话。"else"已核对的对话中暂无${if(filter=="waiting")"待回答"else"运行中"}记录。",color=palette.muted,fontSize=12.sp)
        }else LazyColumn(Modifier.weight(1f).fillMaxWidth()){
            itemsIndexed(shown,key={_,s->s.id}){index,session->
                val status=freshStatuses[session.id];val wait=status?.waiting==true
                val zebra=if(index%2==0)palette.ink.copy(alpha=.035f)else palette.background.copy(alpha=.12f)
                Row(Modifier.fillMaxWidth().background(zebra).clickable(enabled=!busy){onOpen(session.id,wait)}.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.Top){
                    if(wait)Box(Modifier.width(2.dp).height(43.dp).background(palette.green))
                    Column(Modifier.weight(1f).padding(start=if(wait)8.dp else 0.dp)){
                        Text(session.title,color=if(wait)palette.green else palette.ink,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text((projects.firstOrNull{it.id==session.projectId}?.name?:"Codex")+when{wait->" · 等你回答";status?.running==true->" · 运行中";status==null->" · 状态待核对";else->""},Modifier.padding(top=5.dp),color=palette.muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        DraftLine(draftStore?.read(session.id)?.text.orEmpty(),Modifier.padding(top=3.dp))
                    }
                    Column(horizontalAlignment=Alignment.End){
                        Text(if(session.updatedAt>0)android.text.format.DateFormat.format("MM-dd HH:mm",session.updatedAt).toString()else"未记录时间",color=palette.muted,fontSize=10.sp)
                        if(wait)Text("去回答",Modifier.padding(top=8.dp),color=palette.green,fontSize=11.sp)
                    }
                }
            }
        }
    }
}
