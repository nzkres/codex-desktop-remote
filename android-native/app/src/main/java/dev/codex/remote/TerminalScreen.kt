package dev.codex.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class TerminalAction(val id: String, val action: String, val name: String, val pairing: Boolean = false)

@Composable
fun TerminalScreen(client: TerminalControl, selected: TaskTarget = TaskTarget(), selecting: Boolean = false,
    onSelect: ((TaskTarget) -> Unit)? = null) {
    // Recreate all state on account/client change. No pairing code on disk or in saved state.
    key(client) { TerminalPage(client, selected, selecting, onSelect) }
}

@Composable
private fun TerminalPage(client: TerminalControl, selected: TaskTarget, selecting: Boolean, onSelect: ((TaskTarget) -> Unit)?) {
    val scope = rememberCoroutineScope()
    var snapshot by remember { mutableStateOf<TerminalSnapshot?>(null) }
    var code by remember { mutableStateOf<PairingCode?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var action by remember { mutableStateOf<TerminalAction?>(null) }
    var name by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    suspend fun refresh() { snapshot = client.snapshot() }
    fun perform(write: Boolean = false, operation: suspend () -> Unit = {}) {
        if (busy) return
        busy = true
        error = ""
        scope.launch {
            try { operation(); refresh() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                snapshot = null // No stale management buttons after uncertain response.
                error = if (write) "操作结果尚未确认，请先刷新核对，不要重复提交。" else
                    (failure.message?.takeIf { it.startsWith("中继") || it.startsWith("登录") || it.startsWith("终端") } ?: "连接失败，请刷新重试。")
            } finally { busy = false }
        }
    }
    LaunchedEffect(client) { perform() }
    LaunchedEffect(code) {
        while (code != null) {
            now = System.currentTimeMillis() / 1000
            if (now >= (code?.expires ?: 0)) { code = null; break }
            delay(1000) // Local countdown only; no background polling.
        }
    }
    fun ask(value: TerminalAction) { action = value; name = value.name }
    LazyColumn(Modifier.fillMaxSize().background(HapiPalette.background), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("远程终端", color = HapiPalette.ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(if (onSelect == null) "管理接入的电脑。任务仍使用当前入口。" else
                    "当前：${selected.name}。选择电脑后，任务与新对话都在该电脑执行。", color = HapiPalette.muted, fontSize = 12.sp)
                if (onSelect != null) GlassActionButton("原电脑 · 旧通道", { onSelect(TaskTarget()) }, enabled = !busy && !selecting)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassActionButton("刷新", { perform() }, busy = busy, icon = "plan")
                    GlassActionButton("添加电脑", { perform(true) { code = client.createPairing() } }, icon = "plus", enabled = !busy && snapshot != null && code == null)
                }
                if (busy) Text("正在核对终端状态", Modifier.processingLight(true), color = HapiPalette.muted, fontSize = 12.sp)
                if (error.isNotBlank()) Text(error, color = LocalAppPalette.current.errorInk, fontSize = 13.sp)
                code?.let { pairing ->
                    Text("在新电脑连接器中输入以下一次性配对码：", color = HapiPalette.muted, fontSize = 12.sp)
                    SelectionContainer { Text(pairing.code, color = HapiPalette.ink, fontSize = 22.sp) }
                    Text("剩余 ${(pairing.expires - now).coerceAtLeast(0)} 秒 · 电脑申请后刷新并确认", color = HapiPalette.muted, fontSize = 12.sp)
                }
            }
        }
        val loaded = snapshot
        if (loaded != null) {
            if (loaded.terminals.isEmpty() && loaded.pairings.isEmpty()) item {
                Text("还没有已登记电脑。现有聊天连接不会被自动迁移。", Modifier.padding(16.dp), color = HapiPalette.muted)
            }
            items(loaded.pairings, key = { "pair-${it.id}" }) { pairing ->
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (pairing.state == "claimed") pairing.name else "等待电脑申请", color = HapiPalette.ink)
                    Text("核对电脑名称后再批准；配对可能已过期，请以刷新结果为准。", color = HapiPalette.muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pairing.state == "claimed") GlassActionButton("确认加入", { ask(TerminalAction(pairing.id, "confirm", pairing.name, true)) }, enabled = !busy)
                        GlassActionButton("取消配对", { ask(TerminalAction(pairing.id, "cancel", pairing.name, true)) }, enabled = !busy)
                    }
                }
                HorizontalDivider(color = HapiPalette.divider)
            }
            items(loaded.terminals, key = { it.id }) { terminal ->
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(terminal.name, color = HapiPalette.ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(when { terminal.state == "paused" -> "远程访问已暂停"; terminal.online -> "连接器在线 · 执行能力尚未验证"; else -> "连接器离线" }, color = HapiPalette.muted, fontSize = 12.sp)
                    if (onSelect != null) GlassActionButton(if (selected.id == terminal.id) "查看当前电脑任务" else "查看这台电脑任务",
                        { onSelect(TaskTarget(terminal.id, terminal.name)) }, Modifier.testTag("terminal-open-${terminal.id}"), enabled = !busy && !selecting && terminal.state == "active")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassActionButton("重命名", { ask(TerminalAction(terminal.id, "rename", terminal.name)) }, enabled = !busy)
                        GlassActionButton(if (terminal.state == "paused") "恢复连接" else "暂停连接", {
                            ask(TerminalAction(terminal.id, if (terminal.state == "paused") "resume" else "pause", terminal.name))
                        }, enabled = !busy)
                    }
                    GlassActionButton("移除", { ask(TerminalAction(terminal.id, "remove", terminal.name)) }, enabled = !busy)
                }
                HorizontalDivider(color = HapiPalette.divider)
            }
        }
    }
    action?.let { selected ->
        val editable = selected.action in setOf("rename", "confirm")
        AlertDialog(onDismissRequest = { if (!busy) action = null },
            title = { Text(when (selected.action) { "rename" -> "重命名电脑"; "confirm" -> "确认电脑加入"; "remove" -> "移除电脑"; "pause" -> "暂停远程访问"; "resume" -> "恢复远程访问"; else -> "取消配对" }) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (selected.action == "remove") "移除 ${selected.name} 后需要重新配对；不会删除电脑文件或终止任务。" else "仅更改远程访问配置，不会停止电脑正在执行的任务。")
                if (editable) OutlinedTextField(name, { name = it.take(80) }, label = { Text("电脑名称") }, singleLine = true)
            } },
            confirmButton = { TextButton(enabled = !busy && (!editable || name.isNotBlank()), onClick = {
                action = null
                val chosenName = name.trim()
                perform(true) {
                    when { selected.action == "confirm" -> client.confirm(selected.id, chosenName)
                        selected.pairing -> client.cancel(selected.id)
                        else -> client.change(selected.id, selected.action, chosenName) }
                    code = null
                }
            }) { Text("确认") } },
            dismissButton = { TextButton(onClick = { action = null }) { Text("返回") } })
    }
}
