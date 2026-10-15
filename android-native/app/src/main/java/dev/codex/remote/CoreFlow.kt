package dev.codex.remote

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.*

/** All circuit elbows share the exact grid lattice. No animation state or network I/O. */
internal fun coreRoutes(cols:Int,rows:Int):List<List<Offset>> {
    if(cols<2 || rows<2) return emptyList()
    val x=(cols/2).toFloat();val y=(rows*.32f).roundToInt().coerceIn(1,rows-1).toFloat()
    val center=Offset(x,y)
    return (0..3).flatMap { side -> (0..3).map { branch ->
        val spread=(branch-1.5f)*2f
        val elbow=if(side%2==0) Offset((x+spread.roundToInt()).coerceIn(0f,cols.toFloat()),y)
            else Offset(x,(y+spread.roundToInt()).coerceIn(0f,rows.toFloat()))
        val end=when(side) { 0->Offset(elbow.x,0f);1->Offset(cols.toFloat(),elbow.y);2->Offset(elbow.x,rows.toFloat());else->Offset(0f,elbow.y) }
        listOf(center,elbow,end).distinct()
    } }
}

internal fun circuitLength(route:List<Offset>) = route.zipWithNext().sumOf { (a,b)->(b-a).getDistance().toDouble() }.toFloat()
internal fun circuitPoint(route:List<Offset>,distance:Float):Offset {
    if(route.isEmpty()) return Offset.Zero
    var remaining=distance.coerceAtLeast(0f)
    for((a,b) in route.zipWithNext()) {
        val length=(b-a).getDistance()
        if(remaining<=length && length>0f) return a+(b-a)*(remaining/length)
        remaining-=length
    }
    return route.last()
}

internal fun DrawScope.drawCoreFlow(cols:Int,rows:Int,unit:Float,time:Float,skin:TerminalSkin,night:Boolean) {
    if(!skin.coreFlow || skin.flowBrightness<=0f || skin.effect==GridEffect.Off) return
    val routes=coreRoutes(cols,rows);val core=routes.firstOrNull()?.firstOrNull() ?: return
    val power=boundedLight(skin.flowBrightness)
    val hue=if(skin.rainbow) 185f else skin.palette.hue
    val tint=Color.hsv(hue,.42f,if(night)1f else .55f)
    val white=if(night)Color(0xFFE4FCFF) else tint
    val pulse=.8f+.2f*sin(time*2.2f)
    val origin=core*unit
    // Broad, faint bloom; tight bright rings. Keep the center hollow for readability.
    for(layer in 4 downTo 1) drawCircle(tint.copy(alpha=(.014f*power*pulse).coerceAtMost(.09f)),unit*(1.2f+layer*.32f),origin,style=Stroke(unit*.4f))
    drawCircle(tint.copy(alpha=(.15f*power).coerceAtMost(.42f)),unit*1.25f,origin,style=Stroke(unit*.06f))
    drawArc(white.copy(alpha=(.28f*power*pulse).coerceAtMost(.65f)),time*18f,245f,false,
        origin-Offset(unit,unit),androidx.compose.ui.geometry.Size(unit*2,unit*2),style=Stroke(unit*.065f,cap=StrokeCap.Round))
    routes.forEachIndexed { index,route ->
        val length=circuitLength(route)
        if(length<=0f) return@forEachIndexed
        val head=((time*3.2f+index*.81f)%(length+5f))
        // Short segments sample arclength, so bends never produce diagonal shortcuts.
        for(part in 0..23) {
            val from=head-part*.12f;val to=from-.1f
            if(to<1.35f || from>length) continue
            val a=circuitPoint(route,to)*unit;val b=circuitPoint(route,from)*unit
            val fade=(1f-part/24f).pow(2)
            val color=if(skin.rainbow)Color.hsv((hue+index*12f)%360,.5f,if(night)1f else .55f) else tint
            drawLine(color.copy(alpha=(.035f*power*fade).coerceAtMost(.14f)),a,b,unit*.28f,StrokeCap.Round)
            drawLine(color.copy(alpha=(.24f*power*fade).coerceAtMost(.55f)),a,b,unit*.07f,StrokeCap.Round)
            drawLine(white.copy(alpha=(.36f*power*fade).coerceAtMost(.7f)),a,b,unit*.022f,StrokeCap.Round)
        }
    }
}
