package dev.codex.remote

import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

enum class ChatStyle(val label: String, val detail: String) {
    Glacier("冰川蓝", "淡蓝通栏 · 开放式回复"),
    Silver("雾银", "雾灰通栏 · 开放式回复"),
    Sage("鼠尾草", "青绿通栏 · 开放式回复");
}

data class ChatAppearance(val style: ChatStyle = ChatStyle.Glacier, val clarity: Float = .8f, val reduceMotion: Boolean = false,
    val zebra: ZebraStyle = ZebraStyle.Mist)
val LocalChatAppearance = compositionLocalOf { ChatAppearance() }

internal fun readChatAppearance(prefs: SharedPreferences) = ChatAppearance(
    ChatStyle.entries.find { it.name == prefs.getString("chat_style", null) } ?: ChatStyle.Glacier,
    prefs.getFloat("glass_clarity", .8f).let { if (it.isFinite()) it.coerceIn(0f, 1f) else .8f },
    prefs.getBoolean("reduce_motion", false),
    ZebraStyle.entries.find { it.name == prefs.getString("zebra_style", null) } ?: ZebraStyle.Mist,
)

@Composable
internal fun rememberChatAppearance(): ChatAppearance {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    var appearance by remember(prefs) { mutableStateOf(readChatAppearance(prefs)) }
    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> appearance = readChatAppearance(prefs) }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return appearance
}

data class ConversationColors(val mine: Color, val reply: Color, val accent: Color, val atmosphere: Color)

internal fun conversationColors(style: ChatStyle, night: Boolean): ConversationColors = when (style) {
    ChatStyle.Glacier -> if (night) ConversationColors(Color(0xFF254666), Color(0xFF1A2533), Color(0xFF95C8F9), Color(0xFF1D4770))
        else ConversationColors(Color(0xFFD8E9FA), Color(0xFAFFFFFF), Color(0xFF32608C), Color(0xFFB0CEE9))
    ChatStyle.Silver -> if (night) ConversationColors(Color(0xFF424957), Color(0xFF25262C), Color(0xFFCAD4E2), Color(0xFF53556E))
        else ConversationColors(Color(0xFFDEE2E9), Color(0xFAFFFEFC), Color(0xFF515B6D), Color(0xFFC3BED6))
    ChatStyle.Sage -> if (night) ConversationColors(Color(0xFF2B514A), Color(0xFF1C2B28), Color(0xFFA7D5C4), Color(0xFF346659))
        else ConversationColors(Color(0xFFD5E9E0), Color(0xFAFFFEF8), Color(0xFF386153), Color(0xFFAFD2C0))
}

/** No animation loop when backgrounded, system animations disabled, or user asks for less motion. */
@Composable
internal fun rememberChatMotion(): Boolean {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = LocalChatAppearance.current.reduceMotion
    var enabled by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && ValueAnimator.areAnimatorsEnabled()) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> enabled = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && ValueAnimator.areAnimatorsEnabled() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return enabled && !reduced
}

@Composable
internal fun ChatAppearanceSettings() {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
    val appearance = LocalChatAppearance.current
    val palette = LocalAppPalette.current
    var clarity by remember(appearance.clarity) { mutableFloatStateOf(appearance.clarity) }
    Column(Modifier.fillMaxWidth().padding(18.dp).testTag("chat-appearance-settings")) {
        Text("对话与玻璃", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = palette.ink)
        Text("选择配色，即时生效；浅色与深夜各有对应版本。", fontSize = 12.sp, color = palette.muted, modifier = Modifier.padding(top = 5.dp, bottom = 14.dp))
        ChatStyle.entries.forEach { style ->
            val colors = conversationColors(style, palette.isNight)
            val selected = style == appearance.style
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)
                .border(if (selected) 1.5.dp else .5.dp, if (selected) colors.accent else palette.divider, RoundedCornerShape(18.dp))
                .clickable(role = Role.RadioButton) { prefs.edit().putString("chat_style", style.name).apply() }
                .semantics { contentDescription = "配色：${style.label}${if (selected) "，已选中" else ""}" }
                .padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(style.label, color = palette.ink, fontWeight = FontWeight.Medium, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (selected) Text("✓", color = colors.accent)
                }
                Text(style.detail, color = palette.muted, fontSize = 11.sp)
                Text("› 你：让灵感继续", color = palette.ink, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp).background(colors.mine.copy(alpha = .72f)).padding(horizontal = 12.dp, vertical = 8.dp))
                Text("✦ Codex：已经为你准备好了。", color = palette.ink, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
            }
        }
        ZebraSettings(appearance.zebra) { prefs.edit().putString("zebra_style", it.name).apply() }
        Text("玻璃清透程度 · ${(clarity * 100).toInt()}%", color = palette.ink, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        GlassAppearancePreview()
        Slider(value = clarity, onValueChange = { clarity = it; prefs.edit().putFloat("glass_clarity", it).apply() },
            modifier = Modifier.semantics { contentDescription = "玻璃清透程度" })
        Text("左侧更柔雾，右侧更通透。调整的是材质，不会降低文字透明度。", color = palette.muted, fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("减少动画", color = palette.ink, fontSize = 14.sp)
                Text("保留实际状态，关闭出字、形变和扫光。", color = palette.muted, fontSize = 12.sp)
            }
            Switch(checked = appearance.reduceMotion, onCheckedChange = { prefs.edit().putBoolean("reduce_motion", it).apply() },
                modifier = Modifier.semantics { contentDescription = "减少动画" })
        }
    }
}

@Composable
internal fun GlassAppearancePreview() {
    val palette = LocalAppPalette.current
    val colors = conversationColors(LocalChatAppearance.current.style, palette.isNight)
    val backdrop = rememberGraphicsLayer()
    val inset = with(LocalDensity.current) { 12.dp.toPx() }
    val top = with(LocalDensity.current) { 54.dp.toPx() }
    Box(Modifier.fillMaxWidth().padding(top = 10.dp).height(176.dp).testTag("glass-clarity-preview").clip(RoundedCornerShape(20.dp))) {
        Column(Modifier.fillMaxSize().drawWithContent {
            backdrop.record { this@drawWithContent.drawContent() }; drawLayer(backdrop)
        }.background(Brush.horizontalGradient(listOf(colors.atmosphere, palette.background, colors.mine))).padding(16.dp)) {
            Text("✦ Codex · 这段文字在玻璃背后", color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(13.dp))
            Text("让内容清楚，让界面轻盈。\n正在读取项目 · npm test\n下一步，让灵感继续。", color = palette.ink,
                fontSize = 17.sp, lineHeight = 26.sp, modifier = Modifier.testTag("glass-preview-background-text"))
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).offset(y = 54.dp).height(100.dp).clip(RoundedCornerShape(16.dp))
            .border(.8.dp, Color.White.copy(alpha = .65f), RoundedCornerShape(16.dp))) {
            GlassMaterial(backdrop, Offset(inset, top), Modifier.matchParentSize().testTag("clarity-sample-material"))
            Text("玻璃前景 · 始终清晰", color = palette.ink, fontSize = 12.sp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(10.dp).background(palette.background.copy(alpha = .62f), RoundedCornerShape(8.dp)).padding(5.dp))
        }
    }
}
