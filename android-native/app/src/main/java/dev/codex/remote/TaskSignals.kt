package dev.codex.remote

/** Pure event policy: no Android APIs, network, transcript text or credentials. */
data class SignalActivity(val id: String, val fingerprint: String, val active: Boolean, val updatedAt: Long)
data class SignalSnapshot(val turnId: String?, val completed: Boolean, val plan: Boolean, val questions: Set<String>, val approvals: Set<String>)
data class SignalCheckpoint(val fingerprint: String, val delivered: Set<String> = emptySet())
data class TaskSignal(val kind: String, val key: String)

class TaskSignalTracker(
    var initialized: Boolean = false,
    val startedAt: Long,
    val checkpoints: MutableMap<String, SignalCheckpoint> = linkedMapOf(),
) {
    fun candidates(activities: List<SignalActivity>): List<SignalActivity> {
        val first = !initialized
        initialized = true
        return activities.filter { activity ->
            val previous = checkpoints[activity.id]
            // Starting the feature must not announce every old completed conversation.
            if (previous == null && !activity.active && (first || activity.updatedAt < startedAt)) {
                checkpoints[activity.id] = SignalCheckpoint(activity.fingerprint)
                false
            } else activity.active || previous?.fingerprint != activity.fingerprint
        }
    }

    fun preview(activity: SignalActivity, snapshot: SignalSnapshot): List<TaskSignal> {
        val delivered = checkpoints[activity.id]?.delivered.orEmpty()
        return buildList {
            snapshot.questions.sorted().forEach { add(TaskSignal("question", "question:$it")) }
            snapshot.approvals.sorted().forEach { add(TaskSignal("approval", "approval:$it")) }
            if (snapshot.completed && !snapshot.turnId.isNullOrBlank()) {
                add(TaskSignal(if (snapshot.plan) "plan" else "completed", "completed:${snapshot.turnId}"))
            }
        }.filter { it.key !in delivered }
    }

    // Only commit after successful reads AND notification delivery. A transient error retries.
    fun acknowledge(activity: SignalActivity, signals: List<TaskSignal>) {
        val keys = checkpoints[activity.id]?.delivered.orEmpty() + signals.map { it.key }
        checkpoints[activity.id] = SignalCheckpoint(activity.fingerprint, keys.takeLast(128).toSet())
    }
}

private fun <T> Set<T>.takeLast(count: Int): List<T> = toList().takeLast(count)
