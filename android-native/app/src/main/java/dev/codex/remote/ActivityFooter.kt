package dev.codex.remote

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One quiet line at the end of the transcript, not a second toolbar. */
@Composable
internal fun ActivityFooter(activity: TranscriptActivity) {
    val shimmer = activity.animated && rememberChatMotion()
    val palette = LocalAppPalette.current
    Text(activity.label, color = palette.muted, fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 4.dp).testTag("transcript-activity")
            .semantics {
                liveRegion = LiveRegionMode.Polite
                stateDescription = if (shimmer) "正在处理，明亮扫光" else "静态状态"
            }
            .processingLight(activity.animated))
}
