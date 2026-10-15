package dev.codex.remote

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

internal data class LightCell(val x: Int, val y: Int, val strength: Float, val hue: Float)

/** Bounded state; grid coordinates are shared by lines, snakes and tetrominoes. */
internal class GridSimulation(private val random: Random = Random.Default) {
    private var step = -1
    private var snake = mutableListOf<Pair<Int, Int>>()
    private var block = emptyList<Pair<Int, Int>>()
    private var blockX = 0
    private var blockY = -4
    private var blockHue = 0f
    private var spark = emptyList<LightCell>()
    private var dimensions = 0 to 0
    fun cells(effect: GridEffect, time: Float, cols: Int, rows: Int): List<LightCell> {
        if (cols < 1 || rows < 1) return emptyList()
        if (dimensions != (cols to rows)) {
            dimensions = cols to rows; step = -1; snake.clear(); block = emptyList()
        }
        val tick = floor(time * 5).toInt()
        if (tick != step) {
            step = tick
            if (snake.isEmpty()) snake.add(random.nextInt(cols) to random.nextInt(rows))
            val head = snake.first()
            val next = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)
                .map { (x,y) -> (head.first+x) to (head.second+y) }
                .filter { (x,y) -> x in 0 until cols && y in 0 until rows && (x to y) !in snake.take(5) }
            if (next.isEmpty()) snake.clear() else snake.add(0,next.random(random))
            while (snake.size > 12) snake.removeAt(snake.lastIndex)
            if (block.isEmpty() || blockY > rows) {
                block = listOf(listOf(0 to 0,1 to 0,0 to 1,1 to 1), listOf(0 to 0,1 to 0,2 to 0,3 to 0),
                    listOf(0 to 0,1 to 0,2 to 0,1 to 1),listOf(0 to 0,0 to 1,0 to 2,1 to 2),
                    listOf(0 to 0,1 to 0,1 to 1,2 to 1)).random(random)
                repeat(random.nextInt(4)) { block = block.map { (x,y) -> -y to x } }
                val minX=block.minOf { it.first }; val minY=block.minOf { it.second }
                block=block.map { (x,y) -> (x-minX) to (y-minY) }
                blockX=random.nextInt((cols-block.maxOf { it.first }).coerceAtLeast(1)); blockY=-4
                blockHue=random.nextFloat()*360
            } else if (random.nextFloat() > .2f) blockY++
            spark=List((cols*rows/35).coerceIn(2,24)) { LightCell(random.nextInt(cols),random.nextInt(rows),random.nextFloat()*.6f+.25f,random.nextFloat()*360) }
        }
        return when(effect) {
            GridEffect.Off -> emptyList()
            GridEffect.Snake -> snake.mapIndexed { i,(x,y) -> LightCell(x,y,1f-i/13f,(time*15+i*18)%360) }
            GridEffect.Blocks -> block.map { (x,y) -> LightCell(x+blockX,y+blockY,.8f,blockHue) }.filter { it.x in 0 until cols && it.y in 0 until rows }
            GridEffect.Random, GridEffect.Stars -> spark
            GridEffect.Rhythm -> (0 until cols).flatMap { x ->
                val height=((sin(time*2+x*.6f)+1)*rows*.13f).toInt()
                (0..height).map { y -> LightCell(x,rows-1-y,.6f,(x*27f+time*15)%360) }
            }
            GridEffect.Scan -> (0 until cols).map { x -> LightCell(x,((time*3).toInt()%rows),.7f,x*20f%360) }
            GridEffect.Breathe -> spark.map { it.copy(strength=it.strength*(.5f+.5f*sin(time))) }
        }
    }
}

@Composable internal fun TerminalGrid(running: Boolean, modifier: Modifier = Modifier, quota: DeviceQuota? = null,
    showQuota: Boolean = true, live: LiveQuotaRuntime? = null) {
    val skin=LocalChatAppearance.current.skin
    val motion=rememberChatMotion()
    val pace=gridPace(running,skin.idleStill,motion,skin.speed)
    val simulation=remember(skin.effect) { GridSimulation() }
    val clock=remember { mutableFloatStateOf(0f) }
    LaunchedEffect(pace,skin.effect) {
        if(pace==0f || skin.effect==GridEffect.Off) return@LaunchedEffect
        var previous=withFrameNanos { it }
        while(true) {
            delay(33)
            val now=withFrameNanos { it }
            clock.floatValue += ((now-previous)/1_000_000_000f).coerceAtMost(.1f)*pace
            previous=now
        }
    }
    val night=LocalAppPalette.current.isNight
    var observedNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(quota) { while(true) { observedNow=System.currentTimeMillis();delay(30000) } }
    Canvas(modifier) {
        live?.tick // Read only during drawing; device/page switches share one account scene.
        val liveNow=System.currentTimeMillis()
        val scene=if(live!=null && showQuota) live.registry.scene(live.registry.selectedDevice,quota,liveNow) else null
        if(!motion) scene?.advance(0f,false,false)
        val presentation=scene?.presentation
        val unit=24.dp.toPx()
        val cols=ceil(size.width/unit).toInt(); val rows=ceil(size.height/unit).toInt()
        val line=if(night) Color.White else Color.Black
        val lineAlpha=(.075f*boundedLight(skin.lineBrightness)).coerceAtMost(.3f)
        for(x in 0..cols) drawLine(line.copy(alpha=lineAlpha),Offset(x*unit,0f),Offset(x*unit,size.height),.65.dp.toPx())
        for(y in 0..rows) drawLine(line.copy(alpha=lineAlpha),Offset(0f,y*unit),Offset(size.width,y*unit),.65.dp.toPx())
        drawCoreFlow(cols,rows,unit,clock.floatValue,skin,night,quotaCore=true)
        simulation.cells(skin.effect,clock.floatValue,cols,rows).forEach { cell ->
            val hue=if(skin.rainbow) cell.hue else skin.palette.hue
            val color=Color.hsv(hue,.55f,if(night) 1f else .65f, (cell.strength*.25f*boundedLight(skin.brightness)).coerceIn(0f,.48f))
            drawRect(color,Offset(cell.x*unit+1,cell.y*unit+1),Size(unit-2,unit-2))
        }
        presentation?.sweep?.let { progress ->
            val y=size.height*(1f-progress)
            val beam=if(night) Color(0xFFB0EDDA) else Color(0xFF285A4C)
            drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to Color.Transparent,.5f to beam.copy(alpha=(.2f*boundedLight(skin.flowBrightness)).coerceAtMost(.35f)),1f to Color.Transparent,
                startY=y-size.height*.12f,endY=y+size.height*.12f),
                topLeft=Offset(0f,y-size.height*.12f),size=Size(size.width,size.height*.24f))
        }
        val runningNow=live?.registry?.let { it.running(it.key(it.selectedDevice,quota?.snapshot),liveNow) }==true
        val frame=scene?.activity?.frame?.let { if(motion) it else QuotaActivityFrame(if(runningNow).65f else 0f,it.angle,.5f) }
        if (showQuota) drawQuotaCore(quota,observedNow,clock.floatValue,skin,night,activity=frame,presentation=presentation)
    }
}
