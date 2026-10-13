// 手机增量同步：把原生消息整理为稳定条目，再按游标返回变化和历史分页。
// 游标只帮助显示同步，不是 Desktop 的任务数据库或模型执行回执。

import { createHash } from "node:crypto";
import { milliseconds, turnTiming } from "./conversation-timing.js";
// 摘要只为本地稳定标识与修订比较；text/array 将异常字段收窄到渲染器可处理的形状。
const digest = (value) => createHash("sha256").update(JSON.stringify(value)).digest("hex");
const text = (value) => typeof value === "string" ? value : "";
const array = (value) => Array.isArray(value) ? value.filter((v) => v && typeof v === "object") : [];
const visible = new Set(["userMessage", "agentMessage", "plan", "commandExecution", "fileChange"]);
// Project only the fields the mobile renderer uses. Never ship turn params,
// hidden context, base64 images, or permission/runtime internals with history.
function itemContent(item) {
    // 输入原生 item；输出白名单字段。图片只计数，不复制图像数据。
    return {
        type: text(item.type), text: text(item.text) || array(item.content).map((p) => text(p.text)).filter(Boolean).join("\n"),
        command: text(item.command), aggregatedOutput: text(item.aggregatedOutput), cwd: text(item.cwd),
        status: text(item.status), exitCode: typeof item.exitCode === "number" ? item.exitCode : null,
        changes: array(item.changes).map((c) => ({ path: text(c.path), diff: text(c.diff) })),
        imageCount: array(item.content).filter(part => /image/i.test(text(part.type))).length ||
            (Number.isSafeInteger(item.imageCount) && item.imageCount > 0 ? item.imageCount : 0),
    };
}
function entries(source) {
    // 展开回合为可见条目；用回合/条目身份及出现次数生成稳定 key，内容变化另算 revision。
    // 同时附带回合状态、时间和轮次，供增量同步检测相同条目的后续变化。
    const seen = new Map();
    let round = 0;
    return array(source.thread.turns).flatMap((turn, ti) => {
        const timing = turnTiming(turn);
        const shown = array(turn.items).filter(item => visible.has(item.type));
        const firstUser = shown.find(item => item.type === "userMessage");
        return array(turn.items).flatMap((item, ii) => {
            if (!visible.has(item.type))
                return [];
            const turnId = text(turn.id) || `turn-${ti}`;
            const identity = JSON.stringify([turnId, text(item.id) || `item-${ii}`]);
            const occurrence = seen.get(identity) ?? 0;
            seen.set(identity, occurrence + 1);
            const key = digest([identity, occurrence]).slice(0, 32);
            if (item.type === "userMessage")
                round++;
            const started = milliseconds(turn.aeonAssistantMessageStartedAtMsById?.[item.id]) ?? milliseconds(turn.commandExecutionStartedAtMsById?.[item.id]) ??
                (item === firstUser ? timing.startedAtMs : item.type === "agentMessage" && item.phase === "final" ? milliseconds(turn.finalAssistantStartedAtMs) : null);
            const body = itemContent(item);
            const content = { ...body, contentRevision: digest(body), timestampMs: started ?? milliseconds(item.recordedAtMs),
                timestampKind: started != null ? "start" : "recorded", roundNumber: round,
                turnStartedAtMs: timing.startedAtMs, turnEndedAtMs: timing.endedAtMs, turnDurationMs: timing.durationMs,
                turnCompleted: timing.completed, turnLastItem: item === shown.at(-1) };
            const turnStatus = text(turn.status);
            return [{ key, revision: digest(content), turnId, turnStatus, item: { ...content, id: key } }];
        });
    });
}
function preview(entry) {
    // 对列表预览限制正文、命令、输出及差异长度；fullBytes 记录原条目的 JSON 字节数。
    // 截断时避免将 UTF-16 代理对拆开；完整条目由 item() 单独读取。
    let truncated = false;
    const clip = (value, max) => {
        if (value.length <= max)
            return value;
        truncated = true;
        // Do not leave a dangling UTF-16 surrogate at a preview boundary.
        const end = /[\uD800-\uDBFF]/.test(value[max - 1]) ? max - 1 : max;
        return value.slice(0, end);
    };
    const item = entry.item;
    if (item.changes.length > 5)
        truncated = true;
    const projected = { ...item, text: clip(item.text, 6000), command: clip(item.command, 1000),
        aggregatedOutput: clip(item.aggregatedOutput, 2000), cwd: clip(item.cwd, 1000),
        changes: item.changes.slice(0, 5).map((c) => ({ path: clip(c.path, 512), diff: clip(c.diff, 1000) })) };
    return { ...entry, item: { ...projected, truncated, fullBytes: Buffer.byteLength(JSON.stringify(item)) } };
}
/** In-memory, bounded revision ledger; contains hashes/IDs only, never transcript bodies. */
export class MobileSync {
    // 输入快照投影后按 cursor 比对修订；checkpoint 只在本进程内存中保存键和摘要。
    now;
    maxCheckpoints;
    checkpoints = new Map();
    constructor(now = Date.now, maxCheckpoints = 256) {
        this.now = now;
        this.maxCheckpoints = maxCheckpoints;
    }
    sync(threadId, source, cursor, older = false) {
        // 游标只在同一任务、30 分钟内且首条 key 仍能定位时延续，否则重置到最新窗口。
        // older=true 向前翻页；变化过大时重新以最近一页建立游标。
        const all = entries(source);
        const previous = cursor ? this.checkpoints.get(cursor) : undefined;
        const valid = previous?.threadId === threadId && this.now() - previous.at < 30 * 60_000;
        let start = valid && previous.keys.length ? all.findIndex((e) => e.key === previous.keys[0]) : -1;
        const reset = !valid || (previous.keys.length > 0 && start < 0);
        if (reset)
            start = all.length;
        else if (!previous.keys.length)
            start = 0;
        // Page backwards by both count and bytes: a single huge command cannot
        // turn opening a conversation into a multi-megabyte transfer.
        if (reset || older) {
            let bytes = 0;
            for (let n = 0; start > 0 && n < 24; n++) {
                const size = Buffer.byteLength(JSON.stringify(preview(all[start - 1])));
                if (n > 0 && bytes + size > 96 * 1024)
                    break;
                bytes += size;
                start--;
            }
        }
        const window = all.slice(start);
        const keys = window.map((e) => e.key);
        // Include turn status in the wire revision, even for empty-text completion.
        const revisions = window.map((e) => digest([e.revision, e.turnStatus]));
        const latest = array(source.thread.turns).at(-1);
        let latestUser;
        for (let i = all.length - 1; i >= 0; i--) {
            if (all[i].item.type === "userMessage") {
                latestUser = all[i];
                break;
            }
        }
        const meta = { session: source.session, observation: source.observation,
            latestUserMessage: latestUser ? preview(latestUser) : null,
            userMessageCount: all.filter(entry => entry.item.type === "userMessage").length,
            pendingQuestions: source.pendingQuestions, pendingApprovals: source.pendingApprovals,
            latestTurn: latest ? { id: latest.id, status: latest.status, hasPlan: array(latest.items).some((i) => i.type === "plan") } : null };
        const next = digest([threadId, keys, revisions, meta, start > 0]);
        const checkpoint = { threadId, at: this.now(), keys, revisions };
        this.checkpoints.delete(next);
        this.checkpoints.set(next, checkpoint);
        let total = [...this.checkpoints.values()].reduce((n, c) => n + c.keys.length, 0);
        while (this.checkpoints.size > this.maxCheckpoints || (total > 100_000 && this.checkpoints.size > 1)) {
            const first = this.checkpoints.keys().next().value;
            total -= this.checkpoints.get(first).keys.length;
            this.checkpoints.delete(first);
        }
        if (!reset && next === cursor)
            return { protocol: 1, cursor: next, unchanged: true };
        const known = new Map(reset ? [] : previous.keys.map((k, i) => [k, previous.revisions[i]]));
        const changes = window.filter((e, i) => known.get(e.key) !== revisions[i]).map(preview);
        // A long absence can create hundreds of unseen messages. Rebase to the
        // recent page rather than dumping that backlog onto a cellular connection.
        if (!reset && Buffer.byteLength(JSON.stringify(changes)) > 256 * 1024)
            return this.sync(threadId, source);
        return { protocol: 1, cursor: next, reset, hasOlder: start > 0, order: keys, ...meta,
            items: changes };
    }
    item(source, key) {
        // 根据稳定 key 返回一条完整可见内容；找不到时交由 HTTP 层返回 404。
        const entry = entries(source).find((e) => e.key === key);
        return entry && { ...entry, item: { ...entry.item, truncated: false } };
    }
    tasks(source, before) {
        // 只分页用户消息；before 失效返回 stale，页大小同时受 24 条和约 96 KiB 限制。
        const all = entries(source).filter(entry => entry.item.type === "userMessage");
        let end = before ? all.findIndex(entry => entry.key === before) : all.length;
        if (end < 0)
            return { stale: true, items: [], nextBefore: null, total: all.length };
        const result = [];
        let bytes = 0;
        while (end > 0 && result.length < 24) {
            const entry = preview(all[end - 1]);
            const size = Buffer.byteLength(JSON.stringify(entry));
            if (result.length && bytes + size > 96 * 1024)
                break;
            result.push(entry);
            bytes += size;
            end--;
        }
        return { items: result, nextBefore: end > 0 ? result.at(-1)?.key : null, total: all.length };
    }
}
/** Only event identifiers/status cross the network, never notification transcript text. */
export function mobileSignals(source) {
    // 仅返回最近回合状态、计划标记和待处理项 ID，不附带通知正文。
    const latest = array(source.thread.turns).at(-1);
    return { turnId: latest?.id ?? null, completed: latest?.status === "completed",
        plan: source.session.mode === "plan" && array(latest?.items).some((i) => i.type === "plan"),
        questions: array(source.pendingQuestions).map((q) => q.id), approvals: array(source.pendingApprovals).map((a) => a.id) };
}
