package dev.codex.remote

import androidx.compose.animation.core.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal val ProcessingLightState = SemanticsPropertyKey<String>("ProcessingLight")
internal fun processingLightColor(night: Boolean) = if (night) Color(0xFFF4FCFF) else Color(0xFF0064D8)

/** Apply only to text/glyphs, never a panel. Draw-phase animation avoids relaying out the transcript.
 * A narrow bright core plus soft shoulders moves left to right; transparent ends hide the restart.
 * On light surfaces use saturated blue rather than unreadable white. No background/network work. */
@Composable
internal fun Modifier.processingLight(active: Boolean): Modifier {
    val moving = active && rememberChatMotion()
    val night = LocalAppPalette.current.isNight
    val progress = if (moving) rememberInfiniteTransition(label = "processing-light").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1900, delayMillis = 100, easing = LinearEasing), RepeatMode.Restart),
        label = "processing-left-to-right",
    ) else null
    val described = this.semantics { this[ProcessingLightState] = if (moving) "moving" else "static" }
    if (progress == null) return described
    val light = processingLightColor(night)
    return described.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val band = (size.width * .32f).coerceIn(12.dp.toPx(), 64.dp.toPx())
            val center = -band + (size.width + band * 2) * progress.value
            drawRect(Brush.linearGradient(
                0f to light.copy(alpha = 0f), .30f to light.copy(alpha = .30f),
                .46f to light.copy(alpha = .9f), .50f to light,
                .54f to light.copy(alpha = .9f), .70f to light.copy(alpha = .30f), 1f to light.copy(alpha = 0f),
                start = Offset(center - band, 0f), end = Offset(center + band, 0f)), blendMode = BlendMode.SrcAtop)
        }
}
