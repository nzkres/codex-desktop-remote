package dev.codex.remote

/** Display state only: no timers, network requests, or inferred command completion. */
internal data class TranscriptActivity(val label: String, val animated: Boolean = false)

internal fun sessionProcessing(session: Session, observation: Observation?): Boolean =
    session.status !in setOf("WAITING_USER", "WAITING_APPROVAL", "COMPLETED", "INTERRUPTED", "FAILED") &&
        observation?.state != "quiet" && (observation?.state == "running" || session.activeTurnId != null || session.status == "RUNNING")

internal fun transcriptActivity(session: Session?, conversation: Conversation?): TranscriptActivity? {
    if (conversation == null) return null
    if (conversation.approvals.isNotEmpty() || session?.status == "WAITING_APPROVAL")
        return TranscriptActivity("等待你的批准")
    if (conversation.questions.isNotEmpty() || session?.status == "WAITING_USER")
        return TranscriptActivity("等待你的回答")
    val observation = conversation.observation
    if (observation?.state == "quiet") return TranscriptActivity("等待新的活动记录")
    val running = observation?.state == "running" || session?.activeTurnId != null
    if (!running) return null
    // Only the newest turn can describe current work; never animate old history.
    val active = conversation.turns.lastOrNull()?.items?.lastOrNull { it.status == "inProgress" }
    return TranscriptActivity(when (active?.type) {
        "commandExecution" -> "正在运行命令"
        "fileChange" -> "正在修改文件"
        else -> if (observation?.lastItemType == "reasoning") "正在思考" else "正在处理"
    }, animated = true)
}

internal fun toolSummaryLabel(item: MessageItem): String {
    val command = item.type == "commandExecution"
    return when {
        item.status == "failed" || (command && item.exitCode != null && item.exitCode != 0) ->
            if (command) "命令执行失败" else "文件修改失败"
        item.status == "inProgress" -> if (command) "正在运行" else "正在修改"
        item.status == "completed" -> if (command) "已运行" else "已修改"
        else -> if (command) "命令" else "文件修改"
    }
}
