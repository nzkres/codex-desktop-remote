import { connect } from "node:net";
import { EventEmitter } from "node:events";
import { randomUUID } from "node:crypto";
// Private Desktop protocol, deliberately version-pinned and local-only.
export const DESKTOP_VERSIONS = {
    initialize: 0, "thread-owner-discovery": 1,
    "thread-follower-start-turn": 2, "thread-follower-interrupt-turn": 4,
    "thread-follower-load-complete-history": 1,
    "thread-follower-set-queued-follow-ups-state": 1, "thread-queued-followups-changed": 2,
    "thread-follower-command-approval-decision": 1, "thread-follower-file-approval-decision": 1,
    "thread-follower-permissions-request-approval-response": 1, "thread-follower-submit-user-input": 1,
    "thread-stream-following-changed": 1, "thread-stream-following-status-requested": 1,
    "thread-stream-state-changed": 11,
};
export const object = (value) => !!value && typeof value === "object" && !Array.isArray(value);
export class ControlError extends Error {
    code;
    statusCode;
    constructor(code, message, statusCode = 409) {
        super(message);
        this.code = code;
        this.statusCode = statusCode;
    }
}
export class IpcError extends Error {
    remote;
    constructor(message, remote = false) {
        super(message);
        this.remote = remote;
    }
}
const MAX_FRAME = 32 * 1024 * 1024;
/** The envelope's host is authoritative; never route another host to this PC. */
export function desktopRequestVersion(request, options = {}) {
    if (typeof request.method !== "string" || !Object.hasOwn(DESKTOP_VERSIONS, request.method))
        return undefined;
    if (request.hostId != null && request.hostId !== "local")
        return undefined;
    if (object(request.params) && request.params.hostId != null && request.params.hostId !== "local")
        return undefined;
    const version = DESKTOP_VERSIONS[request.method];
    // Desktop 26.928 uses base + 1 only for follower requests carrying hostId.
    // Its legacy requests, owner discovery and broadcasts retain their old versions.
    if (request.hostId != null && request.method.startsWith("thread-follower-")) {
        return options.hostScopedFollowerRequests ? version + 1 : undefined;
    }
    return version;
}
/** Structural diagnostics only: never log prompt text, attachments or field values. */
export function describeRejectedDiscovery(request, options = {}) {
    const shape = (value) => Array.isArray(value) ? { type: "array", count: value.length }
        : value == null ? { type: "null" } : typeof value === "string" ? { type: "string", length: value.length }
            : object(value) ? { type: "object", count: Object.keys(value).length } : { type: typeof value };
    const fields = (value) => object(value) ? Object.fromEntries(Object.entries(value).slice(0, 50)
        .filter(([key]) => /^[A-Za-z][A-Za-z0-9_]{0,63}$/.test(key)).map(([key, value]) => [key, shape(value)])) : shape(value);
    const p = object(request.params) ? request.params : {};
    const queue = object(p.state) && typeof p.conversationId === "string" ? p.state[p.conversationId] : undefined;
    return { phase: "discovery-rejected", method: typeof request.method === "string" && /^[a-z-]{1,100}$/.test(request.method) ? request.method : "invalid",
        version: Number.isInteger(request.version) ? request.version : null,
        expectedVersion: desktopRequestVersion(request, options) ?? null,
        envelopeHost: request.hostId == null ? "legacy" : request.hostId === "local" ? "local" : "rejected",
        conversationId: typeof p.conversationId === "string" && /^[a-f0-9-]{36}$/.test(p.conversationId) ? p.conversationId : "redacted",
        parameterFields: fields(p), queueCount: Array.isArray(queue) ? queue.length : null,
        messages: Array.isArray(queue) ? queue.slice(0, 3).map((m) => object(m) ? {
            fields: fields(m), contextFields: fields(m.context), optionFields: fields(m.submissionOptions), submissionFields: fields(m.submission),
            promptMatchesText: object(m.context) && typeof m.text === "string" && m.context.prompt === m.text,
        } : shape(m)) : undefined };
}
export class DesktopIpc extends EventEmitter {
    endpoint;
    timeoutMs;
    options;
    handlers = new Map();
    socket;
    connecting;
    buffer = Buffer.alloc(0);
    pending = new Map();
    clientId = "initializing-client";
    epoch = 0;
    closed = false;
    constructor(endpoint = "\\\\.\\pipe\\codex-ipc", timeoutMs = 15_000, options = {}) {
        super();
        this.endpoint = endpoint;
        this.timeoutMs = timeoutMs;
        this.options = options;
    }
    async ready() {
        if (this.closed)
            throw new IpcError("Desktop bridge is closed");
        if (this.connecting)
            return this.connecting;
        if (this.socket && !this.socket.destroyed && this.clientId !== "initializing-client")
            return;
        this.connecting = this.open();
        try {
            await this.connecting;
        }
        finally {
            this.connecting = undefined;
        }
    }
    async open() {
        this.clientId = "initializing-client";
        this.buffer = Buffer.alloc(0);
        const socket = connect(this.endpoint);
        this.socket = socket;
        socket.on("data", (chunk) => {
            if (this.socket !== socket)
                return;
            try {
                this.buffer = Buffer.concat([this.buffer, chunk]);
                while (this.buffer.length >= 4) {
                    const size = this.buffer.readUInt32LE(0);
                    if (!size || size > MAX_FRAME)
                        throw new IpcError("Invalid Desktop frame");
                    if (this.buffer.length < size + 4)
                        break;
                    const message = JSON.parse(this.buffer.subarray(4, size + 4).toString("utf8"));
                    this.buffer = this.buffer.subarray(size + 4);
                    if (!object(message))
                        throw new IpcError("Invalid Desktop message");
                    void this.receive(message, socket).catch(() => this.disconnect(socket));
                }
            }
            catch {
                this.disconnect(socket);
            }
        });
        socket.on("error", () => this.disconnect(socket));
        socket.on("close", () => this.disconnect(socket));
        try {
            await new Promise((resolve, reject) => {
                const timer = setTimeout(() => { socket.destroy(); reject(new IpcError("Desktop connection timed out")); }, this.timeoutMs);
                const finish = (error) => { clearTimeout(timer); socket.off("connect", success); socket.off("error", fail); error ? reject(error) : resolve(); };
                const success = () => finish();
                const fail = () => finish(new IpcError("Desktop is unavailable"));
                socket.once("connect", success);
                socket.once("error", fail);
            });
            const reply = await this.request("initialize", { clientType: "personal-codex-remote-bridge" });
            if (typeof reply.result?.clientId !== "string")
                throw new IpcError("Unsupported Desktop handshake");
            this.clientId = reply.result.clientId;
            this.epoch++;
            this.emit("connected");
        }
        catch (error) {
            this.disconnect(socket);
            throw error;
        }
    }
    disconnect(socket) {
        if (this.socket !== socket)
            return;
        this.socket = undefined;
        socket.destroy();
        this.buffer = Buffer.alloc(0);
        this.clientId = "initializing-client";
        for (const p of this.pending.values()) {
            clearTimeout(p.timer);
            p.reject(new IpcError("Desktop disconnected; operation outcome may be unknown"));
        }
        this.pending.clear();
        this.emit("disconnected");
    }
    request(method, params, target) {
        if (!Object.hasOwn(DESKTOP_VERSIONS, method))
            return Promise.reject(new IpcError("Unsupported Desktop method"));
        const requestId = randomUUID();
        return new Promise((resolve, reject) => {
            const timer = setTimeout(() => { this.pending.delete(requestId); reject(new IpcError("Desktop response timed out; do not replay")); }, this.timeoutMs);
            this.pending.set(requestId, { method, target, resolve, reject, timer });
            try {
                this.write({ type: "request", requestId, method, params, version: DESKTOP_VERSIONS[method], sourceClientId: this.clientId, targetClientId: target, timeoutMs: this.timeoutMs });
            }
            catch (error) {
                clearTimeout(timer);
                this.pending.delete(requestId);
                reject(error);
            }
        });
    }
    broadcast(method, params, targets) {
        if (!Object.hasOwn(DESKTOP_VERSIONS, method))
            throw new IpcError("Unsupported Desktop broadcast");
        this.write({ type: "broadcast", method, version: DESKTOP_VERSIONS[method], params, sourceClientId: this.clientId, targetClientIds: targets });
    }
    write(message) {
        if (!this.socket?.writable || this.socket.destroyed)
            throw new IpcError("Desktop is unavailable");
        const body = Buffer.from(JSON.stringify(message));
        if (body.length > MAX_FRAME || this.socket.writableLength > MAX_FRAME)
            throw new IpcError("Desktop bridge capacity exceeded");
        const header = Buffer.alloc(4);
        header.writeUInt32LE(body.length);
        this.socket.write(Buffer.concat([header, body]));
    }
    async receive(message, socket) {
        if (this.socket !== socket || socket.destroyed)
            return;
        if (message.type === "response") {
            const p = this.pending.get(message.requestId);
            if (!p)
                return;
            this.pending.delete(message.requestId);
            clearTimeout(p.timer);
            if (message.resultType !== "success") {
                p.reject(new IpcError(typeof message.error === "string" ? message.error : "Desktop rejected request", true));
                return;
            }
            if (message.method !== p.method || (p.target && message.handledByClientId !== p.target)) {
                p.reject(new IpcError("Desktop response identity mismatch"));
                return;
            }
            p.resolve(message);
        }
        else if (message.type === "broadcast") {
            if (message.hostId != null && message.hostId !== "local")
                return;
            if (message.targetClientIds && (!Array.isArray(message.targetClientIds) || !message.targetClientIds.includes(this.clientId)))
                return;
            this.emit("broadcast", message);
        }
        else if (message.type === "client-discovery-request") {
            const r = message.request;
            const h = object(r) ? this.handlers.get(r.method) : undefined;
            const expected = object(r) ? desktopRequestVersion(r, this.options) : undefined;
            const canHandle = !!h && expected !== undefined && r.version === expected && object(r.params) && h.owns(r.params);
            // Limit diagnostics to conversations actually owned by this Gateway.
            // Missing ownership of unrelated threads is normal discovery traffic.
            if (!canHandle && object(r) && object(r.params) && this.handlers.get("thread-owner-discovery")?.owns(r.params)) {
                this.emit("diagnostic", describeRejectedDiscovery(r, this.options));
            }
            this.write({ type: "client-discovery-response", requestId: message.requestId, response: { canHandle } });
        }
        else if (message.type === "request") {
            try {
                const h = this.handlers.get(message.method);
                const expected = desktopRequestVersion(message, this.options);
                if (!h || expected === undefined || message.version !== expected || !object(message.params) || !h.owns(message.params))
                    throw new Error("Unsupported method or thread owner");
                const result = await h.run(message.params);
                // The operation may have completed, but its reply belongs only to the
                // connection that submitted it. Never relabel an old acknowledgement
                // with a new client ID after reconnect, and never replay the operation.
                if (this.socket !== socket || socket.destroyed)
                    return;
                this.write({ type: "response", requestId: message.requestId, method: message.method, resultType: "success", handledByClientId: this.clientId, result });
            }
            catch (error) {
                if (this.socket !== socket || socket.destroyed)
                    return;
                this.write({ type: "response", requestId: message.requestId, method: message.method, resultType: "error", error: error instanceof ControlError ? error.message : "Gateway rejected operation; check thread state" });
            }
        }
    }
    close() { this.closed = true; if (this.socket)
        this.disconnect(this.socket); }
}
