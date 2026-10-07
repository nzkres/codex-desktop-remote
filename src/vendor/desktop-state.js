import { object, ControlError } from "./desktop-ipc.js";

export function nativeTurns(state) {
    if (state.turnHistory != null && state.turnHistory.kind !== "canonical")
        throw new Error("Unsupported Desktop history mode");
    const history = state.turnHistory?.kind === "canonical" ? state.turnHistory.history : null;
    let turns;
    if (history) {
        if (!Array.isArray(history.islands) || !object(history.entitiesByKey))
            throw new Error("Unsupported Desktop history");
        const seen = new Set();
        turns = history.islands.flatMap((island) => {
            if (!Array.isArray(island.entries))
                throw new Error("Unsupported Desktop history island");
            return island.entries.flatMap((entry) => {
                const key = entry.value;
                if (typeof key !== "string" || ["__proto__", "prototype", "constructor"].includes(key) || seen.has(key))
                    return [];
                seen.add(key);
                return Object.hasOwn(history.entitiesByKey, key) ? [history.entitiesByKey[key]] : [];
            });
        });
    }
    else
        turns = Array.isArray(state.turns) ? state.turns : [];
    return turns.filter((t) => object(t) && typeof t.turnId === "string").map((t) => ({
        ...t, id: t.turnId, items: Array.isArray(t.items) ? t.items : [],
    }));
}
export function activeTurn(state) {
    return nativeTurns(state).reverse().find((t) => t.status === "inProgress")?.id ?? null;
}
export function assertIdle(state) {
    if (activeTurn(state) || state.threadRuntimeStatus?.type === "active" || state.requests?.length || state.unconfirmedTurnSubmissions?.length) {
        throw new ControlError("thread_busy", "这段对话正在运行或等待确认。请等待结束，或先停止当前回合；消息未发送。");
    }
    if (state.threadRuntimeStatus?.type !== "idle")
        throw new ControlError("runtime_status_unknown", "执行端尚未确认空闲状态，消息未发送。请稍后刷新。", 503);
}
