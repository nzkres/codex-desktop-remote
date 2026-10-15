package dev.codex.remote

import kotlin.math.exp
import kotlin.math.sin

/** Only explicitly matching, known accounts aggregate. Never group missing identities. */
internal data class QuotaActivityDevice(val id: String, val account: String?, val running: Boolean)
internal fun accountQuotaRunning(account: String?, devices: List<QuotaActivityDevice>): Boolean =
    !account.isNullOrBlank() && devices.any { it.account == account && it.running }

internal data class QuotaActivityFrame(val energy: Float, val angle: Float, val breath: Float) {
    val speed: Float get() = 105f * energy * energy
    val light: Float get() = 1f + energy * (.65f + .5f * breath)
}

/** Integrate angle rather than multiplying time by speed: acceleration never jumps phase. */
internal class QuotaActivityMotion {
    private var energy = 0f
    private var angle = 0f
    private var phase = 0f
    val frame: QuotaActivityFrame get() = QuotaActivityFrame(energy, angle, (sin(phase) + 1f) * .5f)
    fun advance(seconds: Float, running: Boolean): QuotaActivityFrame {
        if (!seconds.isFinite() || seconds <= 0f) return frame
        // Background time must not cause catch-up flashes or a leap on resume.
        val dt = seconds.coerceAtMost(.1f)
        val before = frame.speed
        val target = if (running) 1f else 0f
        val tau = if (running) 1.6f else 1.8f
        energy = (energy + (target - energy) * (1f - exp(-dt / tau))).coerceIn(0f, 1f)
        if (!running && energy < .001f) energy = 0f
        angle = (angle + (before + frame.speed) * .5f * dt) % 360f
        phase = (phase + (1.1f + energy * 1.7f) * dt) % (2f * Math.PI.toFloat())
        return frame
    }
}
