package dev.codex.remote

/** Presentation only. The server snapshot, task status, copy source and sync cursor are never delayed. */
internal data class ReplyReveal(val from: String, val to: String, val startedAt: Long, val duration: Long)
internal data class TranscriptFrame(val items: List<MessageItem>, val reveals: Map<String, ReplyReveal>)

internal fun replyRevealDuration(characters: Int, queued: Int = 1): Long =
    ((320.0 + kotlin.math.sqrt(characters.coerceAtLeast(1).toDouble()) * 62.0) /
        kotlin.math.sqrt(queued.coerceAtLeast(1).toDouble())).toLong().coerceIn(180L, 1800L)

internal class TranscriptSequence {
    private var initialized = false
    private var latest = emptyList<MessageItem>()
    private var lastTurn: String? = null
    private val shown = linkedMapOf<String, MessageItem>()
    private val queued = mutableSetOf<String>()
    private var activeId: String? = null
    private var active: ReplyReveal? = null
    private var deadline = 0L

    fun accept(conversation: Conversation?, enabled: Boolean, now: Long, expanding: Set<String> = emptySet()) {
        if (conversation == null) return
        val old = latest.associateBy { it.id }
        latest = conversation.turns.flatMap { it.items }.filter { it.type in transcriptTypes }
        val turn = conversation.turns.lastOrNull()
        val ids = latest.mapTo(mutableSetOf()) { it.id }
        // Reopening/resetting history and a new user turn must never replay a backlog.
        val newUser = latest.any { it.type == "userMessage" && it.id !in old }
        val reset = !initialized || !enabled || conversation.historyReset || newUser && (queued.isNotEmpty() || active != null) ||
            (lastTurn != null && lastTurn != turn?.id && active != null)
        if (reset) {
            flush()
        } else {
            shown.keys.retainAll(ids)
            queued.retainAll(ids)
            if (activeId !in ids) { active = null; activeId = null }
            val live = turn?.items?.mapTo(mutableSetOf()) { it.id }.orEmpty()
            val lastKnown = latest.indexOfLast { it.id in old }
            for ((index, item) in latest.withIndex()) {
                val before = old[item.id]
                if (before == item) continue
                if (before != null && before.text == item.text && item.type in replyTypes &&
                    before.truncated == item.truncated) {
                    shown[item.id]?.let { shown[item.id] = item.copy(text = it.text) }
                    continue
                }
                val canReveal = item.type in replyTypes && item.id in live && item.id !in expanding &&
                    // New large previews still deserve reveal. Only a preview -> full download
                    // bypasses the queue, otherwise the longest replies would always pop in.
                    !(before?.truncated == true && !item.truncated) && item.text.isNotEmpty() &&
                    (before != null || index > lastKnown) &&
                    (before == null || item.text.startsWith(before.text)) && item.text != before?.text
                if (canReveal) queued.add(item.id)
                else {
                    shown[item.id] = item
                    queued.remove(item.id)
                    if (activeId == item.id) { active = null; activeId = null }
                }
            }
        }
        initialized = true
        lastTurn = turn?.id
        advance(now)
    }

    fun flush() {
        shown.clear(); latest.forEach { shown[it.id] = it }
        queued.clear(); active = null; activeId = null; deadline = 0L
    }

    fun advance(now: Long) {
        val current = active
        if (current != null && now < current.startedAt + current.duration) return
        active = null; activeId = null
        if (queued.isEmpty()) { deadline = 0; return }
        if (deadline == 0L) deadline = now + 4200L
        if (now >= deadline) { flush(); return }
        val next = latest.firstOrNull { it.id in queued } ?: run { queued.clear(); return }
        val from = shown[next.id]?.text.orEmpty()
        val duration = replyRevealDuration(next.text.length - from.length, queued.size)
            .coerceAtMost(((deadline - now) / queued.size).coerceAtLeast(1L))
        queued.remove(next.id)
        shown[next.id] = next
        activeId = next.id
        active = ReplyReveal(from, next.text, now, duration)
    }

    fun remaining(now: Long): Long? = active?.let { (it.startedAt + it.duration - now).coerceAtLeast(1L) }
    fun frame() = TranscriptFrame(latest.mapNotNull { shown[it.id] },
        activeId?.let { id -> active?.let { mapOf(id to it) } }.orEmpty())

    companion object {
        val replyTypes = setOf("agentMessage", "plan")
        val transcriptTypes = replyTypes + setOf("userMessage", "commandExecution", "fileChange")
    }
}

internal data class TranscriptRow(val key: String, val items: List<MessageItem>) {
    val tools: Boolean get() = items.first().type in setOf("commandExecution", "fileChange")
}

/** Consecutive tools only; never move commands across a reply or across turns. Retain a group's key
 * when older history is prepended or more commands append, so the reader's anchor survives both. */
internal class ToolRunProjector {
    private var owners = emptyMap<String, String>()
    fun project(messages: List<MessageItem>, turns: List<Turn>): List<TranscriptRow> {
        val turnIds = turns.flatMap { turn -> turn.items.map { it.id to turn.id } }.toMap()
        val positions = turns.flatMap { it.items }.mapIndexed { index, item -> item.id to index }.toMap()
        val result = mutableListOf<TranscriptRow>()
        val nextOwners = mutableMapOf<String, String>()
        var index = 0
        while (index < messages.size) {
            val first = messages[index]
            if (first.type !in setOf("commandExecution", "fileChange")) {
                result += TranscriptRow(first.id, listOf(first)); index++; continue
            }
            val start = index++
            while (index < messages.size && messages[index].type in setOf("commandExecution", "fileChange") &&
                turnIds[messages[index].id] == turnIds[first.id] &&
                positions[messages[index].id] == positions[messages[index - 1].id]?.plus(1)) index++
            val group = messages.subList(start, index).toList()
            val used = result.mapTo(mutableSetOf()) { it.key }
            val key = group.firstNotNullOfOrNull { owners[it.id]?.takeUnless { key -> key in used } } ?: "tools-${first.id}"
            group.forEach { nextOwners[it.id] = key }
            result += TranscriptRow(key, group)
        }
        owners = nextOwners
        return result
    }
}

internal fun toolRunSummary(items: List<MessageItem>): String {
    val failed = items.count { it.status == "failed" || it.exitCode?.let { code -> code != 0 } == true }
    val running = items.count { it.status == "inProgress" }
    val commands = items.count { it.type == "commandExecution" }
    val files = items.size - commands
    return buildList {
        add("执行记录")
        if (commands > 0) add("$commands 条命令")
        if (files > 0) add("$files 次文件修改")
        if (failed > 0) add("$failed 项失败")
        if (running > 0) add("$running 项运行中")
    }.joinToString(" · ")
}
