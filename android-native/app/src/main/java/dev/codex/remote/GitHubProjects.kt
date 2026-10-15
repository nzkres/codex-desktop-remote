package dev.codex.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Base64
import java.security.MessageDigest

internal data class GitHubSource(val repository: String, val commit: String, val paths: List<String>, val syncedAt: Long)
internal fun validRepositoryName(value: String): Boolean = value.matches(Regex("[A-Za-z0-9_-][A-Za-z0-9_.-]*/[A-Za-z0-9_-][A-Za-z0-9_.-]*"))
internal fun validProjectPath(value: String): Boolean = value.length in 1..240 && !value.startsWith('/') &&
    value.split('/').all { it.isNotBlank() && it != "." && it != ".." && it.none { c -> c == '\\' || c.code < 32 || c in "?#" } }

/** Only checklist records are tasks. Prose, code blocks, archived flags and release counts are not progress. */
internal fun projectFromMarkdown(repository: String, documents: List<Pair<String, String>>, commit: String, now: Long): RepositoryProject {
    require(validRepositoryName(repository) && commit.matches(Regex("[a-f0-9]{40}"))) { "GitHub来源无效。" }
    require(documents.isNotEmpty() && documents.size <= 4)
    val tasks = linkedMapOf<String, RepositoryTask>()
    val stages = linkedMapOf<String, RepositoryTask>()
    val declarations = mutableListOf<Map<String, String>>()
    var reported: Pair<Int,Int>? = null
    var ambiguousStages = false
    val summary = mutableListOf<String>()
    var title = repository.substringAfter('/')
    var titleFound = false
    documents.forEach { (path, text) ->
        require(validProjectPath(path) && text.toByteArray().size <= 131072) { "计划文档过大或路径无效。" }
        var fence: String? = null
        var stageTable = false
        val readme = path.substringAfterLast('/').equals("README.md",true)
        var taskSection = !readme
        var sectionDepth = 0
        val documentIds = mutableSetOf<String>()
        text.lineSequence().forEach lineLoop@{ raw ->
            val line = raw.trim()
            val delimiter = Regex("^(`{3,}|~{3,})").find(line)?.value
            if (delimiter != null) { if (fence == null) fence = delimiter else if (delimiter.first() == fence!!.first() && delimiter.length >= fence!!.length) fence = null; return@lineLoop }
            if (fence != null) return@lineLoop
            Regex("^(#{1,6}) (.+)$").matchEntire(line)?.let { heading ->
                val depth=heading.groupValues[1].length
                if(readme && (sectionDepth==0 || depth<=sectionDepth)) {
                    taskSection=heading.groupValues[2].trim().lowercase() in setOf("项目计划","任务清单","开发进度","项目进度","project plan","tasks","progress","roadmap")
                    sectionDepth=if(taskSection) depth else 0
                }
            }
            if (line.startsWith("- 当前阶段：") || line.startsWith("- 总体进度：") || line.startsWith("- 当前目标：")) summary.add(line.removePrefix("- ").take(500))
            Regex("总体进度[：:]\\s*(\\d+)\\s*/\\s*(\\d+)").find(line)?.let { reported = it.groupValues[1].toInt() to it.groupValues[2].toInt() }
            val cells = if(line.startsWith('|')) line.trim('|').split('|').map { it.trim() } else emptyList()
            if (cells == listOf("阶段","内容","状态")) stageTable = true
            else if (!line.startsWith('|')) stageTable = false
            else if (stageTable && cells.size == 3 && cells[0].matches(Regex("[0-9]+"))) {
                val state = when {
                    cells[2] in setOf("done","已完成","已验收") || cells[2].startsWith("已完成，") -> "done"
                    cells[2] in setOf("todo","未开始","待开始") -> "todo"
                    cells[2] == "doing" || cells[2].startsWith("进行中") || cells[2].startsWith("已接入") -> "doing"
                    else -> { ambiguousStages = true; "todo" }
                }
                require(cells[1].length in 1..500 && stages.size < 200)
                val id = "stage-${cells[0]}"; stages[id] = RepositoryTask(id,cells[0]+" · "+cells[1],state)
            }
            if (!titleFound && line.startsWith("# ")) { title = line.removePrefix("# ").take(120); titleFound = true }
            Regex("^<!--\\s*desktop-remote:\\s*(.*?)\\s*-->$").matchEntire(line)?.let { match ->
                val fields = match.groupValues[1].split(';').associate { field ->
                    val pair = field.trim().split('=', limit = 2); require(pair.size == 2) { "项目声明格式无效。" }; pair[0].trim() to pair[1].trim()
                }
                require(fields.keys.all { it in setOf("state", "plan", "acceptance") }) { "项目声明存在未知字段。" }
                declarations.add(fields)
            }
            Regex("^[-*+] \\[([ xX~])\\] (.+)$").matchEntire(line)?.takeIf { taskSection }?.let { match ->
                val label = match.groupValues[2].trim(); require(label.length <= 500) { "任务文字超过500字。" }
                // Same explicit task ID in PLAN and PROGRESS means one task; later progress wins.
                val explicit = Regex("^(?:`)?([A-Za-z]+[A-Za-z0-9]*-?[0-9]+(?:-[0-9]+)*)(?:`)?(?:[ :：.、]|$)").find(label)?.groupValues?.get(1)
                val key = explicit?.uppercase() ?: MessageDigest.getInstance("SHA-256").digest(label.toByteArray()).joinToString("") { "%02x".format(it) }
                require(documentIds.add(key)) { "同一文档中的任务编号重复，请核对。" }
                tasks[key] = RepositoryTask(key, label, when(match.groupValues[1].lowercase()) { "x" -> "done"; "~" -> "doing"; else -> "todo" })
                require(tasks.size <= 200) { "单项目最多200项任务。" }
            }
        }
    }
    val usingStages = tasks.isEmpty() && stages.isNotEmpty()
    if (usingStages) tasks.putAll(stages)
    val fields = mutableMapOf<String, String>()
    declarations.forEach { declaration -> declaration.forEach { (key, value) -> require(fields[key] == null || fields[key] == value) { "计划和进度声明冲突，请在GitHub核对。" }; fields[key] = value } }
    fields["plan"]?.let { require(it in setOf("draft", "confirmed")) { "方案状态无效。" } }
    fields["acceptance"]?.let { require(it in setOf("pending", "accepted")) { "验收状态无效。" } }
    val inconsistent = usingStages && (ambiguousStages || reported?.let { it.first != tasks.values.count { task -> task.status == "done" } || it.second != tasks.size } == true)
    val inferred = when { tasks.isEmpty() || inconsistent -> "review"; tasks.values.all { it.status == "todo" } -> "ready"; else -> "active" }
    val state = fields["state"] ?: inferred
    require(state in repositoryStates && state != "all") { "项目状态声明无效。" }
    require(fields["plan"] != "draft" || state == "planning") { "草案不能显示已确认的任务进度。" }
    require(!inconsistent || state == "review") { "阶段表与总体进度声明不一致，请核对。" }
    require(state !in setOf("ready", "active", "completed") || tasks.isNotEmpty()) { "已确认项目缺少任务清单。" }
    val id = "github-" + MessageDigest.getInstance("SHA-256").digest(repository.lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
    val goal = (summary.distinct().take(3) + if(inconsistent) listOf("阶段状态与总体进度尚不一致，保留文档声明并等待核对。") else emptyList()).joinToString("\n").take(2000)
    val project = RepositoryProject(id, title, goal, repository, state, tasks.values.toList(), fields["acceptance"] == "accepted",
        GitHubSource(repository, commit, documents.map { it.first }, now))
    return parseRepositoryProject(repositoryJson(project).toString())
}

/** Read-only REST transport. Never follows redirects or forwards a credential to file/download URLs. */
internal class GitHubProjects(private val token: String, private val fetch: ((String) -> String)? = null) {
    private fun get(path: String): String {
        require(path.startsWith("/repos/") || path.startsWith("/user/repos?"))
        fetch?.let { return it(path) }
        val connection = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = false; connection.connectTimeout = 10000; connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        connection.setRequestProperty("User-Agent", "Desktop-Remote-Lite")
        if (token.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $token")
        try {
            when(val status = connection.responseCode) {
                200 -> return connection.inputStream.use { stream ->
                    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                    while(output.size() < 524289) { val count = stream.read(buffer, 0, minOf(buffer.size, 524289-output.size())); if(count < 0) break; output.write(buffer,0,count) }; val bytes = output.toByteArray(); require(bytes.size <= 524288) { "GitHub响应超过512KiB。" }; String(bytes, Charsets.UTF_8)
                }
                401 -> error("GitHub凭据已失效，请重新设置只读令牌。")
                403, 429 -> error(if (connection.getHeaderField("X-RateLimit-Remaining") == "0" || status == 429) "GitHub请求额度已用完，请稍后同步。" else "GitHub拒绝访问，请检查仓库只读权限或组织授权。")
                404 -> throw GitHubMissing()
                in 300..399 -> error("GitHub仓库地址已变更，请重新关联；未转发凭据。")
                else -> error("GitHub暂时无法读取（HTTP $status），保留上次资料。")
            }
        } finally { connection.disconnect() }
    }
    suspend fun repositories(page: Int = 1): List<String> = withContext(Dispatchers.IO) {
        require(token.isNotBlank() && page in 1..100)
        val rows = JSONArray(get("/user/repos?per_page=30&page=$page&sort=updated&affiliation=owner,collaborator,organization_member"))
        (0 until rows.length()).map { rows.getJSONObject(it).getString("full_name") }.filter(::validRepositoryName)
    }
    suspend fun read(repository: String, paths: List<String> = emptyList()): RepositoryProject = withContext(Dispatchers.IO) {
        require(validRepositoryName(repository)) { "请填写GitHub owner/repo。" }
        require(paths.size <= 4 && paths.all(::validProjectPath)) { "最多4个仓库内文档路径。" }
        try {
            val commits = JSONArray(get("/repos/$repository/commits?per_page=1"))
            check(commits.length()>0) { "GitHub仓库尚无提交，请先在仓库保存计划文档。" }
            val sha = commits.getJSONObject(0).getString("sha"); require(sha.matches(Regex("[a-f0-9]{40}")))
            fun content(path: String) = get("/repos/$repository/contents/${path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }}?ref=$sha")
            val selected = if (paths.isNotEmpty()) paths else {
                val available = mutableListOf<String>()
                listOf("", "docs").forEach { directory ->
                    val listing = try { JSONArray(content(directory)) } catch (_: GitHubMissing) { null }
                    if (listing != null) (0 until listing.length()).forEach { index ->
                        val row = listing.getJSONObject(index); val path = row.optString("path")
                        if (row.optString("type") == "file" && validProjectPath(path)) available.add(path)
                    }
                }
                val preferred = listOf("PLAN.md", "ROADMAP.md", "docs/PLAN.md", "PROGRESS.md", "docs/PROGRESS.md")
                preferred.filter { candidate -> available.any { it.equals(candidate, true) } }.map { candidate -> available.first { it.equals(candidate, true) } }.take(4)
                    .ifEmpty { available.firstOrNull { it.equals("README.md", true) }?.let(::listOf) ?: emptyList() }
            }
            if (selected.isEmpty()) {
                val id = "github-" + MessageDigest.getInstance("SHA-256").digest(repository.lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
                return@withContext RepositoryProject(id, repository.substringAfter('/'), "未找到约定的计划文档，请指定路径或在GitHub补充。", repository, "review", source=GitHubSource(repository,sha,emptyList(),System.currentTimeMillis()))
            }
            val documents = selected.map { path ->
                val row = JSONObject(content(path))
                require(row.optString("type") == "file" && row.optString("encoding") == "base64" && row.optInt("size", Int.MAX_VALUE) <= 131072 && row.optString("path") == path) { "计划应为不超过128KiB的普通UTF-8文件。" }
                val encoded = row.getString("content"); require(encoded.length <= 180000)
                val bytes = Base64.getMimeDecoder().decode(encoded); require(bytes.size <= 131072)
                path to Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            }
            projectFromMarkdown(repository, documents, sha, System.currentTimeMillis())
        } catch (_: GitHubMissing) { error("GitHub仓库或文档不存在，或当前凭据无权读取；保留上次资料。") }
    }
}
internal class GitHubMissing : Exception()
