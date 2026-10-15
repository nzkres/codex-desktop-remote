package dev.codex.remote

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class GitHubProjectsTest {
    private val sha="a".repeat(40)
    private fun parse(text:String)=projectFromMarkdown("example/project",listOf("PROGRESS.md" to text),sha,1000)
    @Test fun checklistProgressDoesNotRequireImportedJson() {
        val p=parse("# Project\n- [x] T1 实现\n- [ ] T2 验证")
        assertEquals("active",p.state);assertEquals(50,p.progress);assertEquals(sha,p.source!!.commit)
        assertEquals(p,parseRepositoryProject(repositoryJson(p).toString()))
    }
    @Test fun noDocumentEvidenceIsReviewNotPlanning() {
        assertEquals("review",parse("# README\nSome text and 100 releases").state)
        assertNull(parse("# README").progress)
        assertEquals("planning",parse("<!-- desktop-remote: state=planning; plan=draft -->").state)
    }
    @Test fun zeroAndAllCheckedDoNotFabricateAcceptance() {
        assertEquals("ready",parse("- [ ] T1 Test").state)
        assertEquals("active",parse("- [x] T1 Test").state)
        assertThrows(IllegalArgumentException::class.java){parse("<!-- desktop-remote: state=completed -->\n- [x] T1 Test")}
        assertEquals("completed",parse("<!-- desktop-remote: state=completed; acceptance=accepted -->\n- [x] T1 Test").state)
    }
    @Test fun draftAndConflictingDeclarationsCannotBecomeConfirmedProgress() {
        assertThrows(IllegalArgumentException::class.java){parse("<!-- desktop-remote: plan=draft -->\n- [x] T1 Test")}
        assertThrows(IllegalArgumentException::class.java){projectFromMarkdown("example/project",listOf("PLAN.md" to "<!-- desktop-remote: state=planning -->","PROGRESS.md" to "<!-- desktop-remote: state=active -->\n- [x] T1 Test"),sha,1000)}
    }
    @Test fun codeExamplesAreNotTasksAndProgressOverridesSameTaskId() {
        val p=projectFromMarkdown("example/project",listOf("PLAN.md" to "- [ ] T1 Design\n```md\n- [x] T9 Example\n```","PROGRESS.md" to "- [x] T1 Design complete"),sha,1000)
        assertEquals(1,p.tasks.size);assertEquals(100,p.progress)
        assertThrows(IllegalArgumentException::class.java){parse("- [ ] T1 A\n- [x] T1 B")}
    }
    @Test fun stageTablesAreReadAndContradictionsStayVisible() {
        val table="| 阶段 | 内容 | 状态 |\n|---|---|---|\n| 0 | 设计 | 已完成 |\n| 1 | 实现 | 进行中 |\n| 2 | 发布 | 未开始 |"
        val p=parse(table);assertEquals(33,p.progress);assertEquals("active",p.state)
        val bad=parse("- 总体进度：2 / 3 个阶段完成\n$table")
        assertEquals("review",bad.state);assertNull(bad.progress);assertTrue(bad.goal.contains("2 / 3"))
    }
    @Test fun unsafeSourcePathsAreRejected() {
        listOf("../PLAN.md","/PLAN.md","docs/../PLAN.md","docs\\PLAN.md","PLAN.md?token=x").forEach { assertFalse(validProjectPath(it)) }
        assertFalse(validRepositoryName("../repo"));assertFalse(validRepositoryName("owner/repo?x"))
    }
    @Test fun readmeInstallationChecklistsAreNotProjectProgress() {
        val p=projectFromMarkdown("example/project",listOf("README.md" to "# 项目\n## 安装\n- [x] 安装依赖\n## 项目计划\n- [ ] T1 开发\n### 实现细节\n- [ ] T2 验收\n## 致谢\n- [x] 感谢"),sha,1000)
        assertEquals(2,p.tasks.size);assertEquals("ready",p.state);assertEquals(0,p.progress)
    }
    @Test fun githubFetchPinsEveryFileToOneCommitAndOnlyReads()= runBlocking {
        val calls=mutableListOf<String>()
        val client=GitHubProjects("synthetic-read-token") { path ->
            calls.add(path)
            when {
                path.endsWith("/commits?per_page=1") -> JSONArray().put(JSONObject().put("sha",sha)).toString()
                path=="/repos/example/project/contents/?ref=$sha" -> "[{\"type\":\"file\",\"path\":\"PROGRESS.md\"}]"
                path=="/repos/example/project/contents/docs?ref=$sha" -> "[]"
                path=="/repos/example/project/contents/PROGRESS.md?ref=$sha" -> JSONObject().put("path","PROGRESS.md").put("type","file").put("encoding","base64").put("size",15).put("content",Base64.getEncoder().encodeToString("- [x] T1 Test".toByteArray())).toString()
                else -> error("unexpected path")
            }
        }
        val p=client.read("example/project");assertEquals(100,p.progress)
        assertTrue(calls.filter { "/contents/" in it }.all { it.endsWith("?ref=$sha") })
        assertFalse(calls.any { "synthetic-read-token" in it })
    }
    @Test fun missingFilesAndSymlinksNeverProduceAnEmptyProject()=runBlocking {
        val missing=GitHubProjects(""){if(it.contains("commits")) "[{\"sha\":\"$sha\"}]" else throw GitHubMissing()}
        try { missing.read("example/project",listOf("PLAN.md"));fail("missing document accepted") }catch(e:IllegalStateException){assertTrue(e.message!!.contains("保留上次"))}
        val symlink=GitHubProjects(""){if(it.contains("commits")) "[{\"sha\":\"$sha\"}]" else "{\"type\":\"symlink\"}"}
        try { symlink.read("example/project",listOf("PLAN.md"));fail("symlink accepted") }catch(_:IllegalArgumentException){}
    }
}
