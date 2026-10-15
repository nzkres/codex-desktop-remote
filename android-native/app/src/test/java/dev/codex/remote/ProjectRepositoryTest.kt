package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test

class ProjectRepositoryTest {
    private fun project(state:String="active", accepted:Boolean=false, tasks:List<RepositoryTask> = listOf(RepositoryTask("a","实现", "done"),RepositoryTask("b","验收"))) =
        RepositoryProject("example","项目","目标","owner/repo",state,tasks,accepted)
    @Test fun progressIsTaskBasedAndRoundTrips() {
        val p=project();assertEquals(50,p.progress);assertEquals(p,parseRepositoryProject(repositoryJson(p).toString()))
    }
    @Test fun ideaHasNoInventedProgress(){val p=project("planning",tasks=emptyList());assertNull(p.progress);assertEquals(p,parseRepositoryProject(repositoryJson(p).toString()))}
    @Test fun completionRequiresAcceptance(){
        assertThrows(IllegalArgumentException::class.java){parseRepositoryProject(repositoryJson(project("completed")).toString())}
        val p=project("completed",true,listOf(RepositoryTask("a","完成","done")));assertEquals(p,parseRepositoryProject(repositoryJson(p).toString()))
    }
    @Test fun duplicatedTasksAndUnconfirmedPlanAreRejected(){
        assertThrows(IllegalArgumentException::class.java){parseRepositoryProject(repositoryJson(project(tasks=listOf(RepositoryTask("a","甲"),RepositoryTask("a","乙")))).toString())}
        val raw=repositoryJson(project());raw.getJSONObject("plan").put("state","draft")
        assertThrows(IllegalArgumentException::class.java){parseRepositoryProject(raw.toString())}
    }
    @Test fun deviceFiltersCannotMixUnknownOrWaitingWithRunning(){
        assertFalse(hubMatches("running",null));assertFalse(hubMatches("waiting",null))
        val waiting=HubStatus(true,true,1);assertTrue(hubMatches("waiting",waiting));assertFalse(hubMatches("running",waiting))
    }
    @Test fun expiredQuestionKeepsOriginalOptionsAndAnswerIdentity(){
        val q=QuestionRequest("question",listOf(Question("q","范围","选择",listOf(QuestionOption("甲","说明")),false,false)),"rev","turn")
        val old=Conversation(emptyList(),Observation("running",0,"",""),listOf(q),emptyList())
        val fresh=old.copy(questions=emptyList());val merged=retainQuestionLifecycle(old,fresh,null,"thread")
        assertEquals("expired",merged.questions.single().status);assertEquals(q.questions,merged.questions.single().questions)
        assertEquals(q.revision,merged.questions.single().revision)
    }
    @Test fun disappearanceNeverConfirmsUnknownDelivery(){
        val q=QuestionRequest("question",emptyList(),"rev","turn")
        val old=Conversation(emptyList(),Observation("running",0,"",""),listOf(q),emptyList())
        val merged=retainQuestionLifecycle(old,old.copy(questions=emptyList()),null,"thread"){"unknown"}
        assertEquals("unknown",merged.questions.single().status)
    }
    @Test fun expiryKeepsThePreviousDraftStorageVocabulary(){
        assertEquals("closed",storedQuestionState("expired"))
        assertEquals("expired",restoredQuestionState("closed","问题已失效：题目已更新"))
        assertEquals("unknown",restoredQuestionState("submitting",""))
        assertEquals("closed",restoredQuestionState("closed","已在电脑处理"))
    }
}
