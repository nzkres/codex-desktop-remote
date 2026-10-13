// 统一整理消息时间字段，供手机显示和顺序处理使用；不控制模型执行。

// 将有效、有限且大于零的数值保留为毫秒；无效时间返回 null。
export const milliseconds = (value) => typeof value === "number" && Number.isFinite(value) && value > 0 ? value : null;
// 输入原生回合；优先使用毫秒字段，旧秒数字段再乘 1000。
// 只有已结束状态才给出结束时间和时长；无有效起止时间时可回退到提供的 durationMs。
export function turnTiming(turn) {
    const startedAtMs = milliseconds(turn.turnStartedAtMs) ?? milliseconds(turn.startedAtMs) ?? (milliseconds(turn.startedAt) ? turn.startedAt * 1000 : null);
    const ended = milliseconds(turn.completedAtMs) ?? (milliseconds(turn.completedAt) ? turn.completedAt * 1000 : null);
    const completed = ["completed", "failed", "interrupted"].includes(turn.status);
    const endedAtMs = completed && ended != null && (startedAtMs == null || ended >= startedAtMs) ? ended : null;
    const supplied = typeof turn.durationMs === "number" && Number.isFinite(turn.durationMs) && turn.durationMs >= 0 ? turn.durationMs : null;
    return { startedAtMs, endedAtMs, durationMs: completed ? endedAtMs != null && startedAtMs != null ? endedAtMs - startedAtMs : supplied : null, completed };
}
