package dev.codex.remote

import androidx.compose.ui.geometry.Offset
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CoreGlowUsageTest {
    @Test fun circuitElbowsStayOnGridAndTravelOutwardInPortraitAndLandscape() {
        for((cols,rows) in listOf(16 to 32,32 to 16,2 to 2)) {
            val routes=coreRoutes(cols,rows)
            assertEquals(16,routes.size)
            for(route in routes) {
                assertTrue(route.all { it.x%1f==0f && it.y%1f==0f && it.x in 0f..cols.toFloat() && it.y in 0f..rows.toFloat() })
                assertTrue(route.zipWithNext().all { (a,b)-> a.x==b.x || a.y==b.y })
                val length=circuitLength(route)
                assertEquals(route.first(),circuitPoint(route,0f))
                assertEquals(route.last(),circuitPoint(route,length+10))
                assertTrue(circuitPoint(route,length*.8f).let { (it-route.first()).getDistance() } >=
                    circuitPoint(route,length*.2f).let { (it-route.first()).getDistance() })
            }
        }
        assertTrue(coreRoutes(0,0).isEmpty())
        assertEquals(Offset.Zero,circuitPoint(emptyList(),0f))
    }
    @Test fun brightnessIsIndependentOfSpeedAndSupportsZeroAndFourHundredPercent() {
        assertEquals(4f,boundedLight(10f));assertEquals(0f,boundedLight(-1f));assertEquals(1f,boundedLight(Float.NaN))
        assertEquals(2f,boundedMotion(10f))
    }
    @Test fun quotaUsesRemainingPercentActualWindowAndSnapshotAge() {
        val now=1700000000000L
        val quota=UsageSnapshot(listOf(UsageLimit("codex",now,listOf(UsageWindow(89.0,10080,now/1000+1000)))))
        assertEquals("额度 · 7天剩余 11%",usageSummary(quota,now))
        assertTrue(usageSummary(quota,now+601000).startsWith("上次额度"))
        assertTrue(usageSummary(quota,now+1000001).startsWith("上次额度"))
        assertEquals("额度未同步",usageSummary(null,now))
        assertEquals("额度暂不可用",usageSummary(UsageSnapshot(emptyList()),now))
    }
    @Test fun malformedWindowsDoNotBecomeZeroUsageOrFullAllowance() {
        val parsed=parseUsageSnapshot(JSONObject("""{"limits":[{"id":"codex","capturedAt":1700000000000,"windows":[{"usedPercent":-1,"windowDurationMins":300,"resetsAt":1700001000},{"usedPercent":100,"windowDurationMins":10080,"resetsAt":1700001000}]}]}"""))!!
        assertEquals(1,parsed.limits.single().windows.size)
        assertEquals(0,parsed.limits.single().windows.single().remaining)
        assertNull(parseUsageSnapshot(null))
    }
}
