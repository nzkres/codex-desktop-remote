package dev.codex.remote

internal data class HeaderActivity(val subtitle: String, val running: Boolean = false)

private val terminalEscape = Regex("\u001B\\[[0-?]*[ -/]*[@-~]")
internal fun headerPreview(source: String, tail: Boolean = false): String {
    val bounded = if (tail) source.takeLast(2048) else source.take(2048)
    val clean = terminalEscape.replace(userDisplayText(bounded), "")
    val lines = clean.lineSequence().map { line -> line.filter { !it.isISOControl() || it == '\t' }.trim() }.filter { it.isNotEmpty() }
    val line = (if (tail) lines.lastOrNull() else lines.firstOrNull()).orEmpty().replace('\t', ' ')
    val end = line.offsetByCodePoints(0, line.codePointCount(0, line.length).coerceAtMost(120))
    return line.substring(0, end)
}

/** Read the real snapshot, never the delayed presentation queue, and never reuse an old turn's job. */
internal fun headerActivity(session: Session?, conversation: Conversation?): HeaderActivity {
    if (conversation == null) return HeaderActivity("等待同步")
    val activity = transcriptActivity(session, conversation)
    if (activity?.animated != true) return HeaderActivity(activity?.label ?: when (session?.status) {
        "FAILED", "ERROR" -> "任务出现错误"
        "STOPPED", "INTERRUPTED" -> "任务已停止"
        "COMPLETED" -> "任务已完成"
        else -> "等待开始"
    })
    val turn = if (session?.activeTurnId != null) conversation.turns.find { it.id == session.activeTurnId }
        else conversation.turns.lastOrNull()
    if (turn == null) return HeaderActivity("正在处理", running = true)
    val item = turn.items.lastOrNull { it.status == "inProgress" && it.type in setOf("commandExecution", "fileChange") }
    val preview = when (item?.type) {
        "commandExecution" -> {
            val output = headerPreview(item.output, tail = true)
            if (output.isNotBlank()) "输出 · $output" else headerPreview(item.command).takeIf { it.isNotBlank() }?.let { "命令 · $it" }
        }
        "fileChange" -> item.changes.firstOrNull()?.path?.let { "修改 · ${headerPreview(it)}" }
        else -> if (conversation.observation.lastItemType in setOf("agentMessage", "plan"))
            turn.items.lastOrNull { it.type in setOf("agentMessage", "plan") }?.text
                ?.let { headerPreview(it, tail = true) }?.takeIf { it.isNotBlank() }?.let { "回复 · $it" }
            else null
    }
    return HeaderActivity(preview ?: activity.label, running = true)
}
