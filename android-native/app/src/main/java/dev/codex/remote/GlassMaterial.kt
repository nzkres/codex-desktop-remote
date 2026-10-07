package dev.codex.remote

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Samples the actual transcript layer; tint is deliberately thin, not an opaque card. */
@Composable
internal fun GlassMaterial(backdrop: GraphicsLayer, origin: Offset, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val appearance = LocalChatAppearance.current
    val night = LocalAppPalette.current.isNight
    val blur = remember(density, appearance.clarity) {
        if (Build.VERSION.SDK_INT >= 31) android.graphics.RenderEffect.createBlurEffect(
            with(density) { (22f - appearance.clarity * 12f).dp.toPx() },
            with(density) { (22f - appearance.clarity * 12f).dp.toPx() }, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() else null
    }
    Box(modifier.testTag("glass-material")) {
        Box(Modifier.matchParentSize().graphicsLayer { renderEffect = blur }.drawBehind {
            translate(-origin.x, -origin.y) { drawLayer(backdrop) }
        })
        // Android 8-11 retain real translucency with a stronger scrim (no GPU backdrop blur).
        val opacity = if (blur == null) .62f else .42f - appearance.clarity * .30f
        val tint = if (night) Color(0xFF203047) else Color.White
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = opacity), tint.copy(alpha = opacity * .6f)))))
    }
}
