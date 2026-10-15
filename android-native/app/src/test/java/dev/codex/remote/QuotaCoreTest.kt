package dev.codex.remote

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuotaCoreTest {
    @Test fun planDoesNotInventOrHideRealWindowsAndSurvivesEncryptedCacheSerialization() {
        for(plan in listOf("plus","pro",null)) for(count in 1..2) {
            val windows=List(count) { UsageWindow(30.0+it,if(it==0)300 else 10080,1900000000) }
            val original=UsageSnapshot(listOf(UsageLimit("codex",1800000000000,windows,plan)))
            val restored=parseUsageSnapshot(quotaJson(original) as JSONObject)!!
            assertEquals(original,restored)
            assertEquals(count,quotaWindows(restored.limits.single()).size)
        }
        assertNull(quotaPlan("PRIVATE_TOKEN"));assertNull(quotaPlan(null))
        assertEquals("pro",quotaPlan("PRO"));assertTrue(quotaWindows(null).isEmpty())
    }
    @Test fun oneAndTwoCoreNumbersStayCenteredAndInsideNarrowPortraitLandscapeAndLargeFontLayouts() {
        for((width,height) in listOf(280f to 540f,390f to 840f,840f to 390f)) for(count in 1..2) {
            val cores=quotaCoreGeometry(width,height,count)
            assertEquals(count,cores.size)
            for(core in cores) {
                assertTrue(core.center.x-core.radius>=0);assertTrue(core.center.x+core.radius<=width)
                assertTrue(core.center.y-core.radius>=0);assertTrue(core.center.y+core.radius<=height)
                for(text in listOf("0%","72%","100%","—")) {
                    val cells=quotaDigitCells(text)
                    assertTrue(cells.isNotEmpty());assertTrue(cells.size<150)
                    assertTrue(cells.all { it.first in 0 until text.length*6 && it.second in 0..6 })
                }
            }
            if(count==2)assertTrue(cores[0].center.x+cores[0].radius<cores[1].center.x-cores[1].radius)
        }
        assertTrue(quotaCoreGeometry(0f,100f,2).isEmpty())
        assertNotEquals(quotaDigitCells("0%"),quotaDigitCells("100%"))
    }
}
