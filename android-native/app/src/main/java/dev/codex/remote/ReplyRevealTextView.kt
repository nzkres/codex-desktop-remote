package dev.codex.remote

import android.animation.ValueAnimator
import android.content.Context
import android.os.SystemClock
import android.graphics.Canvas
import android.graphics.Path
import android.icu.text.BreakIterator
import android.view.animation.LinearInterpolator
import android.widget.TextView
import io.noties.markwon.Markwon
import kotlin.math.floor

/** Parse once per received snapshot, then reveal the rendered glyphs, not partial Markdown source. */
internal class ReplyRevealTextView(context: Context) : TextView(context) {
    private var source: String? = null
    private var renderer: Markwon? = null
    private var animator: ValueAnimator? = null
    private var boundaries = intArrayOf(0)
    private var progress = 0f
    private var scheduled = false
    private val revealClip = Path()
    internal val isRevealing get() = progress < boundaries.lastIndex
    internal val visibleCharacters get() = boundaries[floor(progress).toInt().coerceIn(boundaries.indices)]

    fun bind(markwon: Markwon, markdown: String, motion: Boolean, reveal: ReplyReveal? = null, claim: () -> String?) {
        if (!motion || scheduled && reveal == null) finishReveal()
        if (source == markdown && renderer === markwon) return
        val oldSource = source
        val oldRendered = text.toString()
        val oldVisible = visibleCharacters
        val from = reveal?.takeIf { it.to == markdown }?.from ?: claim()
        animator?.cancel()
        animator = null
        source = markdown
        renderer = markwon
        scheduled = reveal != null
        markwon.setMarkdown(this, markdown)
        val rendered = text.toString()
        boundaries = if (motion && from != null) characterBoundaries(rendered)
            else if (rendered.isEmpty()) intArrayOf(0) else intArrayOf(0, rendered.length)
        progress = boundaries.lastIndex.toFloat()
        if (!motion || from == null || !ValueAnimator.areAnimatorsEnabled() || rendered.isEmpty()) return
        val previous = if (from == oldSource) oldRendered else markwon.toMarkdown(from).toString()
        // Re-parsing a closing Markdown delimiter can rewrite the old prefix; show corrections immediately.
        if (!rendered.startsWith(previous)) return
        val start = if (from == oldSource) oldVisible.coerceAtMost(previous.length) else previous.length
        val index = boundaries.indexOfLast { it <= start }.coerceAtLeast(0)
        val count = boundaries.lastIndex - index
        if (count <= 0) return
        progress = index.toFloat()
        animator = ValueAnimator.ofFloat(progress, boundaries.lastIndex.toFloat()).apply {
            duration = reveal?.duration ?: replyRevealDuration(count)
            interpolator = LinearInterpolator()
            addUpdateListener {
                val oldHeight = revealedHeight()
                progress = it.animatedValue as Float
                if (revealedHeight() != oldHeight) requestLayout()
                invalidate()
            }
            start()
            if (reveal != null) currentPlayTime = (SystemClock.uptimeMillis() - reveal.startedAt).coerceIn(0L, duration)
        }
        requestLayout()
        invalidate()
    }

    fun finishReveal() {
        animator?.cancel()
        animator = null
        if (isRevealing) {
            progress = boundaries.lastIndex.toFloat()
            requestLayout()
            invalidate()
        }
    }

    private fun revealedHeight(): Int {
        val lines = layout ?: return Int.MAX_VALUE
        if (!isRevealing) return lines.height + compoundPaddingTop + compoundPaddingBottom
        val index = floor(progress).toInt().coerceIn(0, boundaries.lastIndex - 1)
        val offset = boundaries[index] + (boundaries[index + 1] - boundaries[index]) * (progress - index)
        val line = lines.getLineForOffset(boundaries[index].coerceAtMost(text.length))
        // Grow through a newly entered line gently, without reserving a giant blank final reply.
        val start = lines.getLineStart(line)
        val end = lines.getLineEnd(line).coerceAtLeast(start + 1)
        val growth = ((offset - start).toFloat() / (end - start) * 4f).coerceIn(0f, 1f)
        val top = if (line == 0) lines.getLineBottom(0) else lines.getLineTop(line)
        return (top + (lines.getLineBottom(line) - top) * growth).toInt() + compoundPaddingTop + compoundPaddingBottom
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        if (isRevealing && MeasureSpec.getMode(heightMeasureSpec) != MeasureSpec.EXACTLY)
            setMeasuredDimension(measuredWidth, measuredHeight.coerceAtMost(revealedHeight()))
    }

    override fun onDraw(canvas: Canvas) {
        val lines = layout
        if (!isRevealing || lines == null) { super.onDraw(canvas); return }
        val index = floor(progress).toInt().coerceIn(0, boundaries.lastIndex - 1)
        val start = boundaries[index]
        val next = boundaries[index + 1]
        val line = lines.getLineForOffset(start)
        val nextLine = lines.getLineForOffset(next)
        val fraction = progress - index
        val rtl = lines.getParagraphDirection(line) < 0
        val x1 = lines.getPrimaryHorizontal(start)
        val x2 = if (nextLine == line) lines.getPrimaryHorizontal(next) else if (rtl) lines.getLineLeft(line) else lines.getLineRight(line)
        val edge = x1 + (x2 - x1) * fraction
        val tx = compoundPaddingLeft.toFloat() - scrollX
        val ty = extendedPaddingTop.toFloat() - scrollY
        revealClip.reset()
        revealClip.addRect(0f, 0f, width.toFloat(), ty + lines.getLineTop(line), Path.Direction.CW)
        revealClip.addRect(if (rtl) tx + edge else 0f, ty + lines.getLineTop(line),
            if (rtl) width.toFloat() else tx + edge, ty + lines.getLineBottom(line), Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(revealClip)
        super.onDraw(canvas)
        canvas.restoreToCount(save)
    }

    override fun onDetachedFromWindow() { finishReveal(); super.onDetachedFromWindow() }
}

/** ICU character boundaries keep surrogate pairs, combining marks and emoji sequences together. */
internal fun characterBoundaries(text: String): IntArray {
    val iterator = BreakIterator.getCharacterInstance()
    iterator.setText(text)
    val offsets = ArrayList<Int>()
    var offset = iterator.first()
    while (offset != BreakIterator.DONE) { offsets.add(offset); offset = iterator.next() }
    return offsets.toIntArray()
}
