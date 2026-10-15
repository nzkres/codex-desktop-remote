package dev.codex.remote

import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class LiveQuotaRuntime {
    internal val statuses = mutableStateMapOf<String,Map<String,HubStatus>>()
    internal var readError by mutableStateOf(false)
    internal val registry = LiveQuotaRegistry()
    internal var tick by mutableLongStateOf(0L)
}
internal val LocalLiveQuota = staticCompositionLocalOf<LiveQuotaRuntime?> { null }

@Composable internal fun rememberLiveQuota(root: Gateway?, scope: String, quotas: DeviceQuotaStore?, reads: ConversationReadStore?): LiveQuotaRuntime {
    val runtime = remember(scope) { LiveQuotaRuntime() }
    val lifecycle=LocalLifecycleOwner.current
    val motion=rememberChatMotion()
    val rootClient=remember(scope,root!=null) { root }
    LaunchedEffect(rootClient,lifecycle,quotas) {
        val client=rootClient ?: return@LaunchedEffect
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            supervisorScope {
                val jobs=linkedMapOf<String,Job>()
                val gate=Semaphore(3)
                try { while(isActive) {
                    try {
                        val terminals=withTimeout(10000) { client.terminalControl().snapshot() }.terminals.filter { it.online && it.state=="active" }
                        val ids=terminals.map { it.id }.toSet()
                        jobs.keys.toList().filter { it !in ids }.forEach { id -> jobs.remove(id)?.cancelAndJoin();runtime.registry.unavailable(id);runtime.statuses.remove(id) }
                        terminals.take(100).forEach { terminal -> if(terminal.id !in jobs) jobs[terminal.id]=launch {
                            val device=client.forTarget(TaskTarget(terminal.id,terminal.name)); var offset=0
                            var sessions=emptyList<Session>();var listedAt=0L
                            val active=linkedSetOf<String>()
                            while(isActive) {
                                try {
                                    if(System.currentTimeMillis()-listedAt>30000) { sessions=gate.withPermit { withTimeout(8000) { device.sessions() } }.sortedByDescending { it.updatedAt };active.retainAll(sessions.map { it.id }.toSet());runtime.registry.retain(terminal.id,sessions.map { it.id }.toSet());listedAt=System.currentTimeMillis() }
                                    // Bounded rotating ordinary reads; never open=1, no hidden Desktop navigation.
                                    val rotated=if(sessions.isEmpty()) emptyList() else sessions.drop(offset%sessions.size)+sessions.take(offset%sessions.size)
                                    val known=active.toList();val prioritized=if(known.isEmpty()) emptyList() else known.drop(offset%known.size)+known.take(offset%known.size)
                                    val batch=(prioritized.take(4).mapNotNull { id -> sessions.find { it.id==id } }+rotated.take(2)).distinctBy { it.id }
                                    offset+=2
                                    for(session in batch) {
                                        try {
                                            val requestedAt=System.currentTimeMillis()
                                            val signal=gate.withPermit { withTimeout(3500) { device.signals(session.id) } }
                                            runtime.statuses[terminal.id]=runtime.statuses[terminal.id].orEmpty().filterKeys { key -> sessions.any { it.id==key } } + (session.id to hubStatus(session,signal,requestedAt))
                                            runCatching { reads?.observe(terminal.id,session.id,signal.readRevision,requestedAt) }.onFailure { runtime.readError=true }
                                            runtime.registry.observe(terminal.id,session.id,signal.running,signal.usage,System.currentTimeMillis())
                                            if(signal.running==true || signal.questions.isNotEmpty()) active.add(session.id) else if(signal.running==false || signal.completed) active.remove(session.id)
                                            signal.usage?.let { quotas?.update(terminal.id,session.id,it) }
                                        } catch(e:CancellationException) { if(e !is TimeoutCancellationException) throw e }
                                          catch(_:Exception) { /* A failed read is unknown, never "all stopped" evidence. */ }
                                    }
                                } catch(e:CancellationException){throw e}catch(_:Exception){runtime.registry.unavailable(terminal.id);quotas?.failed(terminal.id)}
                                delay(5000)
                            }
                        } }
                    } catch(e:CancellationException){throw e}catch(_:Exception){ /* retain bounded fresh observations only */ }
                    delay(15000)
                }} finally { jobs.values.forEach { it.cancel() } }
            }
        }
    }
    LaunchedEffect(runtime,motion) {
        if(!motion) { runtime.registry.advance(0f,System.currentTimeMillis(),false);runtime.tick++;return@LaunchedEffect }
        var previous=withFrameNanos { it }
        while(isActive) {
            delay(if(runtime.registry.animating(System.currentTimeMillis()))16 else 250)
            withFrameNanos { now ->
                runtime.registry.advance((now-previous)/1_000_000_000f,System.currentTimeMillis(),true)
                previous=now;runtime.tick++
            }
        }
    }
    return runtime
}
