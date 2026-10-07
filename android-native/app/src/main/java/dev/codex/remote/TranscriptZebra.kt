package dev.codex.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ZebraStyle(val label: String) {
    Mist("雾银"), Blue("冰蓝"), Sage("青苔"), Sand("暖沙"), Off("关闭")
}

internal fun zebraSurface(style: ZebraStyle, stripe: Int, night: Boolean): Color {
    if (style == ZebraStyle.Off) return Color.Transparent
    val pair = when (style) {
        ZebraStyle.Mist -> if (night) 0xFF101820L to 0xFF1A2530L else 0xFFF9FAFBL to 0xFFEBEFF3L
        ZebraStyle.Blue -> if (night) 0xFF101C2AL to 0xFF1B2D42L else 0xFFF6FAFFL to 0xFFE3EEF9L
        ZebraStyle.Sage -> if (night) 0xFF111F1BL to 0xFF20332DL else 0xFFF6FBF8L to 0xFFE4F0E9L
        ZebraStyle.Sand -> if (night) 0xFF211C16L to 0xFF302A22L else 0xFFFFFCF7L to 0xFFF2EADFL
        ZebraStyle.Off -> error("handled above")
    }
    return Color(if (stripe and 1 == 0) pair.first else pair.second)
}

internal fun messageSurface(appearance: ChatAppearance, mine: Boolean, stripe: Int, night: Boolean): Color {
    val colors = conversationColors(appearance.style, night)
    if (appearance.zebra == ZebraStyle.Off) return if (mine) colors.mine.copy(alpha = .72f) else Color.Transparent
    val background = zebraSurface(appearance.zebra, stripe, night)
    return if (mine) colors.accent.copy(alpha = if (night) .18f else .10f).compositeOver(background) else background
}

/** The inset beneath the last row is still transcript, not a separately colored toolbar.
 * Opaque zebra rows can share their final surface with the backdrop without double tinting.
 * With stripes disabled keep a neutral base beneath the translucent user-message tint. */
internal fun transcriptPageSurface(appearance: ChatAppearance, mine: Boolean, stripe: Int, night: Boolean): Color =
    if (appearance.zebra == ZebraStyle.Off) { if (night) Color(0xFF0E1520) else Color(0xFFF7F8FC) }
    else messageSurface(appearance, mine, stripe, night)

/** Keep small labels/copy controls readable on both stripes, including the user-role tint. */
internal fun zebraSecondary(style: ZebraStyle, night: Boolean, fallback: Color): Color =
    if (style == ZebraStyle.Off) fallback else if (night) Color(0xFFB6C3D4) else Color(0xFF525B69)

/** Anchor the alternation to an existing row, so loading an odd number of older rows does not flash
 * every already visible stripe. Only current keys are retained; this is not an ever-growing cache. */
internal class StripeLedger {
    private var previous = emptyMap<String, Int>()
    fun assign(keys: List<String>): Map<String, Int> {
        val anchor = keys.indexOfFirst { it in previous }
        val parity = if (anchor < 0) 0 else (previous.getValue(keys[anchor]) - anchor) and 1
        return keys.mapIndexed { index, key -> key to ((index + parity) and 1) }.toMap().also { previous = it }
    }
}

@Composable
internal fun ZebraSettings(selected: ZebraStyle, onSelect: (ZebraStyle) -> Unit) {
    val palette = LocalAppPalette.current
    Text("斑马纹", fontSize = 15.sp, color = palette.ink, modifier = Modifier.padding(top = 8.dp))
    Text("按消息交替铺底，命令输出按行交替；不改变文字。", fontSize = 12.sp, color = palette.muted,
        modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
    ZebraStyle.entries.chunked(2).forEach { pair ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { style ->
                Column(Modifier.weight(1f)
                    .border(if (style == selected) 1.5.dp else .5.dp,
                        if (style == selected) palette.green else palette.divider, RoundedCornerShape(12.dp))
                    .clickable(role = Role.RadioButton) { onSelect(style) }
                    .semantics { contentDescription = "斑马纹：${style.label}${if (style == selected) "，已选中" else ""}" }
                    .padding(10.dp)) {
                    Text("${style.label}${if (style == selected) "  ✓" else ""}", color = palette.ink, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    repeat(3) { index ->
                        Box(Modifier.fillMaxWidth().height(12.dp).background(zebraSurface(style, index, palette.isNight)))
                    }
                }
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
