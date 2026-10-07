package dev.codex.remote

/** An execution target, never a URL supplied by a terminal. Empty ID is the existing route. */
data class TaskTarget(val id: String = "", val name: String = "原电脑 · 旧通道") {
    init { require(validTerminalId(id)) { "电脑标识无效，请重新选择电脑。" } }
    fun endpoint(base: String): String = base.trimEnd('/') + if (id.isEmpty()) "" else "/terminal-api/$id"
    fun route(path: String): String {
        val task = path.substringBefore('?').let {
            it == "/api/projects" || it.startsWith("/api/projects/") || it == "/api/activity" || it == "/api/sessions" || it.startsWith("/api/sessions/")
        }
        return if (task && id.isNotEmpty()) "/terminal-api/$id$path" else path
    }
}

internal fun validTerminalId(id: String): Boolean = id.isEmpty() || id.matches(Regex("[0-9a-f]{32}"))
