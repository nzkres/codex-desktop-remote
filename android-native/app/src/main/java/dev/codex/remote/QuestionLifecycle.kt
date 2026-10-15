package dev.codex.remote

/** A changed/missing question cannot accept an old answer. Disappearance never proves delivery. */
internal fun retainQuestionLifecycle(previous: Conversation?, fresh: Conversation, store: QuestionDraftStore?, threadId: String,
    draftState: (QuestionRequest) -> String? = { store?.read(threadId,it)?.state }): Conversation {
    if(previous == null) return fresh
    val retained=previous.questions.filter { old -> fresh.questions.none { it.id==old.id && it.revision==old.revision } }.map { old ->
        val state=draftState(old)
        when {
            state in setOf("submitting","unknown") -> old.copy(status="unknown",notice="提交结果待核对；请刷新，不要重复提交。")
            old.status in setOf("accepted","closed","expired") -> old
            else -> old.copy(status="expired",notice="原问题已变化或不再等待回答。旧答案保留但不能提交，请核对最新对话。")
        }
    }
    return fresh.copy(questions=(fresh.questions+retained).take(24))
}
