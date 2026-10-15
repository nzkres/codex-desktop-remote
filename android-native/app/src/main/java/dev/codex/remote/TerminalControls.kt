package dev.codex.remote

import androidx.compose.foundation.background
import androidx.compose.animation.core.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** A task becomes sticky only after its whole original message has left the readable viewport. */
internal fun stickyTaskIndex(tasks: List<Int>, firstVisible: Int, visibleBottoms: Map<Int, Int>, boundary: Int): Int? =
    tasks.lastOrNull { index -> visibleBottoms[index]?.let { it <= boundary } ?: (index < firstVisible) }

@Composable internal fun StickyTask(item: MessageItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p=LocalAppPalette.current
    Row(modifier.fillMaxWidth().heightIn(min=48.dp)
        .background(if(p.isNight) Color(0xF037383B) else Color(0xF0DFE1E4))
        .testTag("sticky-task-${item.id}").clickable(role=Role.Button,onClickLabel="跳转到原消息",onClick=onClick)
        .padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(if(item.roundNumber>0) "${item.roundNumber} · " else "› ",color=p.muted,fontSize=12.sp)
        Text(taskText(item).replace('\n',' '),color=p.ink,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable internal fun ComposerWorking(active: Boolean, waiting: Boolean, startedAt: Long?, modifier: Modifier = Modifier) {
    val motion=rememberChatMotion()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _,_ -> foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var now by remember(startedAt) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active,foreground,startedAt) {
        now=System.currentTimeMillis()
        while(active && foreground) { delay(1000); now=System.currentTimeMillis() }
    }
    val pulse=if(active && motion) rememberInfiniteTransition(label="working-dot").animateFloat(.3f,1f,
        infiniteRepeatable(tween(700),RepeatMode.Reverse),label="working-dot-alpha") else null
    val p=LocalAppPalette.current
    Row(modifier,verticalAlignment=Alignment.CenterVertically) {
        Text("●",color=if(active) Color(0xFF36DDB0) else p.muted,fontSize=10.sp,
            modifier=Modifier.graphicsLayer { alpha=pulse?.value ?: 1f }.padding(end=6.dp))
        Text(if(active) "Working · ${if(startedAt==null) "时间未记录" else elapsedText((now-startedAt).coerceAtLeast(0))}"
            else if(waiting) "等待处理" else "就绪",color=p.muted,fontSize=11.sp)
    }
}
