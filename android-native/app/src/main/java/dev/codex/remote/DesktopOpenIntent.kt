package dev.codex.remote

/** A user tap authorizes one Desktop navigation for one client and one conversation. */
internal class DesktopOpenIntent {
    private var pending: Pair<Any, String>? = null

    fun request(client: Any, threadId: String) { pending = client to threadId }

    fun consume(client: Any, threadId: String?): Boolean {
        val requested = pending
        if (requested == null || requested.first !== client || requested.second != threadId) return false
        pending = null // Consume before network I/O; automatic retries cannot reopen Desktop.
        return true
    }

    fun clear() { pending = null }
}

/** Only these two read failures can mean an already requested Desktop open is still settling. */
internal fun desktopOpenMayStillLoad(code: String?): Boolean =
    code == "owner_unavailable" || code == "snapshot_unavailable"
