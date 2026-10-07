package dev.codex.remote

import android.os.SystemClock
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

/** One clock for the entire page, independent of which lazy rows happen to be on screen. */
@Composable
internal fun rememberSequentialTranscript(threadId: String, conversation: Conversation?, enabled: Boolean,
    expanding: Set<String>): TranscriptFrame {
    val sequence = remember(threadId) { TranscriptSequence().apply {
        accept(conversation, false, SystemClock.uptimeMillis())
    } }
    var frame by remember(threadId) { mutableStateOf(sequence.frame()) }
    var revision by remember(threadId) { mutableIntStateOf(0) }
    LaunchedEffect(conversation, enabled, expanding) {
        sequence.accept(conversation, enabled, SystemClock.uptimeMillis(), expanding)
        frame = sequence.frame()
        revision++
    }
    LaunchedEffect(revision) {
        val remaining = sequence.remaining(SystemClock.uptimeMillis()) ?: return@LaunchedEffect
        delay(remaining)
        sequence.advance(SystemClock.uptimeMillis())
        frame = sequence.frame()
        revision++
    }
    return frame
}
