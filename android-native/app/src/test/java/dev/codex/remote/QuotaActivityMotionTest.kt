package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test

class QuotaActivityMotionTest {
    @Test fun onlyMatchingKnownAccountAggregatesAnyDevice() {
        val devices = listOf(QuotaActivityDevice("work", "a", false), QuotaActivityDevice("home", "a", true),
            QuotaActivityDevice("other", "b", false), QuotaActivityDevice("unknown", null, true))
        assertTrue(accountQuotaRunning("a", devices))
        assertFalse(accountQuotaRunning("b", devices))
        assertFalse(accountQuotaRunning(null, devices))
        assertFalse(accountQuotaRunning("", devices))
    }
    @Test fun startsDimAndSlowThenBuildsContinuously() {
        val model = QuotaActivityMotion()
        assertEquals(0f, model.frame.energy, 0f)
        val first = model.advance(1f/60, true)
        assertTrue(first.energy < .02f)
        assertTrue(first.speed < .1f)
        var previous = first
        repeat(300) {
            val next = model.advance(1f/60, true)
            assertTrue(next.energy >= previous.energy)
            assertTrue(next.speed >= previous.speed)
            assertTrue(next.energy - previous.energy < .02f)
            previous = next
        }
        assertTrue(previous.energy > .95f)
        assertTrue(previous.speed > 90f)
    }
    @Test fun stopEasesOutInsteadOfSnappingAndRestartHasNoAngleReset() {
        val model = QuotaActivityMotion()
        repeat(360) { model.advance(1f/60, true) }
        val before = model.frame
        val after = model.advance(1f/60, false)
        assertTrue(after.energy > .9f)
        assertTrue(after.energy < before.energy)
        repeat(120) { model.advance(1f/60, false) }
        val slow = model.frame
        assertTrue(slow.speed < 15f)
        val restarted = model.advance(1f/60, true)
        assertTrue(restarted.energy > slow.energy)
        assertTrue((restarted.angle - slow.angle + 360f) % 360f < 2f)
        repeat(900) { model.advance(1f/60, false) }
        assertEquals(0f, model.frame.speed, 0f)
    }
    @Test fun noLargeCatchupOrNanOnResume() {
        val model = QuotaActivityMotion()
        assertEquals(model.frame, model.advance(Float.NaN, true))
        assertEquals(model.frame, model.advance(-1f, true))
        assertTrue(model.advance(3600f, true).energy < .07f)
    }
    @Test fun breathingIsBoundedAndDoesNotChangeQuotaNumbers() {
        val model = QuotaActivityMotion()
        val fixture = UsageWindow(32.0, 300, 1L)
        val brightness = mutableListOf<Float>()
        repeat(600) { brightness += model.advance(1f/60, true).light }
        assertEquals(68, fixture.remaining)
        assertTrue(brightness.min() >= 1f)
        assertTrue(brightness.max() <= 2.15f)
        assertTrue(brightness.takeLast(240).max() - brightness.takeLast(240).min() > .3f)
    }
}
