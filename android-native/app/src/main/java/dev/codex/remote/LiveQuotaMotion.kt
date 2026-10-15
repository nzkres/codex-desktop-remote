package dev.codex.remote

import kotlin.math.pow
import kotlin.math.exp

internal data class QuotaPresentation(val numbers: Map<Int, Float>, val sweep: Float?, val resetLight: Float, val animating: Boolean, val scatter: Float = 0f, val flare: Float = 0f)
internal class QuotaNumberMotion {
    private var captured = 0L
    private var reset = 0L
    private var target = 0f
    private var from = 0f
    private var elapsed = 0f
    private var duration = 1.35f
    private var first = true
    private var resetBurst = false
    private var intro = false
    val value: Float get() {
        if(intro) return when {
            elapsed<1.35f -> 100f*(1f-(1f-elapsed/1.35f).pow(3))
            elapsed<2f -> 100f
            elapsed<3.1f -> { val t=(elapsed-2f)/1.1f;100f+(target-100f)*t*t*(3f-2f*t) }
            else -> target
        }
        val p = (elapsed / duration).coerceIn(0f, 1f)
        return (from + (target - from) * (1f - (1f - p).pow(3))).coerceIn(0f, 100f)
    }
    val moving get() = elapsed < duration
    val flash get() = if (resetBurst && moving) (1f - elapsed / duration).coerceIn(0f, 1f) else 0f
    fun observe(window: UsageWindow, at: Long) {
        if (at <= captured) return
        val next = window.remaining.toFloat()
        val confirmedReset = !first && reset > 0 && window.resetsAt > reset && at / 1000 >= reset && next > target
        if(!first && intro && moving) { target=next;captured=at;reset=window.resetsAt;return }
        if (first || next != target) {
            from = if (first) 0f else value
            target = next; elapsed = 0f;intro=first; duration = if(first)3.1f else if(confirmedReset)1.35f else .3f
            resetBurst = confirmedReset
        }
        captured = at; reset = window.resetsAt; first = false
    }
    fun advance(dt: Float, finish: Boolean = false) { elapsed = if (finish) duration else (elapsed + dt.coerceIn(0f, .1f)).coerceAtMost(duration) }
}

/** One scene per confirmed GPT account, outside either selected computer/page. */
internal class LiveQuotaScene {
    val activity = QuotaActivityMotion()
    private val numbers = linkedMapOf<Int, QuotaNumberMotion>()
    private var opening = 0f
    private var initialized = false
    private var scatter=0f
    private var flare=0f
    fun observe(limit: UsageLimit, fresh: Boolean) {
        if(fresh && !initialized) { initialized=true;opening=0f;numbers.clear() }
        quotaWindows(limit).forEach { w -> numbers.getOrPut(w.minutes) { QuotaNumberMotion() }.observe(w, limit.capturedAt) }
        if (!fresh) numbers.values.forEach { it.advance(0f, true) }
    }
    fun advance(dt: Float, running: Boolean, motion: Boolean) {
        if (!motion) { opening = 1.15f; numbers.values.forEach { it.advance(0f, true) };scatter=0f;return }
        activity.advance(dt, running)
        val on=running || initialized && (opening<1.15f || numbers.values.any { it.moving })
        val previous=scatter
        scatter+=(if(on)1f-scatter else -scatter)*(1f-exp(-dt.coerceIn(0f,.1f)/if(on)1.6f else 1.8f))
        if(!on && scatter<.001f)scatter=0f
        fun speed(v:Float)=.025f*v+.7f*v*v
        flare=(flare+(speed(previous)+speed(scatter))*.5f*dt.coerceIn(0f,.1f))%2.1f
        val wasOpen = opening >= 1.15f
        if(initialized) opening = (opening + dt.coerceIn(0f, .1f)).coerceAtMost(1.15f)
        if (wasOpen) numbers.values.forEach { it.advance(dt) }
    }
    val presentation get() = QuotaPresentation(numbers.mapValues { it.value.value },
        if(initialized && opening < 1.15f) opening / 1.15f else null, numbers.values.maxOfOrNull { it.flash } ?: 0f,
        initialized && opening < 1.15f || numbers.values.any { it.moving } || scatter>0f,scatter,flare)
}

internal data class QuotaRunObservation(val running: Boolean, val at: Long)
internal class LiveQuotaRegistry {
    private val accounts = linkedMapOf<String, String>()
    private val observations = linkedMapOf<Pair<String,String>, QuotaRunObservation>()
    private val scenes = linkedMapOf<String, LiveQuotaScene>()
    private val accountAt = linkedMapOf<String,Long>()
    var selectedDevice = ""
    fun observe(device: String, thread: String, running: Boolean?, usage: UsageSnapshot?, now: Long) {
        val at=usage?.limits?.maxOfOrNull { it.capturedAt }
        if(at!=null && at <= now+60000 && now-at<=600000 && at >= (accountAt[device] ?: 0L)) {
            accountAt[device]=at
            if(usage.accountKey==null) accounts.remove(device) else {
                accounts[device]=usage.accountKey
                scenes["device:$device"]?.let { if(usage.accountKey !in scenes) scenes[usage.accountKey]=it }
                scenes.remove("device:$device")
            }
        }
        if (running != null) observations[device to thread] = QuotaRunObservation(running,now)
        while (observations.size > 2000) observations.remove(observations.keys.first())
    }
    fun retain(device: String, threads: Set<String>) { observations.keys.filter { it.first==device && it.second !in threads }.forEach { observations.remove(it) } }
    fun unavailable(device: String) { observations.keys.filter { it.first == device }.forEach { observations.remove(it) } }
    fun key(device: String, usage: UsageSnapshot?): String = accounts[device] ?: "device:$device"
    fun running(key: String, now: Long): Boolean = observations.any { (id, sample) ->
        val account = accounts[id.first] ?: "device:${id.first}"
        account == key && sample.running && now - sample.at in 0..120_000
    }
    fun scene(device: String, quota: DeviceQuota?, now: Long): LiveQuotaScene {
        val key=key(device,quota?.snapshot)
        val scene=scenes.getOrPut(key) { LiveQuotaScene() }
        quotaLimit(quota)?.let { scene.observe(it, quota?.unavailable!=true && !usageStale(it,now)) }
        return scene
    }
    fun advance(dt: Float, now: Long, motion: Boolean) {
        accountAt.keys.filter { now-(accountAt[it] ?: 0L)>600000 }.toList().forEach { accounts.remove(it);accountAt.remove(it) }
        scenes.forEach { (key,scene) -> scene.advance(dt,running(key,now),motion) } }
    fun animating(now:Long)=scenes.any { (key,scene) -> scene.presentation.animating || scene.activity.frame.energy>0f || running(key,now) }
}
