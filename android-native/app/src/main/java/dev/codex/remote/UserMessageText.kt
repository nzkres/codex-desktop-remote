package dev.codex.remote

import android.widget.TextView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView

/** User drafts are literal text, not Markdown. Preserve newlines, blank lines and indentation.
 * Only normalize platform line endings for rendering; the stored and copied source is untouched. */
internal fun userDisplayText(source: String): String = source.replace("\r\n", "\n").replace('\r', '\n')

@Composable
internal fun UserMessageText(source: String, id: String) {
    val ink = LocalAppPalette.current.ink.toArgb()
    AndroidView(factory = { TextView(it).apply { configureChatText(this) } },
        update = {
            configureChatText(it); it.setTextColor(ink); it.tag = "user-text-$id"
            val display = userDisplayText(source)
            if (it.text.toString() != display) it.text = display
        }, modifier = Modifier.fillMaxWidth())
}
