package dev.codex.remote

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal fun synchronizeDraft(value: TextFieldValue, draft: String): TextFieldValue =
    if (value.text == draft) value else TextFieldValue(draft, TextRange(draft.length))

/** A bounded viewport with an explicit caret, selection and IME composition, not just a String. */
@Composable
internal fun ComposerEditor(draft: String, onDraft: (String) -> Unit, enabled: Boolean, expanded: Boolean,
    running: Boolean, focusRequester: FocusRequester, accent: Color) {
    var localValue by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(draft, TextRange(draft.length))) }
    val value = synchronizeDraft(localValue, draft)
    SideEffect { if (localValue != value) localValue = value }
    val scroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeight by remember { mutableIntStateOf(0) }
    var focused by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val padding = with(density) { 8.dp.toPx() }
    val maximumHeight = with(density) { (21.sp.toPx() * 4).toDp() } + 16.dp
    val motion = rememberChatMotion()
    val palette = LocalAppPalette.current

    LaunchedEffect(value.text, value.selection, expanded, focused, layout?.size, viewportHeight) {
        if (!expanded || viewportHeight == 0) return@LaunchedEffect
        withFrameNanos { }
        val measured = layout?.takeIf { it.layoutInput.text.text == value.text } ?: return@LaunchedEffect
        val caret = measured.getCursorRect(value.selection.end.coerceIn(0, value.text.length))
        val top = caret.top + padding
        val bottom = caret.bottom + padding
        val target = when {
            bottom > scroll.value + viewportHeight - padding -> bottom - viewportHeight + padding
            top < scroll.value + padding -> top - padding
            else -> scroll.value.toFloat()
        }.toInt().coerceIn(0, scroll.maxValue)
        if (target != scroll.value) scroll.animateScrollTo(target, tween(if (motion) 130 else 0))
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 44.dp, max = maximumHeight.coerceAtLeast(44.dp))
        .testTag("composer-input-viewport").clipToBounds().onSizeChanged { viewportHeight = it.height }
        .verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 8.dp)) {
        BasicTextField(value = value, onValueChange = { next ->
            localValue = next // Keep the IME composing range and selection on every keystroke.
            onDraft(next.text)
        }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("composer-editor")
            .focusRequester(focusRequester).onFocusChanged { focused = it.isFocused },
            textStyle = TextStyle(color = palette.ink, fontSize = 15.sp, lineHeight = 21.sp),
            cursorBrush = SolidColor(accent), onTextLayout = { layout = it },
            decorationBox = { inner -> Box {
                if (value.text.isEmpty()) Text(if (running) "先写好下一条消息…" else "发消息，继续这段对话…",
                    color = palette.muted, fontSize = 15.sp, lineHeight = 21.sp)
                inner()
            } })
    }
}
