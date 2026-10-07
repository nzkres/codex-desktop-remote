package dev.codex.remote

internal const val MAX_COPY_CHARACTERS = 200_000

internal class CopyContentException(message: String) : IllegalStateException(message)

/** Copy source text, never visual labels, shortened code rows, or Markdown-rendered spans. */
internal fun contentCopyText(item: MessageItem): String = when (item.type) {
    "commandExecution" -> listOf(item.command, item.output).filter { it.isNotEmpty() }.joinToString("\n\n")
    "fileChange" -> item.changes.joinToString("\n\n") { "${it.path}\n${it.diff}" }
    else -> item.text
}

internal suspend fun resolveCopyText(item: MessageItem, loadFull: suspend (MessageItem) -> MessageItem): String {
    val full = if (item.truncated) loadFull(item) else item
    if (full.id != item.id || full.type != item.type || full.truncated) {
        throw CopyContentException("未取得完整内容，请刷新后重试")
    }
    val text = contentCopyText(full)
    if (text.isEmpty()) throw CopyContentException("暂无可复制的内容")
    // Leave room for Android's clipboard Binder transaction. Never silently truncate.
    if (text.length > MAX_COPY_CHARACTERS) throw CopyContentException("内容过大，暂不支持一次复制超过 20 万字符")
    return text
}
