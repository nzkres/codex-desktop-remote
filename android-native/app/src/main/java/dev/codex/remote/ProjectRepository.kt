package dev.codex.remote

import android.content.Context
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class RepositoryTask(val id: String, val title: String, val status: String = "todo")
internal data class RepositoryProject(val id: String, val title: String, val goal: String, val repository: String,
    val state: String = "planning", val tasks: List<RepositoryTask> = emptyList(), val accepted: Boolean = false, val source: GitHubSource? = null) {
    val done: Int get() = tasks.count { it.status == "done" }
    val progress: Int? get() = if (tasks.isEmpty() || state == "review") null else (100f * done / tasks.size).roundToInt()
}
internal val repositoryStates = linkedMapOf("all" to "全部", "review" to "待核对", "planning" to "构思中", "ready" to "待开始", "active" to "进行中", "completed" to "已结束")

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
    require(state in setOf("planning", "review") || (tasks.isNotEmpty() && planConfirmed)) { "请先确认方案并生成任务。" }
    require(state != "planning" || tasks.isEmpty()) { "构思阶段请先核对任务，确认方案后再导入。" }
    require(state != "ready" || tasks.all { it.status == "todo" }) { "待开始项目不能包含已执行任务。" }
    require(state != "completed" || (accepted && tasks.all { it.status == "done" })) { "任务全部完成并确认验收后，才能标记已结束。" }
    val repo = value.optJSONObject("repository")?.optString("fullName").orEmpty()
    require(repo.isEmpty() || repo.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+"))) { "仓库名称应为owner/repo。" }
    val source = value.optJSONObject("githubSource")?.let { row ->
        val commit = row.getString("commit"); val paths = row.getJSONArray("paths")
        require(validRepositoryName(repo) && commit.matches(Regex("[a-f0-9]{40}")) && paths.length() <= 4 && row.getLong("syncedAt") > 0)
        val files = (0 until paths.length()).map { paths.getString(it) }; require(files.all(::validProjectPath))
        GitHubSource(repo, commit, files, row.getLong("syncedAt"))
    }
    return RepositoryProject(id, text("title", 120), text("goal", 2000, false), repo, state, tasks, accepted, source)
}
internal fun repositoryJson(p: RepositoryProject): JSONObject = JSONObject().put("schemaVersion", 1).put("id", p.id)
    .put("title", p.title).put("goal", p.goal).put("repository", JSONObject().put("provider", "github").put("fullName", p.repository))
    .put("state", p.state).put("plan", JSONObject().put("state", if (p.state == "planning") "draft" else "confirmed"))
    .put("tasks", JSONArray(p.tasks.map { JSONObject().put("id", it.id).put("title", it.title).put("status", it.status) }))
    .put("acceptance", JSONObject().put("state", if (p.accepted) "accepted" else "pending"))
    .apply { p.source?.let { put("githubSource", JSONObject().put("commit", it.commit).put("paths", JSONArray(it.paths)).put("syncedAt", it.syncedAt)) } }

internal class ProjectRepositoryStore(private val prefs: SharedPreferences) {
    var projects by mutableStateOf(run {
        val rows = JSONArray(prefs.getString("projects", "[]")); require(rows.length() <= 100)
        (0 until rows.length()).map { parseRepositoryProject(rows.getJSONObject(it).toString()) }
    }); private set
    fun githubToken(): String = prefs.getString("github-read-token", "").orEmpty()
    fun setGithubToken(value: String) {
        require(value.length <= 512 && value.none { it.isWhitespace() || it.code < 32 }) { "GitHub令牌格式无效。" }
        check(prefs.edit().putString("github-read-token", value).commit()) { "GitHub凭据未能加密保存。" }
    }
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
    val scope = rememberCoroutineScope(); val lifecycle = LocalLifecycleOwner.current
    var githubOpen by remember(store) { mutableStateOf(false) }
    var tokenInput by remember(store) { mutableStateOf("") }
    var githubRepo by remember(store) { mutableStateOf("") }
    var githubPaths by remember(store) { mutableStateOf("") }
    var githubRepos by remember(store) { mutableStateOf<List<String>>(emptyList()) }
    var githubPage by remember(store) { mutableIntStateOf(1) }
    var githubBusy by remember(store) { mutableStateOf(false) }
    var githubErrors by remember(store) { mutableStateOf<Map<String,String>>(emptyMap()) }
    var githubError by remember(store) { mutableStateOf("") }
    var tokenRevision by remember(store) { mutableIntStateOf(0) }
    val githubLock = remember(store) { Mutex() }
    suspend fun syncGithub(repository: String, paths: List<String>) = githubLock.withLock {
        if (store == null) return@withLock false
        githubBusy = true
        try {
            val p = withTimeout(60000) { GitHubProjects(store.githubToken()).read(repository, paths) }
            store.save(p); githubErrors = githubErrors - repository; githubError = ""; true
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) { githubError="GitHub读取超时，保留上次资料。"; githubErrors=githubErrors+(repository to githubError); false }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { val message = e.message ?: "GitHub同步失败，保留上次资料。"; githubErrors = githubErrors + (repository to message); githubError = message; false }
        finally { githubBusy = false }
    }
    LaunchedEffect(store, lifecycle, tokenRevision) {
        val attempts = mutableMapOf<String,Long>()
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                val age = if (store?.githubToken().isNullOrBlank()) 900000L else 300000L
                val p = store?.projects?.filter { it.source != null }?.minByOrNull { maxOf(it.source!!.syncedAt, attempts[it.repository] ?: 0L) }
                if (p != null && now - maxOf(p.source!!.syncedAt, attempts[p.repository] ?: 0L) >= age && !githubBusy) {
                    attempts[p.repository] = now
                    syncGithub(p.repository, p.source!!.paths)
                }
                delay(60000L)
            }
        }
    }
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
            else { TextButton(onClick = { githubOpen = true }, enabled = store != null) { Text("GitHub") }
                TextButton(onClick = { importer.launch(arrayOf("application/json", "text/plain")) }, enabled = store != null) { Text("导入") }
                TextButton(onClick = { editingId=null;title="";goal="";repo="";plan=false;editing=true }, enabled = store != null) { Text("新建") } }
        } }
        if (error.isNotBlank()) item { Text(error, color = palette.errorInk, fontSize = 12.sp) }
        if (store == null) item { Text("本机加密项目存储不可用，未创建或修改项目。", color = palette.errorInk) }
        else if (current == null) {
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { repositoryStates.forEach { (key, label) ->
                TextButton(onClick = { filter = key }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(3.dp)) { Text(label, color = if (filter == key) palette.ink else palette.muted, fontSize = 11.sp) }
            } } }
            val rows = store.projects.filter { filter == "all" || it.state == filter }.sortedBy { listOf("review","active","planning","ready","completed").indexOf(it.state) }
            if (rows.isEmpty()) item { Text("关联GitHub，自动读取计划与进度；也可先记录想法。\n私有仓库使用本机加密保存的只读令牌。", color = palette.muted, fontSize = 12.sp) }
            items(rows, key = { it.id }) { p ->
                DecisionSurface { Column(Modifier.fillMaxWidth().clickable { selected = p.id }.padding(12.dp)) {
                    Row { Text(p.title, Modifier.weight(1f), color=palette.ink,fontSize=14.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(repositoryStates[p.state].orEmpty(),color=if(p.state=="planning") palette.muted else palette.green,fontSize=11.sp) }
                    if(p.goal.isNotBlank()) Text(p.goal,Modifier.padding(top=5.dp),color=palette.muted,fontSize=10.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                    Spacer(Modifier.height(8.dp))
                    Row { Text(if(p.progress==null) "方案未确认 · 尚无任务进度" else "${p.done} / ${p.tasks.size} 项完成",Modifier.weight(1f),color=palette.muted,fontSize=11.sp)
                        Text(p.progress?.let { "$it%" } ?: if(p.state=="review") "待核对" else "待构思",color=palette.ink,fontSize=16.sp) }
                    p.progress?.let { LinearProgressIndicator(progress={it/100f},modifier=Modifier.fillMaxWidth().padding(top=7.dp).height(3.dp),color=palette.green,trackColor=palette.muted.copy(alpha=.1f)) }
                    p.source?.let { Text("GitHub · ${android.text.format.DateFormat.format("MM-dd HH:mm", it.syncedAt)} · ${it.commit.take(7)}", color=palette.muted,fontSize=10.sp) }
                    githubErrors[p.repository]?.let { Text("同步失败 · $it",color=palette.errorInk,fontSize=10.sp) }
                    Text(when(p.state){"review"->"未识别计划 · 指定文档或补充状态约定";"planning"->"下一步：确认范围与验收标准";"ready"->"任务已生成 · 等待开始";"completed"->"已验收归档 · 查看交付记录";else->"当前："+(p.tasks.firstOrNull{it.status!="done"}?.title?:"等待验收")},Modifier.padding(top=9.dp),color=palette.muted,fontSize=11.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                } }
            }
        } else {
            item { Text(current.title,color=palette.ink,fontSize=18.sp);Text(current.goal,color=palette.muted,fontSize=12.sp)
                if(current.repository.isNotBlank()) Text(current.repository,color=palette.green,fontSize=12.sp)
                Text(repositoryStates[current.state].orEmpty(),Modifier.padding(vertical=9.dp),color=palette.muted,fontSize=12.sp) }
            if(current.source == null) item { TextButton(onClick={editingId=current.id;title=current.title;goal=current.goal;repo=current.repository;plan=false;editing=true}) {Text("编辑项目信息")} }
            if(current.source==null && current.state=="planning") item { GlassActionButton("确认方案并生成任务",{taskText="";plan=true;editing=true}) }
            else {
                item { Text((current.progress?.let { "$it%" } ?: "待核对")+" · ${current.done}/${current.tasks.size} 项已完成",color=palette.ink,fontSize=18.sp) }
                items(current.tasks,key={it.id}) { task -> Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(task.status=="done",onCheckedChange={checked->save(current.copy(tasks=current.tasks.map{if(it.id==task.id)it.copy(status=if(checked)"done" else "todo")else it},accepted=false))},enabled=current.source==null && current.state=="active")
                    Text(task.title,color=palette.ink,fontSize=12.sp,modifier=Modifier.weight(1f))
                } }
                if(current.source==null && current.state=="ready") item { GlassActionButton("开始开发",{save(current.copy(state="active"))}) }
                if(current.source==null && current.state=="active" && current.done==current.tasks.size) item { GlassActionButton("确认验收并结束项目",{save(current.copy(state="completed",accepted=true))}) }
            }
            current.source?.let { source ->
                item {
                    Text("GitHub只读 · ${source.commit.take(12)}\n${source.paths.joinToString(" + ")}\n${android.text.format.DateFormat.format("MM-dd HH:mm",source.syncedAt)} 同步",color=palette.muted,fontSize=11.sp)
                    githubErrors[current.repository]?.let { Text(it,color=palette.errorInk,fontSize=11.sp) }
                    Row { TextButton(enabled=!githubBusy,onClick={scope.launch { syncGithub(current.repository,source.paths) }}) { Text(if(githubBusy) "同步中" else "同步进度") }
                        TextButton(onClick={githubRepo=current.repository;githubPaths=source.paths.joinToString("\n");githubOpen=true}) { Text("文档路径") } }
                    Text("进度以GitHub任务清单为准，请在仓库中更新；全部勾选后仍需验收声明。",color=palette.muted,fontSize=11.sp)
                }
            }
            item { TextButton(onClick={exported=current;exporter.launch("${current.id}.project.json")}){Text("导出项目JSON")}
                Text("GitHub项目从仓库读取并定期同步，本机想法独立保存；不会自动发送或执行Desktop任务。",color=palette.muted,fontSize=11.sp) }
        }
    }
    if(githubOpen) AlertDialog(onDismissRequest={if(!githubBusy){githubOpen=false;tokenInput=""}},title={Text("关联 GitHub")},text={
        Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text("公开仓库无需令牌；私有仓库使用指定仓库的 Contents 只读令牌，仅加密保存在手机。",fontSize=11.sp)
            OutlinedTextField(tokenInput,{tokenInput=it.take(512)},label={Text(if(store?.githubToken().isNullOrBlank())"只读令牌（可选）"else"已保存凭据，填写可替换")},visualTransformation=PasswordVisualTransformation(),singleLine=true)
            Row {
                TextButton(enabled=!githubBusy && tokenInput.isNotBlank(),onClick={try{store?.setGithubToken(tokenInput.trim());tokenInput="";tokenRevision++;githubError=""}catch(e:Exception){githubError=e.message.orEmpty()}}){Text("保存凭据")}
                TextButton(enabled=!githubBusy,onClick={store?.setGithubToken("");tokenInput="";tokenRevision++;githubRepos=emptyList()}){Text("移除凭据")}
                TextButton(enabled=!githubBusy && !store?.githubToken().isNullOrBlank(),onClick={scope.launch {
                    githubBusy=true
                    try {githubRepos=GitHubProjects(store!!.githubToken()).repositories(githubPage);githubError=""}
                    catch(e:CancellationException){throw e}catch(e:Exception){githubError=e.message?:"仓库列表读取失败"}finally{githubBusy=false}
                }}){Text("仓库")}
            }
            if(githubRepos.isNotEmpty()) {
                LazyColumn(Modifier.heightIn(max=110.dp)) { items(githubRepos) { name -> Text(name,Modifier.fillMaxWidth().clickable { githubRepo=name;githubPaths="" }.padding(vertical=10.dp),fontSize=12.sp) } }
                TextButton(enabled=!githubBusy,onClick={githubPage++;scope.launch {githubBusy=true;try{githubRepos=GitHubProjects(store!!.githubToken()).repositories(githubPage)}catch(e:CancellationException){throw e}catch(e:Exception){githubError=e.message.orEmpty()}finally{githubBusy=false}}}){Text("下一页")}
            }
            OutlinedTextField(githubRepo,{githubRepo=it.take(160)},label={Text("owner/repo")},singleLine=true)
            OutlinedTextField(githubPaths,{githubPaths=it.take(1000)},label={Text("文档路径，每行一个；留空自动发现")},maxLines=3)
            Text("自动读取 PLAN / ROADMAP / PROGRESS（根目录或docs）；否则读取README。自定义最多4个，进度文档放在最后。",fontSize=10.sp)
            if(githubError.isNotBlank()) Text(githubError,color=palette.errorInk,fontSize=11.sp)
        }
    },confirmButton={TextButton(enabled=!githubBusy && githubRepo.isNotBlank(),onClick={scope.launch {
        val result=syncGithub(githubRepo.trim(),githubPaths.lines().map{it.trim()}.filter{it.isNotEmpty()})
        if(result==true){githubOpen=false;tokenInput=""}
    }}){Text(if(githubBusy)"读取中…"else"关联并同步")}},dismissButton={TextButton(enabled=!githubBusy,onClick={githubOpen=false;tokenInput=""}){Text("关闭")}})
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
