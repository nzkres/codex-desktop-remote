package dev.codex.remote

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DecisionSurface(modifier: Modifier = Modifier, warning: Boolean = false, content: @Composable () -> Unit) {
    val palette = LocalAppPalette.current
    val skin = LocalChatAppearance.current.skin
    val glassAlpha = when (skin.glass) { GlassSkin.Clear -> .58f; GlassSkin.Smoke -> .78f; GlassSkin.Frost -> .86f }
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        color = if (warning) palette.warningSurface.copy(alpha = .85f) else
            (if (palette.isNight) Color(skin.palette.dark) else Color.White).copy(alpha = glassAlpha), contentColor = palette.ink,
        border = BorderStroke(.7.dp, palette.muted.copy(alpha = .45f))) { content() }
}

@Composable
internal fun decisionButtonColors(): ButtonColors {
    val palette = LocalAppPalette.current
    val accent = conversationColors(LocalChatAppearance.current.style, palette.isNight).accent
    return ButtonDefaults.buttonColors(containerColor = accent,
        contentColor = if (palette.isNight) Color(0xFF0D1117) else Color.White,
        disabledContainerColor = palette.card, disabledContentColor = palette.ink)
}

@Composable
internal fun DecisionOption(label: String, selected: Boolean, enabled: Boolean, description: String = "", onSelect: () -> Unit) {
    val palette = LocalAppPalette.current
    val accent = conversationColors(LocalChatAppearance.current.style, palette.isNight).accent
    Surface(Modifier.fillMaxWidth().padding(vertical = 3.dp), shape = RoundedCornerShape(12.dp),
        color = if (selected) accent.copy(alpha = .12f) else Color.Transparent, contentColor = palette.ink,
        border = BorderStroke(if (selected) 1.dp else .5.dp, if (selected) accent else palette.muted.copy(alpha = .45f))) {
        Row(Modifier.heightIn(min = 48.dp).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected, onClick = null, enabled = enabled,
                colors = RadioButtonDefaults.colors(selectedColor = accent, unselectedColor = palette.muted,
                    disabledSelectedColor = palette.ink, disabledUnselectedColor = palette.muted))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = palette.ink, fontSize = 12.sp)
                if (description.isNotBlank()) Text(description, color = palette.muted, fontSize = 11.sp, lineHeight = 15.sp)
            }
        }
    }
}
