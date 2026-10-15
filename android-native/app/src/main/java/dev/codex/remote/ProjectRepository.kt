package dev.codex.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

internal data class RepositoryTask(val id: String, val title: String, val status: String = "todo")
internal data class RepositoryProject(val id: String, val title: String, val goal: String, val repository: String,
    val state: String = "planning", val tasks: List<RepositoryTask> = emptyList(), val accepted: Boolean = false) {
    val done: Int get() = tasks.count { it.status == "done" }
    val progress: Int? get() = if (tasks.isEmpty()) null else (100f * done / tasks.size).roundToInt()
}
internal val repositoryStates = linkedMapOf("all" to "全部", "planning" to "构思中", "ready" to "待开始", "active" to "进行中", "completed" to "已结束")

/** Explicit user-reviewed import. Never infers progress from repository existence or conversation count. */
internal fun parseRepositoryProject(raw: String): RepositoryProject {
    require(raw.toByteArray().size <= 131072) { "项目资料超过128KiB，请精简后导入。" }
    val value = JSONObject(raw)
    require(value.getInt("schemaVersion") == 1) { "项目资料版本不支持。" }
    fun text(key: String, limit: Int, required: Boolean = true): String {
        val result = value.optString(key).trim()
        require(result.length <= limit && (!required || result.isNotBlank())) { "项目${key}无效。" }; return result
    }
    val id = text("id", 120); require(id.matches(Regex("[A-Za-z0-9._-]+"))) { "项目编号无效。" }
    val state = text("state", 30); require(state in repositoryStates && state != "all") { "项目状态无效。" }
    val rows = value.optJSONArray("tasks") ?: JSONArray()
    require(rows.length() <= 200) { "单项目最多200项任务。" }
    val tasks = (0 until rows.length()).map { i ->
        val t = rows.getJSONObject(i); val title = t.getString("title").trim(); val taskId = t.getString("id")
        val status = t.optString("status", "todo")
        require(taskId.isNotBlank() && taskId.length <= 120 && title.isNotBlank() && title.length <= 500 && status in setOf("todo", "doing", "done")) { "任务格式无效。" }
        RepositoryTask(taskId, title, status)
    }
    require(tasks.map { it.id }.distinct().size == tasks.size) { "任务编号重复。" }
    val planConfirmed = value.optJSONObject("plan")?.optString("state") == "confirmed"
    val accepted = value.optJSONObject("acceptance")?.optString("state") == "accepted"
    require(state == "planning" || (tasks.isNotEmpty() && planConfirmed)) { "请先确认方案并生成任务。" }
    require(state != "planning" || tasks.isEmpty()) { "构思阶段请先核对任务，确认方案后再导入。" }
    require(state != "ready" || tasks.all { it.status == "todo" }) { "待开始项目不能包含已执行任务。" }
    require(state != "completed" || (accepted && tasks.all { it.status == "done" })) { "任务全部完成并确认验收后，才能标记已结束。" }
    val repo = value.optJSONObject("repository")?.optString("fullName").orEmpty()
    require(repo.isEmpty() || repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "仓库名称应为owner/repo。" }
    return RepositoryProject(id, text("title", 120), text("goal", 2000, false), repo, state, tasks, accepted)
}
internal fun repositoryJson(p: RepositoryProject): JSONObject = JSONObject().put("schemaVersion", 1).put("id", p.id)
    .put("title", p.title).put("goal", p.goal).put("repository", JSONObject().put("provider", "github").put("fullName", p.repository))
    .put("state", p.state).put("plan", JSONObject().put("state", if (p.state == "planning") "draft" else "confirmed"))
    .put("tasks", JSONArray(p.tasks.map { JSONObject().put("id", it.id).put("title", it.title).put("status", it.status) }))
    .put("acceptance", JSONObject().put("state", if (p.accepted) "accepted" else "pending"))

internal class ProjectRepositoryStore(private val prefs: SharedPreferences) {
    var projects by mutableStateOf(run {
        val rows = JSONArray(prefs.getString("projects", "[]")); require(rows.length() <= 100)
        (0 until rows.length()).map { parseRepositoryProject(rows.getJSONObject(it).toString()) }
    }); private set
    fun save(p: RepositoryProject) {
        parseRepositoryProject(repositoryJson(p).toString())
        val next = listOf(p) + projects.filterNot { it.id == p.id }; require(next.size <= 100) { "本机最多100个项目。" }
        val raw = JSONArray(next.map(::repositoryJson)).toString(); require(raw.toByteArray().size <= 524288) { "项目总资料超过512KiB。" }
        check(prefs.edit().putString("projects", raw).commit()) { "项目未能加密保存，请重试。" }; projects = next
    }
    companion object {
        fun open(context: Context, scope: String): ProjectRepositoryStore {
            require(scope.matches(Regex("[a-f0-9]{64}")))
            return ProjectRepositoryStore(EncryptedSharedPreferences.create("project-repository-$scope", MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM))
        }
    }
}

@Composable internal fun ProjectRepositoryScreen(store: ProjectRepositoryStore?) {
    val context = LocalContext.current; val palette = LocalAppPalette.current
    var filter by remember { mutableStateOf("all") }; var selected by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }; var plan by remember { mutableStateOf(false) }
    var editingId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }; var goal by remember { mutableStateOf("") }; var repo by remember { mutableStateOf("") }
    var taskText by remember { mutableStateOf("") }; var error by remember { mutableStateOf("") }
    var imported by remember { mutableStateOf<RepositoryProject?>(null) }; var exported by remember { mutableStateOf<RepositoryProject?>(null) }
    fun save(p: RepositoryProject) { try { requireNotNull(store).save(p); error = "" } catch (e: Exception) { error = e.message ?: "项目保存失败" } }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try { val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytesUpTo(131073) }
            require(bytes.size <= 131072) { "项目文件过大。" }; imported = parseRepositoryProject(bytes.toString(Charsets.UTF_8))
        } catch (e: Exception) { error = e.message ?: "无法读取项目资料" }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val p = exported
        if (uri != null && p != null) try { context.contentResolver.openOutputStream(uri)!!.use { it.write(repositoryJson(p).toString(2).toByteArray()) } }
        catch (_: Exception) { error = "未能导出项目资料。" }
        exported = null
    }
    val current = store?.projects?.firstOrNull { it.id == selected }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (current == null) "任务仓库" else "项目详情", Modifier.weight(1f), color = palette.ink, fontSize = 21.sp)
            if (current != null) TextButton(onClick = { selected = null }) { Text("返回") }
            else { TextButton(onClick = { importer.launch(arrayOf("application/json", "text/plain")) }, enabled = store != null) { Text("导入") }
                TextButton(onClick = { editingId=null;title="";goal="";repo="";plan=false;editing=true }, enabled = store != null) { Text("新建") } }
        } }
        if (error.isNotBlank()) item { Text(error, color = palette.errorInk, fontSize = 12.sp) }
        if (store == null) item { Text("本机加密项目存储不可用，未创建或修改项目。", color = palette.errorInk) }
        else if (current == null) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { repositoryStates.forEach { (key, label) ->
                TextButton(onClick = { filter = key }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(3.dp)) { Text(label, color = if (filter == key) palette.ink else palette.muted, fontSize = 11.sp) }
            } } }
            val rows = store.projects.filter { filter == "all" || it.state == filter }.sortedBy { listOf("active","planning","ready","completed").indexOf(it.state) }
            if (rows.isEmpty()) item { Text("从一个想法开始，或导入已确认的项目JSON。\n本机保存，按账号隔离；不会按对话数量推算进度。", color = palette.muted, fontSize = 12.sp) }
            items(rows, key = { it.id }) { p ->
                DecisionSurface { Column(Modifier.fillMaxWidth().clickable { selected = p.id }.padding(12.dp)) {
                    Row { Text(p.title, Modifier.weight(1f), color=palette.ink,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(repositoryStates[p.state].orEmpty(),color=if(p.state=="planning") palette.muted else palette.green,fontSize=11.sp) }
                    Spacer(Modifier.height(10.dp))
                    Row { Text(if(p.progress==null) "方案未确认 · 尚无任务进度" else "${p.done} / ${p.tasks.size} 项完成",Modifier.weight(1f),color=palette.muted,fontSize=11.sp)
                        Text(p.progress?.let { "$it%" } ?: "待构思",color=palette.ink,fontSize=16.sp) }
                    p.progress?.let { LinearProgressIndicator(progress={it/100f},modifier=Modifier.fillMaxWidth().padding(top=7.dp).height(3.dp),color=palette.green,trackColor=palette.muted.copy(alpha=.1f)) }
                    Text(when(p.state){"planning"->"下一步：确认范围与验收标准";"ready"->"任务已生成 · 等待开始";"completed"->"已验收归档 · 查看交付记录";else->"当前："+(p.tasks.firstOrNull{it.status!="done"}?.title?:"等待验收")},Modifier.padding(top=9.dp),color=palette.muted,fontSize=11.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                } }
            }
        } else {
            item { Text(current.title,color=palette.ink,fontSize=18.sp);Text(current.goal,color=palette.muted,fontSize=12.sp)
                if(current.repository.isNotBlank()) Text(current.repository,color=palette.green,fontSize=12.sp)
                Text(repositoryStates[current.state].orEmpty(),Modifier.padding(vertical=9.dp),color=palette.muted,fontSize=12.sp) }
            item { TextButton(onClick={editingId=current.id;title=current.title;goal=current.goal;repo=current.repository;plan=false;editing=true}) {Text("编辑项目信息")} }
            if(current.state=="planning") item { GlassActionButton("确认方案并生成任务",{taskText="";plan=true;editing=true}) }
            else {
                item { Text("${current.progress}% · ${current.done}/${current.tasks.size} 项已完成",color=palette.ink,fontSize=18.sp) }
                items(current.tasks,key={it.id}) { task -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(task.status=="done",onCheckedChange={checked->save(current.copy(tasks=current.tasks.map{if(it.id==task.id)it.copy(status=if(checked)"done" else "todo")else it},accepted=false))},enabled=current.state=="active")
                    Text(task.title,color=palette.ink,fontSize=12.sp,modifier=Modifier.weight(1f))
                } }
                if(current.state=="ready") item { GlassActionButton("开始开发",{save(current.copy(state="active"))}) }
                if(current.state=="active" && current.done==current.tasks.size) item { GlassActionButton("确认验收并结束项目",{save(current.copy(state="completed",accepted=true))}) }
            }
            item { TextButton(onClick={exported=current;exporter.launch("${current.id}.project.json")}){Text("导出项目JSON")}
                Text("项目任务独立于Desktop对话。修改清单不会自动发送、执行或修改GitHub仓库。",color=palette.muted,fontSize=11.sp) }
        }
    }
    if(editing) AlertDialog(onDismissRequest={editing=false},title={Text(if(plan)"确认方案与任务" else "记录项目想法")},text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        if(!plan){OutlinedTextField(title,{title=it.take(120)},label={Text("项目名称")});OutlinedTextField(goal,{goal=it.take(2000)},label={Text("目标与范围")});OutlinedTextField(repo,{repo=it.take(160)},label={Text("GitHub owner/repo（可选）")})}
        else {Text("每行一项任务。请先确认范围与验收标准，确认后进度从0%开始。",fontSize=12.sp);OutlinedTextField(taskText,{taskText=it.take(16000)},label={Text("任务清单")},minLines=4,maxLines=8)}
        if(error.isNotBlank()) Text(error,color=palette.errorInk,fontSize=11.sp)
    }},confirmButton={TextButton(enabled=if(plan)taskText.isNotBlank()else title.isNotBlank(),onClick={
        val p=if(plan&&current!=null)current.copy(state="ready",tasks=taskText.lines().filter{it.isNotBlank()}.mapIndexed{i,t->RepositoryTask("task-${i+1}",t.trim())}) else store?.projects?.firstOrNull{it.id==editingId}?.copy(title=title.trim(),goal=goal.trim(),repository=repo.trim()) ?: RepositoryProject(UUID.randomUUID().toString(),title.trim(),goal.trim(),repo.trim())
        save(p);if(error.isBlank())editing=false
    }){Text(if(plan)"确认并生成"else"保存")}},dismissButton={TextButton(onClick={editing=false}){Text("取消")}})
    imported?.let { p -> AlertDialog(onDismissRequest={imported=null},title={Text("核对导入项目")},text={Text("${p.title}\n${repositoryStates[p.state]} · ${p.done}/${p.tasks.size} 项完成\n${p.repository}\n仅导入确认后的结构化资料；同编号项目将更新。")},confirmButton={TextButton(onClick={save(p);if(error.isBlank())imported=null}){Text("确认导入")}},dismissButton={TextButton(onClick={imported=null}){Text("取消")}}) }
}
private fun java.io.InputStream.readBytesUpTo(limit:Int):ByteArray { val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(4096);while(out.size()<limit){val n=read(buffer,0,minOf(buffer.size,limit-out.size()));if(n<0)break;out.write(buffer,0,n)};return out.toByteArray() }
