package dev.codex.remote

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

internal fun copyContentToClipboard(context: Context, text: String) {
    val clip = ClipData.newPlainText("Desktop Remote Lite 内容", text).apply {
        // Chats and command output can contain secrets: suppress the system content preview.
        description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

@Composable
internal fun ContentCopyButton(
    item: MessageItem,
    onLoadFull: suspend (MessageItem) -> MessageItem = { it },
    enabled: Boolean = true,
    color: Color = LocalAppPalette.current.muted,
    compact: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var copying by remember(item.id) { mutableStateOf(false) }
    var copied by remember(item.id) { mutableStateOf(false) }
    var failed by remember(item.id) { mutableStateOf(false) }
    val hasContent = item.truncated || when (item.type) {
        "commandExecution" -> item.command.isNotEmpty() || item.output.isNotEmpty()
        "fileChange" -> item.changes.isNotEmpty()
        else -> item.text.isNotEmpty()
    }
    LaunchedEffect(copied) { if (copied) { delay(2000); copied = false } }
    val label = when {
        copying -> if (item.truncated) "下载并复制中…" else "复制中…"
        copied -> "已复制"
        failed -> "复制失败，重试"
        item.truncated -> "下载全文并复制"
        else -> "复制"
    }
    // Compact rows retain Compose's 48dp expanded pointer target. Their surrounding
    // 8dp gutters keep that target away from the body and other interactive content.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides if (compact) 32.dp else 48.dp) {
    TextButton(
        enabled = enabled && !copying && hasContent,
        onClick = {
            copying = true; copied = false; failed = false
            scope.launch {
                try {
                    val text = resolveCopyText(item, onLoadFull)
                    currentCoroutineContext().ensureActive()
                    if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) throw CancellationException("界面已离开前台")
                    copyContentToClipboard(context, text)
                    copied = true
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    failed = true
                    Toast.makeText(context, if (exception is CopyContentException) exception.message else "复制失败，请重试", Toast.LENGTH_LONG).show()
                } finally { copying = false }
            }
        },
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        modifier = Modifier.heightIn(min = if (compact) 32.dp else 48.dp).testTag("copy-${item.id}").semantics {
            contentDescription = label
            liveRegion = LiveRegionMode.Polite
        },
    ) {
        if (copying && rememberChatMotion()) CircularProgressIndicator(Modifier.size(15.dp).processingLight(true), color = color, strokeWidth = 1.5.dp)
        else Canvas(Modifier.size(16.dp)) {
            val stroke = 1.3.dp.toPx()
            if (copied) {
                drawLine(color, Offset(size.width * .1f, size.height * .5f), Offset(size.width * .4f, size.height * .8f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * .4f, size.height * .8f), Offset(size.width * .9f, size.height * .2f), stroke, StrokeCap.Round)
            } else {
                drawRoundRect(color.copy(alpha = .65f), Offset(size.width * .1f, size.height * .08f), Size(size.width * .6f, size.height * .7f), CornerRadius(2.dp.toPx()), style = Stroke(stroke))
                drawRoundRect(color, Offset(size.width * .3f, size.height * .27f), Size(size.width * .6f, size.height * .65f), CornerRadius(2.dp.toPx()), style = Stroke(stroke))
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(label, Modifier.processingLight(copying), color = color, fontSize = 11.sp)
    }
    }
}
