package dev.codex.remote

/** UI-only receipt. A successful POST is not the same as an already-synchronized transcript. */
internal data class SendPresentation(
    val key: String,
    val text: String,
    val turnId: String?,
    val previousUserIds: Set<String>,
    val confirmedItemId: String? = null,
)

internal fun confirmedSentItem(receipt: SendPresentation, turns: List<Turn>): MessageItem? {
    if (receipt.confirmedItemId != null) return turns.flatMap { it.items }.find { it.id == receipt.confirmedItemId }
    val candidates = if (!receipt.turnId.isNullOrBlank()) turns.filter { it.id == receipt.turnId }.flatMap { it.items }
        else turns.flatMap { it.items }
    return candidates.firstOrNull { it.type == "userMessage" && it.id !in receipt.previousUserIds &&
        (!receipt.turnId.isNullOrBlank() || it.text.trim() == receipt.text.trim()) }
}
