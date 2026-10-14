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
    val blur = remember(density, appearance.clarity, appearance.skin.glass) {
        if (Build.VERSION.SDK_INT >= 31) android.graphics.RenderEffect.createBlurEffect(
            with(density) { (when (appearance.skin.glass) { GlassSkin.Frost -> 28f; GlassSkin.Smoke -> 18f; GlassSkin.Clear -> 12f } - appearance.clarity * 5f).dp.toPx() },
            with(density) { (when (appearance.skin.glass) { GlassSkin.Frost -> 28f; GlassSkin.Smoke -> 18f; GlassSkin.Clear -> 12f } - appearance.clarity * 5f).dp.toPx() }, android.graphics.Shader.TileMode.CLAMP).asComposeRenderEffect() else null
    }
    Box(modifier.testTag("glass-material")) {
        Box(Modifier.matchParentSize().graphicsLayer { renderEffect = blur }.drawBehind {
            translate(-origin.x, -origin.y) { drawLayer(backdrop) }
        })
        // Android 8-11 retain real translucency with a stronger scrim (no GPU backdrop blur).
        val opacity = if (blur == null) .68f else when (appearance.skin.glass) { GlassSkin.Smoke -> .48f; GlassSkin.Frost -> .32f; GlassSkin.Clear -> .16f }
        val tint = if (night) Color(appearance.skin.palette.dark) else Color.White
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(tint.copy(alpha = opacity), tint.copy(alpha = opacity * .6f)))))
    }
}
