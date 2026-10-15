package dev.codex.remote

import org.junit.Assert.*
import org.junit.Test

class LiveQuotaMotionTest {
    private val now=1800000000000L
    private fun usage(key:String?="a".repeat(64),used:Double=10.0,at:Long=now,reset:Long=now/1000+1000)=
        UsageSnapshot(listOf(UsageLimit("codex",at,listOf(UsageWindow(used,10080,reset)),"pro")),accountKey=key)
    private fun quota(u:UsageSnapshot=usage())=DeviceQuota("task",u)
    @Test fun anotherComputerInSameKnownAccountDrivesSameSceneWithoutRestart() {
        val r=LiveQuotaRegistry();val u=usage()
        r.observe("work","t",false,u,now);r.observe("home","t",true,u,now)
        val first=r.scene("work",quota(),now)
        repeat(60){r.advance(.1f,now,true)}
        val angle=first.activity.frame.angle
        assertTrue(first.activity.frame.energy>.9f)
        assertSame(first,r.scene("home",quota(),now))
        assertEquals(angle,r.scene("home",quota(),now).activity.frame.angle,0f)
        r.observe("work","t",true,u,now);r.observe("home","t",false,u,now)
        assertTrue(r.running(u.accountKey!!,now))
        r.observe("work","t",false,u,now)
        r.advance(.1f,now,true);assertTrue(first.activity.frame.energy>0f)
        repeat(160){r.advance(.1f,now,true)};assertEquals(0f,first.activity.frame.energy,0f)
    }
    @Test fun unknownAndDifferentAccountsNeverAggregateAndExpiredEvidenceCannotRun() {
        val r=LiveQuotaRegistry();r.observe("home","t",true,usage(),now)
        assertFalse(r.running("b".repeat(64),now));assertFalse(r.running("device:other",now))
        assertFalse(r.running("a".repeat(64),now+120001))
        r.observe("home","t",true,usage(null,at=now+1),now+1)
        assertFalse(r.running("a".repeat(64),now+1))
    }
    @Test fun scanThenHundredThenActualQuota() {
        val scene=LiveQuotaScene();scene.observe(usage().limits.first(),true)
        repeat(10){scene.advance(.1f,false,true)}
        assertNotNull(scene.presentation.sweep);assertEquals(0f,scene.presentation.numbers[10080]!!,0f)
        repeat(17){scene.advance(.1f,false,true)}
        assertEquals(100f,scene.presentation.numbers[10080]!!,.01f)
        repeat(20){scene.advance(.1f,false,true)}
        assertNull(scene.presentation.sweep);assertEquals(90f,scene.presentation.numbers[10080]!!,0f)
    }
    @Test fun confirmedNewResetPeriodAnimatesUpButReplayAndCorrectionDoNotFlash() {
        val n=QuotaNumberMotion();val reset=now/1000+100
        n.observe(UsageWindow(90.0,300,reset),now);n.advance(0f,true)
        n.observe(UsageWindow(0.0,300,reset+300),now+101000)
        assertTrue(n.flash>0f);assertEquals(10f,n.value,0f)
        repeat(20){n.advance(.1f)};assertEquals(100f,n.value,0f)
        n.observe(UsageWindow(99.0,300,reset),now);assertEquals(100f,n.value,0f)
        n.observe(UsageWindow(1.0,300,reset+300),now+102000);assertEquals(0f,n.flash,0f)
    }
    @Test fun reducedMotionShowsActualValueImmediatelyAndCachedDataDoesNotConsumeOpening() {
        val scene=LiveQuotaScene();scene.observe(usage().limits.first(),false)
        assertEquals(90f,scene.presentation.numbers[10080]!!,0f)
        scene.observe(usage().limits.first(),true);assertNotNull(scene.presentation.sweep)
        scene.advance(0f,false,false);assertNull(scene.presentation.sweep)
        assertEquals(90f,scene.presentation.numbers[10080]!!,0f)
    }
    @Test fun glintRampsDecaysAndStopsWithContinuousPhase() {
        val s=LiveQuotaScene();s.observe(usage().limits.first(),true)
        s.advance(.1f,true,true);assertTrue(s.presentation.scatter in 0f.. .1f)
        repeat(70){s.advance(.1f,true,true)};assertTrue(s.presentation.scatter>.95f)
        val start=s.presentation.flare;s.advance(.1f,false,true)
        assertTrue(s.presentation.scatter>.8f);assertNotEquals(start,s.presentation.flare)
        repeat(150){s.advance(.1f,false,true)};assertEquals(0f,s.presentation.scatter,0f)
        val stopped=s.presentation.flare;s.advance(.1f,false,true);assertEquals(stopped,s.presentation.flare,0f)
    }
}
