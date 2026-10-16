package dev.codex.remote

import androidx.compose.animation.core.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
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

internal data class HubStatus(val waiting: Boolean, val running: Boolean, val checkedAt: Long, val known: Boolean = true)
internal fun hubMatches(filter: String, status: HubStatus?): Boolean = when(filter) {
    "waiting" -> status?.waiting == true
    "running" -> status?.running == true && !status.waiting
    else -> true
}
internal fun hubStatus(session:Session, signal:SignalSnapshot, now:Long):HubStatus = HubStatus(signal.questions.isNotEmpty(),
    signal.running == true && signal.questions.isEmpty(),now,signal.running!=null || signal.completed || signal.questions.isNotEmpty())


@Composable internal fun ConversationHub(
    gateway: Gateway, sessions: List<Session>, projects: List<Project>, activity: List<ActivityEntry>, busy: Boolean,
    statuses: MutableMap<String,HubStatus>, lastSync:Long, draftStore: DraftStore?, quotas: DeviceQuotaStore?, runtime: LiveQuotaRuntime, reads: ConversationReadStore?,
    onSelect:(TaskTarget)->Unit, onOpen:(String,Boolean)->Unit, onManage:()->Unit, onCreate:()->Unit = {},
) {
    val palette=LocalAppPalette.current; val lifecycle=LocalLifecycleOwner.current
    val control=remember(gateway){gateway.terminalControl()}
    var terminals by remember(control){mutableStateOf<List<RemoteTerminal>>(emptyList())}
    var deviceError by remember(control){mutableStateOf("")};var loading by remember(control){mutableStateOf(true)}
    var filter by rememberSaveable(gateway.target.id){mutableStateOf("all")}
    val target=gateway.target
    LaunchedEffect(control,lifecycle){lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){
        try {terminals=control.snapshot().terminals;deviceError=""}catch(e:CancellationException){throw e}catch(_:Exception){deviceError="设备状态暂未核对"}finally{loading=false};delay(15000)
    }}}
    val selected=terminals.firstOrNull{it.id==target.id};val online=selected?.online==true && selected.state=="active"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(lifecycle){lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){now=System.currentTimeMillis();delay(1000)}}}
    val merged=(statuses.keys+runtime.statuses[target.id].orEmpty().keys).associateWith { id -> listOfNotNull(statuses[id],runtime.statuses[target.id]?.get(id)).maxBy { it.checkedAt } }
    val freshStatuses=if(online)merged.filterValues{it.known && now-it.checkedAt in 0..30000} else emptyMap()
    val waiting=sessions.count{freshStatuses[it.id]?.waiting==true}
    val running=sessions.count{freshStatuses[it.id]?.running==true && freshStatuses[it.id]?.waiting!=true}
    val known=sessions.count{it.id in freshStatuses}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
            terminals.forEach { terminal ->
                val active=terminal.id==target.id
                Column(Modifier.width(IntrinsicSize.Min).heightIn(min=48.dp).clickable(enabled=!busy&&terminal.state=="active"){onSelect(TaskTarget(terminal.id,terminal.name))},verticalArrangement=Arrangement.Center){
                    Text(terminal.name,Modifier.processingLight(terminal.state=="active"&&!terminal.online&&loading),color=if(terminal.online&&terminal.state=="active")palette.green else palette.muted,fontSize=12.sp,maxLines=1)
                    Spacer(Modifier.height(4.dp));Box(Modifier.fillMaxWidth().height(1.dp).background(if(active)palette.muted else Color.Transparent))
                }
            }
            if(terminals.isEmpty())Text(if(loading)"正在连接…"else "暂无设备",Modifier.processingLight(loading).padding(vertical=14.dp),color=palette.muted,fontSize=12.sp)
        }
        if(deviceError.isNotEmpty()) Text(deviceError,Modifier.padding(horizontal=16.dp),color=palette.muted,fontSize=10.sp)
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),verticalAlignment=Alignment.CenterVertically){
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            listOf(Triple("all","全部",sessions.size),Triple("running",if(online)"运行中"else"上次运行",running),Triple("waiting",if(online)"待回答"else"上次待答",waiting)).forEach{(key,label,count)->
                TextButton(onClick={filter=key},modifier=Modifier.testTag("hub-filter-$key"),contentPadding=PaddingValues(horizontal=7.dp)){
                    Text("$label ${if(key=="all") count.toString() else if(known==0) "—" else count.toString()+if(known<sessions.size) "+" else ""}",color=if(key=="waiting"&&count>0)palette.green else if(filter==key)palette.ink else palette.muted,fontSize=11.sp)
                }
            }
            }
            GlassActionButton("新对话",onCreate,Modifier.testTag("hub-new-conversation"),icon="plus",compact=true,enabled=!busy&&online&&target.isSelected)
            TextButton(onClick=onManage,enabled=!busy,contentPadding=PaddingValues(horizontal=5.dp)){Text("设备",color=palette.muted,fontSize=11.sp)}
        }
        if(reads==null || runtime.readError)Text("阅读位置暂未能保存",Modifier.padding(horizontal=16.dp),color=palette.errorInk,fontSize=10.sp)
        val shown=sessions.sortedByDescending{it.updatedAt}.filter{hubMatches(filter,freshStatuses[it.id])}
        if(shown.isEmpty())Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){
            Text(if(filter=="all")"选择已接入的电脑，同步它的对话。"else"已同步的对话中暂无${if(filter=="waiting")"待回答"else"运行中"}记录。",color=palette.muted,fontSize=12.sp)
        }else LazyColumn(Modifier.weight(1f).fillMaxWidth()){
            itemsIndexed(shown,key={_,s->s.id}){index,session->
                val status=freshStatuses[session.id];val wait=status?.waiting==true
                val read=reads?.value(target.id,session.id);val unread=read?.unread==true
                val zebra=if(index%2==0)palette.ink.copy(alpha=.035f)else palette.background.copy(alpha=.12f)
                Row(Modifier.fillMaxWidth().background(zebra).clickable(enabled=!busy){onOpen(session.id,wait)}.padding(horizontal=16.dp,vertical=12.dp),verticalAlignment=Alignment.Top){
                    if(wait)Box(Modifier.width(2.dp).height(43.dp).background(palette.green))
                    Column(Modifier.weight(1f).padding(start=if(wait)8.dp else 0.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically) {
                        if(status?.running==true) HubRunningDot(palette.green)
                        else if(unread) Box(Modifier.padding(end=7.dp).size(5.dp).background(palette.green,CircleShape))
                        Text(session.title,color=if(wait)palette.green else palette.ink,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis) }
                        Text((projects.firstOrNull{it.id==session.projectId}?.name?:"Codex")+when{wait->" · 等你回答";status?.running==true->" · 运行中";status==null->" · 状态待同步"+if(unread)" · 未读"else"";unread->" · 完成未读";read?.revision?.isNotEmpty()==true->" · 已读";else->""},Modifier.padding(top=5.dp),color=palette.muted,fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
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

@Composable private fun HubRunningDot(color:Color) {
    val motion=rememberChatMotion()
    val pulse=if(motion) rememberInfiniteTransition(label="hub-running").animateFloat(.3f,1f,infiniteRepeatable(tween(1400),RepeatMode.Reverse),label="hub-breath") else null
    Box(Modifier.padding(end=7.dp).size(5.dp).graphicsLayer { alpha=pulse?.value ?: 1f }.background(color,CircleShape).testTag("hub-running-dot"))
}
