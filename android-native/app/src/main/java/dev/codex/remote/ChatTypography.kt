package dev.codex.remote

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Spannable
import android.text.Spanned
import android.text.style.LineHeightSpan
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.core.content.res.ResourcesCompat
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.core.spans.CodeBlockSpan
import io.noties.markwon.ext.tables.TablePlugin
import kotlin.math.roundToInt

/** Local, licensed font resource: no font provider or network fetch. */
internal val TerminalFont = FontFamily(Font(R.font.jetbrains_mono_regular))
internal val TerminalTextStyle = TextStyle(fontFamily = TerminalFont,
    fontFeatureSettings = "'liga' 0, 'calt' 0", platformStyle = PlatformTextStyle(includeFontPadding = false))

internal fun terminalTypeface(context: Context): Typeface =
    requireNotNull(ResourcesCompat.getFont(context, R.font.jetbrains_mono_regular))

internal fun configureChatText(view: TextView) {
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
    view.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    view.includeFontPadding = false
    view.setLineSpacing(0f, 1f)
    // Keep code symbols separate (==, ->, !=), as in Tabby's default ligatures=false.
    view.fontFeatureSettings = "'liga' 0, 'calt' 0"
}

internal fun chatMarkdown(context: Context): Markwon {
    val metrics = context.resources.displayMetrics
    val mono = terminalTypeface(context)
    fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, metrics).roundToInt()
    fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, metrics).roundToInt()
    return Markwon.builder(context).usePlugin(TablePlugin.create(context))
        .usePlugin(object : AbstractMarkwonPlugin() {
            override fun configureTheme(builder: MarkwonTheme.Builder) {
                builder.codeTypeface(mono).codeTextSize(sp(12f)).codeBlockTextSize(sp(12f))
                    .codeBlockMargin(dp(6f)).blockMargin(dp(18f)).headingBreakHeight(0)
                    .headingTextSizeMultipliers(floatArrayOf(1.4f, 1.25f, 1.15f, 1.05f, 1f, 1f))
            }

            override fun beforeSetText(textView: TextView, markdown: Spanned) {
                // Reduce only rendered blank separator lines, never rewrite message
                // text or remove blank lines inside fenced/indented code blocks.
                if (markdown !is Spannable) return
                for (index in 1 until markdown.length) {
                    if (markdown[index] == '\n' && markdown[index - 1] == '\n' &&
                        markdown.getSpans(index, index + 1, CodeBlockSpan::class.java).isEmpty()) {
                        markdown.setSpan(ParagraphGap(sp(6f)), index, index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }).build()
}

internal class ParagraphGap(private val pixels: Int) : LineHeightSpan {
    override fun chooseHeight(text: CharSequence, start: Int, end: Int, spanstartv: Int, v: Int, fm: Paint.FontMetricsInt) {
        // Some Android layouts apply paragraph spans to boundary lines too.
        // Never shrink a line that contains a visible glyph.
        if ((start until end).any { text[it] != '\n' }) return
        fm.ascent = -pixels; fm.top = -pixels; fm.descent = 0; fm.bottom = 0
    }
}
