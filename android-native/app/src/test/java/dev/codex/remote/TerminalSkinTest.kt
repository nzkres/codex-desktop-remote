package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class TerminalSkinTest {
    @Test fun stickyTracksPreviousTaskWhenCurrentReentersViewport() {
        val tasks=listOf(0,4,8)
        assertEquals(4,stickyTaskIndex(tasks,5,mapOf(4 to 100,5 to 180),120))
        assertEquals(0,stickyTaskIndex(tasks,4,mapOf(4 to 180,5 to 240),120))
        assertNull(stickyTaskIndex(tasks,0,mapOf(0 to 180),120))
        assertEquals(4,stickyTaskIndex(tasks,4,mapOf(4 to 120),120))
    }
    @Test fun motionHonorsLifecycleIdleAndCorruptPreferences() {
        assertEquals(0f,gridPace(true,false,false,2f))
        assertEquals(0f,gridPace(false,true,true,1f))
        assertEquals(.25f,gridPace(false,false,true,1f))
        assertEquals(1.5f,gridPace(true,false,true,Float.NaN))
        assertEquals(3f,gridPace(true,false,true,100f))
    }
    @Test fun randomEffectsRemainBoundedAndChangeAcrossSeedsAndTime() {
        for(effect in GridEffect.entries) {
            val simulation=GridSimulation(Random(41))
            repeat(600) { tick ->
                val cells=simulation.cells(effect,tick*.21f,16,30)
                assertTrue(cells.size<=16*30)
                assertTrue(cells.all { it.x in 0..15 && it.y in 0..29 && it.strength in 0f..1f })
            }
        }
        for(effect in listOf(GridEffect.Snake,GridEffect.Blocks,GridEffect.Random)) {
            val a=GridSimulation(Random(1)); val b=GridSimulation(Random(2))
            val one=(0..100).map { a.cells(effect,it*.3f,16,30) }
            val two=(0..100).map { b.cells(effect,it*.3f,16,30) }
            assertNotEquals(one,two)
            assertTrue(one.distinct().size>10)
        }
    }
    @Test fun resizeAndSmallWindowsNeverProduceOffGridCells() {
        val sim=GridSimulation(Random(3))
        repeat(100) { sim.cells(GridEffect.Snake,it.toFloat(),20,40) }
        assertTrue(sim.cells(GridEffect.Snake,101f,1,1).all { it.x==0 && it.y==0 })
        assertTrue(sim.cells(GridEffect.Blocks,102f,0,0).isEmpty())
    }
}
