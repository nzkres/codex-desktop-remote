package dev.codex.remote

/** Synthetic UI state only. Never passed to Gateway, DraftStore or receipt stores. */
internal enum class PreviewPhase(val label: String) {
    Idle("待发送"), Sending("提交中"), Received("电脑已收到"), Running("运行中"),
    Completed("已完成"), Failed("发送失败 · 未送达"), Unknown("回执待核对")
}
internal enum class PreviewOutcome(val label: String) {
    Success("成功"), Failure("未送达"), Unknown("回执未知")
}
internal data class InterfacePreviewState(
    val phase: PreviewPhase = PreviewPhase.Running,
    val revision: Int = 0,
    val seenRevision: Int = 0,
    val draft: String = "继续检查下一步。",
) {
    val unread: Boolean get() = revision > seenRevision
    val canSend: Boolean get() = phase !in setOf(PreviewPhase.Sending, PreviewPhase.Received,
        PreviewPhase.Running, PreviewPhase.Unknown) && draft.isNotBlank()
    fun complete() = copy(phase = PreviewPhase.Completed, revision = revision + 1)
    fun seeResult(visible: Boolean, foreground: Boolean) =
        if (phase == PreviewPhase.Completed && visible && foreground) copy(seenRevision = revision) else this
    fun submit() = if (canSend) copy(phase = PreviewPhase.Sending) else this
    fun receive(outcome: PreviewOutcome) = if (phase != PreviewPhase.Sending) this else when(outcome) {
        PreviewOutcome.Success -> copy(phase = PreviewPhase.Received, draft = "")
        PreviewOutcome.Failure -> copy(phase = PreviewPhase.Failed)
        PreviewOutcome.Unknown -> copy(phase = PreviewPhase.Unknown)
    }
}
