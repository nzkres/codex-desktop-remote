export const milliseconds = (value) => typeof value === "number" && Number.isFinite(value) && value > 0 ? value : null;
export function turnTiming(turn) {
    const startedAtMs = milliseconds(turn.turnStartedAtMs) ?? milliseconds(turn.startedAtMs) ?? (milliseconds(turn.startedAt) ? turn.startedAt * 1000 : null);
    const ended = milliseconds(turn.completedAtMs) ?? (milliseconds(turn.completedAt) ? turn.completedAt * 1000 : null);
    const completed = ["completed", "failed", "interrupted"].includes(turn.status);
    const endedAtMs = completed && ended != null && (startedAtMs == null || ended >= startedAtMs) ? ended : null;
    const supplied = typeof turn.durationMs === "number" && Number.isFinite(turn.durationMs) && turn.durationMs >= 0 ? turn.durationMs : null;
    return { startedAtMs, endedAtMs, durationMs: completed ? endedAtMs != null && startedAtMs != null ? endedAtMs - startedAtMs : supplied : null, completed };
}
