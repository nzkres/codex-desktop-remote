package dev.codex.remote

data class SyncEntry(val key: String, val revision: String, val turnId: String, val turnStatus: String, val item: MessageItem)

/** Atomic merge: no duplicates, no lost pages, no downgrade of explicitly downloaded full text. */
class ConversationWindow {
    var cursor: String = ""; private set
    var current: Conversation? = null; private set
    private var entries = linkedMapOf<String, SyncEntry>()

    fun applyPage(nextCursor: String, reset: Boolean, order: List<String>, changes: List<SyncEntry>, metadata: Conversation): Conversation {
        require(order.toSet().size == order.size && changes.map { it.key }.toSet().size == changes.size) { "同步消息编号重复" }
        val next = if (reset) linkedMapOf() else LinkedHashMap(entries)
        changes.forEach { change ->
            val full = next[change.key]?.takeIf { !it.item.truncated && (it.revision == change.revision ||
                it.item.contentRevision.isNotBlank() && it.item.contentRevision == change.item.contentRevision) }
            // A turn's timing/footer can change without changing its downloaded original.
            next[change.key] = if (full != null) change.copy(item = change.item.copy(text = full.item.text,
                command = full.item.command, output = full.item.output, cwd = full.item.cwd, changes = full.item.changes,
                truncated = false, fullBytes = full.item.fullBytes)) else change
        }
        require(order.all { it in next }) { "同步消息不完整，请重新打开对话" }
        val ordered = order.associateWithTo(linkedMapOf()) { next.getValue(it) }
        val conversation = render(ordered, metadata)
        entries = ordered; cursor = nextCursor; current = conversation
        return conversation
    }

    fun expand(entry: SyncEntry): Conversation {
        require(entry.key in entries) { "消息已变化，请刷新后再试" }
        entries[entry.key] = entry
        return render(entries, requireNotNull(current)).also { current = it }
    }

    private fun render(items: Map<String, SyncEntry>, metadata: Conversation): Conversation = metadata.copy(
        turns = items.values.groupBy { it.turnId }.map { (id, values) -> Turn(id, values.last().turnStatus, values.map { it.item }) },
    )
}
