package dev.codex.remote

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import kotlin.math.*

// Pro has no five-hour limit. Use its real weekly window, never manufacture one
// from an obsolete short-term sample. Unknown plans keep the received windows.
internal fun quotaWindows(limit: UsageLimit?): List<UsageWindow> {
    val actual=limit?.windows.orEmpty()
    return if(limit?.planType=="pro") actual.filter { it.minutes==10080 }.take(1) else actual.take(2)
}
internal data class QuotaCoreGeometry(val center: Offset, val radius: Float)
internal fun quotaCoreGeometry(width: Float, height: Float, count: Int): List<QuotaCoreGeometry> {
    if(width<=0f || height<=0f) return emptyList()
    val n=count.coerceIn(1,2)
    val radius=min(width*(if(n==1).31f else .215f),height*.19f)
    return List(n) { i -> QuotaCoreGeometry(Offset(width*(if(n==1).5f else if(i==0).265f else .735f),height*.32f),radius) }
}
private val quotaGlyphs=mapOf(
    '0' to "01110/11011/11011/11011/11011/11011/01110", '1' to "00100/01100/00100/00100/00100/00100/01110",
    '2' to "01110/11011/00011/00110/01100/11000/11111", '3' to "11110/00011/00011/01110/00011/00011/11110",
    '4' to "00011/00111/01111/11011/11111/00011/00011", '5' to "11111/11000/11000/11110/00011/00011/11110",
    '6' to "01110/11000/11000/11110/11011/11011/01110", '7' to "11111/00011/00110/00110/01100/01100/01100",
    '8' to "01110/11011/11011/01110/11011/11011/01110", '9' to "01110/11011/11011/01111/00011/00011/01110",
    '%' to "11001/11011/00010/00100/01000/11011/10011", '—' to "00000/00000/00000/11111/00000/00000/00000")
internal fun quotaDigitCells(text: String): List<Pair<Int,Int>> = text.take(4).flatMapIndexed { index,ch ->
    quotaGlyphs[ch].orEmpty().split('/').flatMapIndexed { y,row -> row.mapIndexedNotNull { x,c -> if(c=='1') (index*6+x) to y else null } }
}

internal fun DrawScope.drawQuotaCore(value: DeviceQuota?, now: Long, time: Float, skin: TerminalSkin, night: Boolean,
    activity: QuotaActivityFrame? = null, presentation: QuotaPresentation? = null) {
    val limit=quotaLimit(value); val windows=quotaWindows(limit)
    val stale=value?.unavailable==true || limit?.let { usageStale(it,now) }!=false
    val power=boundedLight(skin.flowBrightness)
    if(power==0f) return
    val tint=if(night) Color(0xFFD5F7EB) else Color(0xFF285A4C)
    val baseAlpha=(power*(if(stale).16f else .32f)).coerceAtMost(if(night).48f else .4f)
    // Bound the base before breathing so a bright skin cannot clip the whole pulse flat.
    val alpha=if(activity==null) baseAlpha else baseAlpha +
        ((baseAlpha*2.15f).coerceAtMost(if(night).68f else .5f)-baseAlpha)*
        activity.energy*(.35f+.65f*activity.breath)
    val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign=Paint.Align.CENTER;typeface=Typeface.MONOSPACE }
    val combined=skin.quotaStyle==QuotaStyle.Dual && windows.size==2
    val geometry=quotaCoreGeometry(size.width,size.height,if(combined)1 else windows.size)
    geometry.forEachIndexed { index,g ->
        val w=windows.getOrNull(index);val amount=w?.let { presentation?.numbers?.get(it.minutes)?.roundToInt() ?: it.remaining }
        val fraction=(amount ?: 0)/100f
        val c=g.center;val r=g.radius
        val ink=tint.copy(alpha=alpha)
        if (activity != null && activity.energy > 0f) {
            // Broad soft halo stays behind the ring, not over the conversation text.
            val glow = activity.energy * (.05f + .09f * activity.breath) * (power / (1f + power))
            val ringAlpha = activity.energy * (.55f + .45f * activity.breath) * (power / (1f + power))
            drawCircle(Brush.radialGradient(0f to Color.Transparent, .72f to Color.Transparent,
                .85f to tint.copy(alpha = glow.coerceAtMost(.2f)), 1f to Color.Transparent,
                center = c, radius = r * 1.24f), r * 1.24f, c)
            repeat(2) { segment ->
                val start = activity.angle + segment * 180f
                val radius = r * 1.075f
                val origin = c - Offset(radius, radius)
                val dimensions = Size(radius * 2, radius * 2)
                for ((width, opacity) in listOf(.1f to .05f, .05f to .13f, .016f to .75f)) {
                    drawArc(tint.copy(alpha = (ringAlpha * opacity).coerceAtMost(.8f)), start, 65f, false,
                        origin, dimensions, style = Stroke(r * width, cap = StrokeCap.Round))
                }
                // A short luminous head and a softer tail make the direction readable.
                drawArc(tint.copy(alpha = (ringAlpha * .9f).coerceAtMost(.9f)), start + 55f, 9f, false,
                    origin, dimensions, style = Stroke(r * .021f, cap = StrokeCap.Round))
            }
        }
        drawCircle(ink.copy(alpha=alpha*.17f),r,c,style=Stroke(r*.06f))
        drawCircle(ink.copy(alpha=alpha*.1f),r*.91f,c,style=Stroke(r*.14f))
        when(skin.quotaStyle) {
            QuotaStyle.Digits -> {
                drawCircle(ink.copy(alpha=alpha*.4f),r*.96f,c,style=Stroke(r*.009f))
                drawArc(ink,-90f,360f*fraction,false,c-Offset(r,r),Size(r*2,r*2),style=Stroke(r*.022f))
            }
            QuotaStyle.Ring -> repeat(80) { cell ->
                val angle=(cell/80f*2*PI-PI/2).toFloat()
                val point=c+Offset(cos(angle),sin(angle))*r*.94f
                val lit=cell<floor(fraction*80).toInt()
                val cellSize=r*.045f
                drawRect(ink.copy(alpha=alpha*if(lit).9f else .12f),point-Offset(cellSize/2,cellSize/2),Size(cellSize,cellSize))
            }
            QuotaStyle.Matrix -> {
                val cells=(-10..10).flatMap { y -> (-10..10).mapNotNull { x ->
                    if(x*x+y*y<=100 && (abs(y)>4 || abs(x)>8)) x to y else null
                } }.sortedWith(compareByDescending<Pair<Int,Int>>{it.second}.thenBy {it.first})
                val edge=r*.082f
                cells.forEachIndexed { i,(x,y) ->
                    val point=c+Offset(x*r*.09f,y*r*.09f)
                    drawRect(ink.copy(alpha=alpha*if(i<floor(cells.size*fraction).toInt()).55f else .08f),point-Offset(edge/2,edge/2),Size(edge,edge))
                }
            }
            QuotaStyle.Dual -> {
                for((ring,scale) in (if(combined)listOf(.94f,.81f)else listOf(.94f)).withIndex()) {
                    val radius=r*scale
                    val win=windows.getOrNull(if(combined)ring else index)
                    val actual=(win?.let { presentation?.numbers?.get(it.minutes) ?: it.remaining.toFloat() } ?: 0f)/100f
                    drawCircle(ink.copy(alpha=alpha*.15f),radius,c,style=Stroke(r*.026f))
                    drawArc(ink.copy(alpha=alpha*if(scale>.9f).85f else .55f),-90f,360f*actual,false,
                        c-Offset(radius,radius),Size(radius*2,radius*2),style=Stroke(r*.026f))
                }
            }
            QuotaStyle.Radial -> repeat(64) { ray ->
                val a=(ray/64f*2*PI).toFloat();val direction=Offset(cos(a),sin(a))
                val lit=ray<floor(64*fraction).toInt()
                val pulse=if(lit) .8f+.2f*sin(time*1.3f+ray*.4f) else .12f
                drawLine(ink.copy(alpha=alpha*pulse*.65f),c+direction*r*.76f,c+direction*r*(if(ray%4==0)1.07f else .98f),r*.018f)
            }
        }
        // Square pixels build the digits; every style has its number in the ring center.
        val slots=if(combined)windows else listOf(w)
        slots.forEachIndexed { slot,window ->
        val numberCenter=if(combined)c+Offset(0f,if(slot==0)-r*.27f else r*.27f)else c
        val text=window?.let { "${presentation?.numbers?.get(it.minutes)?.roundToInt() ?: it.remaining}%" } ?: "—"
        val pixel=min(r*(if(combined).048f else .073f),r*1.38f/(text.length*6-1))
        val origin=numberCenter-Offset((text.length*6-1)*pixel/2,7*pixel/2)
        quotaDigitCells(text).forEach { (x,y) ->
            val point=origin+Offset(x*pixel,y*pixel)
            // Solid pixel silhouettes occlude the grid/sweep; the displaced dark
            // edge reads as a cast shadow, with the luminous digit in front.
            if(presentation!=null) {
                val shadow=if(night) Color(skin.palette.dark) else Color.White
                drawRect(shadow.copy(alpha=.9f),point-Offset(pixel*.16f,pixel*.1f),Size(pixel*1.12f,pixel*1.12f))
                drawRect(shadow.copy(alpha=.65f),point+Offset(pixel*.28f,pixel*.4f),Size(pixel*.88f,pixel*1.45f))
                if(presentation.resetLight>0f) drawLine(tint.copy(alpha=(presentation.resetLight*.5f).coerceAtMost(.5f)),
                    point+Offset(pixel*.4f,pixel*2.5f),point+Offset(pixel*.4f,-pixel*1.5f),pixel*.25f)
            }
            drawRect(ink.copy(alpha=alpha*.13f),point-Offset(pixel*.3f,pixel*.3f),Size(pixel*1.45f,pixel*1.45f))
            val projection=(x+y).toFloat()/(text.length*6+6)
            val wave=presentation?.let { exp(-((projection-(it.flare-.55f))/.15f).pow(2))*it.scatter } ?: 0f
            if(wave>0f) drawCircle(Brush.radialGradient(listOf(tint.copy(alpha=(wave*.38f).coerceAtMost(.38f)),Color.Transparent),
                center=point+Offset(pixel*.4f,pixel*.4f),radius=pixel*3f),pixel*3f,point+Offset(pixel*.4f,pixel*.4f))
            drawRect(ink.copy(alpha=(alpha+wave*.55f).coerceAtMost(.9f)),point,Size(pixel*.78f,pixel*.78f))
        }
        paint.textSize=min(11.dp.toPx(),r*.15f);paint.color=ink.copy(alpha=alpha*.9f).toArgb()
        val label=if(window==null) "未同步" else (if(stale)"上次 · " else "")+window.label
        drawContext.canvas.nativeCanvas.drawText(label,numberCenter.x,numberCenter.y+r*(if(combined).27f else .49f),paint)
        }
        if(index==0 && limit?.planType!=null) {
            paint.textSize=min(10.dp.toPx(),r*.13f)
            drawContext.canvas.nativeCanvas.drawText(limit.planType.uppercase(),c.x,c.y-r*(if(combined).65f else .5f),paint)
        }
    }
}
