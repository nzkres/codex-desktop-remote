// 手机任务接口的业务层：限制原任务、检查权限、读取历史、记录发送回执。
// 它调用 Follower 向 Desktop 发操作；模型是否完成要看后续任务状态，而非一次 HTTP 成功。

import { createHash, timingSafeEqual } from 'node:crypto';
import { createServer } from 'node:http';
import { realpathSync } from 'node:fs';
import { isAbsolute, sep } from 'node:path';
import { ControlError, object } from './vendor/desktop-ipc.js';
import { nativeTurns, activeTurn, assertIdle } from './vendor/desktop-state.js';
import { MobileSync, mobileSignals } from './vendor/mobile-sync.js';
import { VERSION } from './version.js';

// 把 Desktop 当前权限规范化后计算摘要；输入必须包含非空 currentPermissions。
// 根目录取真实路径、去重排序，并移除已被授权父目录覆盖的子目录。
export const permissionDigest = state => {
  if (!object(state.currentPermissions) || !Object.keys(state.currentPermissions).length)
    throw new ControlError('permissions_unverified', '未能核对线程权限，消息未发送。');
  const canonical = (value, key = '') => {
    if (Array.isArray(value) && ['writableRoots', 'runtimeWorkspaceRoots'].includes(key)) {
      const roots = [...new Set(value.map(p => {
        if (typeof p !== 'string' || !isAbsolute(p)) throw new ControlError('permissions_unverified','权限目录无法核对。');
        return realpathSync(p).toLowerCase();
      }))].sort();
      // Desktop can add cwd when it is already covered by an authorized parent.
      // Resolve real paths so a junction to an external directory is not covered.
      return roots.filter(p => !roots.some(parent => p !== parent && p.startsWith(parent.endsWith(sep) ? parent : parent + sep)));
    }
    if (Array.isArray(value)) return value.map(v => canonical(v));
    if (object(value)) return Object.fromEntries(Object.keys(value).sort().map(k => [k,canonical(value[k],k)]));
    return value;
  };
  return createHash('sha256').update(JSON.stringify(canonical(state.currentPermissions))).digest('hex');
};
export const permissionsMatch = (state, expectedDigest) => {
  // 先比当前摘要；仅允许 auto_review 与旧名 guardian_subagent 这一处序列化别名互换。
  if (permissionDigest(state) === expectedDigest) return true;
  // Codex names this reviewer auto_review; guardian_subagent is its legacy
  // serialization alias. Keep persisted hashes valid without rebasing any
  // other permission, and never change the observed snapshot or saved config.
  const current = state.currentPermissions;
  const alias = current.approvalsReviewer === 'guardian_subagent' ? 'auto_review'
    : current.approvalsReviewer === 'auto_review' ? 'guardian_subagent' : null;
  return alias !== null && permissionDigest({
    currentPermissions: { ...current, approvalsReviewer: alias },
  }) === expectedDigest;
};
export class Service {
  // 汇集任务范围、权限、回执和更新/断开栅栏；mutating 仅串行化本进程操作。
  constructor(config, follower, receipts, { drainTimeoutMs = 5000, quarantine = false } = {}) {
    if (!Number.isSafeInteger(drainTimeoutMs) || drainTimeoutMs < 1 || drainTimeoutMs > 30000)
      throw new Error('invalid_drain_timeout');
    this.config = config; this.follower = follower; this.receipts = receipts;
    this.sync = new MobileSync(); this.mutating = false;
    this.draining = false; this.stopUncertain = false; this.drainWaiters = new Set();
    this.drainTimeoutMs = drainTimeoutMs; this.quarantine = quarantine;
  }
  target(id) { if (id !== this.config.threadId) throw new ControlError('outside_scope', '未开放这个任务。', 403); }
  permissions(state) {
    // 每次依赖快照的操作都要与启动配置里的权限摘要核对。
    if (!permissionsMatch(state, this.config.permissionsHash))
      throw new ControlError('permissions_changed', 'Desktop 任务权限已变化，请在电脑端重新核对。');
  }
  async read(id) {
    // 输入目标任务 ID；输出供手机接口使用的 session、turns、运行观察值。
    // 未知运行状态、没有回合、待 Desktop 处理的请求都会拒绝读作正常结果。
    this.target(id);
    const state = await this.follower.completeSnapshot(snapshot => this.permissions(snapshot));
    this.permissions(state);
    const turns = nativeTurns(state), latest = turns.at(-1), active = activeTurn(state);
    const pending = Array.isArray(state.requests) ? state.requests : [];
    if (!['idle','active'].includes(state.threadRuntimeStatus?.type) || !latest)
      throw new ControlError('runtime_status_unknown','Desktop 尚未确认任务状态，请在电脑端查看。',503);
    if (pending.length) throw new ControlError('desktop_action_required','请在 Desktop 处理待审批或提问，然后刷新手机。',409);
    const status = active ? 'RUNNING'
      : latest?.status === 'failed' ? 'FAILED' : latest?.status === 'interrupted' ? 'INTERRUPTED' : 'COMPLETED';
    return {
      session: { id, project_id: 'desktop-fixture', title: this.config.title, status, active_turn_id: active,
        mode: state.latestCollaborationMode?.mode ?? 'default', updated_at: latest?.turnStartedAtMs ?? latest?.startedAtMs ?? 0 },
      thread: { id, cwd: this.config.projectPath, turns },
      observation: { state: active ? 'running' : 'idle', source: 'desktop-ipc',
        lastActivityAt: latest?.turnStartedAtMs ?? 0, lastItemType: latest?.items?.at(-1)?.type ?? '' },
      // Initial text acceptance deliberately leaves approvals on the Desktop.
      pendingQuestions: [], pendingApprovals: [],
      controlNotice: null,
    };
  }
  async mutate(action, readonly = false) {
    // 更新隔离期拒绝写操作；断开准备期拒绝进入本方法的操作；同时只允许一项受控操作。
    // finally 唤醒等待断开的调用，即使 action 抛错也会释放本地占用。
    if (this.quarantine && !readonly) throw new ControlError('update_quarantine', '更新验证中，未发送操作。', 503);
    if (this.draining) throw new ControlError('disconnect_preparing', '连接器正在断开，未发送操作。', 503);
    if (this.mutating) throw new ControlError('operation_busy', '另一项操作正在核对，请稍后刷新。');
    this.mutating = true;
    try { return await action(); } finally {
      this.mutating = false;
      for (const wake of this.drainWaiters) wake();
      this.drainWaiters.clear();
    }
  }
  async prepareDisconnect() {
    // 控制端请求断开时先立栅栏，再等正在执行的操作结束。
    // 超时、未知停止结果或任何未确认发送回执都不能宣称已安全断开。
    // Fence first, before awaiting any in-flight Desktop control.
    this.draining = true;
    if (this.mutating) {
      await new Promise((resolve, reject) => {
        const finish = () => { clearTimeout(timer); this.drainWaiters.delete(finish); resolve(); };
        const timer = setTimeout(() => {
          this.drainWaiters.delete(finish);
          reject(new ControlError('drain_timeout', '连接器仍有未完成操作，不能确认断开。', 503));
        }, this.drainTimeoutMs);
        this.drainWaiters.add(finish);
      });
    }
    if (this.stopUncertain) throw new ControlError('drain_uncertain', '停止操作结果未确认，不能确认断开。', 503);
    this.receipts.assertCertain();
    return { drained: true };
  }
  async updateStatus() {
    // 更新器读取的只是“当前可更新”证据：完整新快照、权限不变、空闲且无未知回执。
    // 此方法不执行下载安装，也不主动打断 Desktop 回合。
    // Reserve local mutation admission while reading a fresh native snapshot.
    // This is observation, not permission to interrupt a Desktop task.
    return this.mutate(async () => {
      if (this.stopUncertain) throw new ControlError('update_uncertain', '存在未确认操作，更新已延期。', 409);
      this.receipts.assertCertain();
      const state = await this.follower.completeSnapshot(snapshot => this.permissions(snapshot));
      this.permissions(state);
      assertIdle(state);
      this.receipts.assertCertain();
      return { idle: true, version: VERSION, permissionsVerified: true, receiptsCertain: true };
    }, true);
  }
  async send(id, body) {
    // 输入任务 ID 与 {text,clientMessageId}；成功返回 turnId，重复已确认请求返回 replayed。
    // 先验消息形状/任务范围，再在串行区内核对回执、权限和空闲状态。
    this.target(id);
    if (!object(body) || Object.keys(body).some(k => !['text','clientMessageId'].includes(k))
      || typeof body.text !== 'string' || !body.text.trim() || body.text.length > 16000
      || typeof body.clientMessageId !== 'string' || !/^[A-Za-z0-9_-]{1,100}$/.test(body.clientMessageId))
      throw new ControlError('invalid_message', '消息或发送编号无效。', 400);
    return this.mutate(async () => {
      const key = this.receipts.key(id, body.clientMessageId), digest = this.receipts.digest(body.text);
      const old = this.receipts.get(key, digest);
      if (old) return old;
      this.receipts.assertCertain();
      const state = await this.follower.snapshot();
      this.permissions(state); assertIdle(state);
      this.receipts.begin(key, digest);
      // pending 必须在 IPC 请求前落盘；回复缺失时保留未知状态，要求 Desktop 人工核对。
      try {
        const reply = await this.follower.forward('thread-follower-start-turn', { turnStart: {
          request: { threadId: id, input: [{type:'text',text:body.text,text_elements:[]}], clientUserMessageId: body.clientMessageId },
          context: { inheritThreadSettings: true },
        }}, state);
        const turnId = reply?.result?.turn?.id;
        if (typeof turnId !== 'string' || !turnId) throw new Error('invalid_ack');
        this.receipts.accept(key, digest, turnId);
        return { turnId };
      } catch { throw new ControlError('send_unknown', '发送结果未确认，请在 Desktop 查看；不会重复发送。'); }
    });
  }
  async stop(id, body) {
    // 输入 expectedTurnId；只在它等于当前活动回合时发送停止。
    // 请求出错后把 stopUncertain 留在内存，阻止断开和更新状态通过。
    this.target(id);
    return this.mutate(async () => {
      const state = await this.follower.snapshot();
      this.permissions(state);
      if (typeof body?.expectedTurnId !== 'string' || !body.expectedTurnId || body.expectedTurnId !== activeTurn(state))
        throw new ControlError('stale_turn', '回合已经变化，没有发送停止操作。');
      try { await this.follower.forward('thread-follower-interrupt-turn', { mode: 'user-stop', expectedTurnId: body.expectedTurnId }, state); }
      catch {
        this.stopUncertain = true;
        throw new ControlError('stop_unknown', '停止结果未确认，请在 Desktop 查看。');
      }
      return { ok: true };
    });
  }
}
async function bodyOf(req) {
  // 最多接收 64 KiB JSON 请求体；过大或解析失败以 ControlError 返回。
  let bytes = 0, chunks = [];
  for await (const chunk of req) {
    bytes += chunk.length;
    if (bytes > 65536) throw new ControlError('body_too_large', '请求过大。', 413);
    chunks.push(chunk);
  }
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); }
  catch { throw new ControlError('invalid_json', '请求格式无效。', 400); }
}
export function httpServer(service, token, instance, controlToken = null) {
  // token 保护普通接口，独立 controlToken 保护 prepare-disconnect。
  // 输入 Service 与启动凭据；输出尚未 listen 的 Node HTTP server。
  if (!/^[a-f0-9]{64}$/.test(token) || !/^[a-f0-9]{32}$/.test(instance)) throw new Error('invalid_bootstrap');
  if (controlToken !== null && (!/^[a-f0-9]{64}$/.test(controlToken) || controlToken === token))
    throw new Error('invalid_control_token');
  const expected = Buffer.from('Bearer '+token);
  const controlExpected = controlToken === null ? null : Buffer.from('Bearer '+controlToken);
  const server = createServer(async (req, res) => {
    // 所有响应都禁止缓存；鉴权在具体路由处理之前，以等长安全比较核对 Bearer 值。
    const reply = (code, value) => {
      const data = JSON.stringify(value);
      res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': Buffer.byteLength(data), 'Cache-Control': 'no-store' });
      res.end(data);
    };
    try {
      const url = new URL(req.url, 'http://127.0.0.1');
      const drain = req.method === 'POST' && url.pathname === '/api/connector/prepare-disconnect';
      if (drain && !controlExpected) return reply(404,{error:'Not found'});
      const authorization = drain ? controlExpected : expected;
      const supplied = Buffer.from(req.headers.authorization ?? '');
      if (supplied.length !== authorization.length || !timingSafeEqual(supplied, authorization)) return reply(401,{error:'Unauthorized'});
      if (drain)
        return reply(200, { instance, ...await service.prepareDisconnect() });
      if (req.method === 'GET' && url.pathname === '/api/connector/health')
        // 健康响应只证明本机 HTTP 存活，执行器和 Desktop 任务仍标注为未核实。
        return reply(200, {instance,healthy:true,executor:'unknown',desktopTask:'unverified',version:VERSION});
      if (req.method === 'GET' && url.pathname === '/api/connector/update-status')
        return reply(200, await service.updateStatus());
      if (req.method === 'GET' && url.pathname === '/api/projects')
        return reply(200, {projects:[{id:'desktop-fixture', name:'Desktop 联调', path:service.config.projectPath}]});
      if (req.method === 'GET' && url.pathname === '/api/sessions')
        return reply(200, {sessions:[(await service.read(service.config.threadId)).session]});
      if (req.method === 'GET' && url.pathname === '/api/activity') return reply(200, {activity:[]});
      const match = /^\/api\/sessions\/([A-Za-z0-9-]+)\/(mobile-sync|mobile-items|mobile-tasks|signals|messages|stop)(?:\/([a-f0-9]{32}))?$/.exec(url.pathname);
      if (!match) throw new ControlError('unsupported_route', '本轮仅支持已有 Desktop 测试任务的文字交互；其他操作请在 Desktop 完成。', 501);
      const [, id, route, itemKey] = match;
      service.target(id);
      if (req.method === 'POST' && route === 'messages') return reply(200, {result:await service.send(id, await bodyOf(req))});
      if (req.method === 'POST' && route === 'stop') return reply(200, await service.stop(id, await bodyOf(req)));
      if (req.method !== 'GET' || !['mobile-sync','mobile-items','mobile-tasks','signals'].includes(route))
        throw new ControlError('unsupported_route', '不支持这项操作。', 405);
      const source = await service.read(id);
      // 手机端投影、分页、详情和信号都基于同一次经过范围与权限检查的状态读取。
      if (route === 'mobile-sync') return reply(200, service.sync.sync(id, source, url.searchParams.get('cursor') ?? undefined, url.searchParams.get('older') === '1'));
      if (route === 'mobile-tasks') return reply(200, service.sync.tasks(source, url.searchParams.get('before') ?? undefined));
      if (route === 'signals') return reply(200, mobileSignals(source));
      const item = service.sync.item(source,itemKey);
      if (!item) throw new ControlError('item_missing','条目已变化，请刷新。',404);
      return reply(200,item);
    } catch (e) { reply(e instanceof ControlError ? e.statusCode : 503, {error:e instanceof ControlError ? e.message : '本机连接状态无法确认，请在电脑端诊断。',code:e instanceof ControlError ? e.code : 'local_unavailable'}); }
  });
  server.requestTimeout = 15000; server.headersTimeout = 10000; server.keepAliveTimeout = 3000;
  return server;
}
