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
import { taskScope } from './catalog.js';
import {readUsage} from './usage.js';
import {nativeQuestions,publicQuestion,validateAnswer,answerProof,answerEvidence,questionProof,QUESTION_NOTICE} from './questions.js';

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
// 两个明确批准的完整权限快照，而不是自动接受任意新权限。
// permissionsHash 保留首次授权；备用摘要只能由用户明确审核后写入同一任务配置。
// Desktop 重启/桌面发言可能在两种已批准设置间切换；未列出的第三种设置仍拒绝。
export function permissionDigests(config) {
  const valid = value => typeof value === 'string' && /^[a-f0-9]{64}$/.test(value);
  if (!valid(config.permissionsHash))
    throw new ControlError('permissions_unverified', '原任务权限记录无法核对。');
  if (!Object.hasOwn(config, 'approvedAlternatePermissionsHash')) return [config.permissionsHash];
  const alternate = config.approvedAlternatePermissionsHash;
  if (!valid(alternate) || alternate === config.permissionsHash)
    throw new ControlError('permissions_unverified', '已批准的备用权限记录无法核对。');
  return [config.permissionsHash, alternate];
}
export const configuredPermissionsMatch = (state, config) => {
  // 用户一次授权本机全部对话后，以每条任务的实时 Desktop 设置为准。
  // 仍要求完整、可核对的权限；发送只使用 inheritThreadSettings，不构造新权限。
  if (taskScope(config) === 'all-local') {
    const p = state?.currentPermissions;
    if (!object(p) || !object(p.sandboxPolicy) || typeof p.sandboxPolicy.type !== 'string'
        || !(typeof p.approvalPolicy === 'string' || object(p.approvalPolicy)))
      throw new ControlError('permissions_unverified', '未取得这条对话的 Desktop 当前权限，请在电脑端查看。');
    permissionDigest(state); return true;
  }
  return permissionDigests(config).some(expected => permissionsMatch(state, expected));
};

export const READ_EVIDENCE = Symbol('native-read-evidence');

export class Service {
  // 汇集任务范围、权限、回执和更新/断开栅栏；mutating 仅串行化本进程操作。
  constructor(config, follower, receipts, { drainTimeoutMs = 5000, quarantine = false, beforeStop = null, afterStop = null, questionProtocol = false } = {}) {
    if (!Number.isSafeInteger(drainTimeoutMs) || drainTimeoutMs < 1 || drainTimeoutMs > 30000)
      throw new Error('invalid_drain_timeout');
    if (taskScope(config) !== 'all-local') permissionDigests(config);
    this.beforeStop = beforeStop; this.afterStop = afterStop;
    this.config = config; this.follower = follower; this.receipts = receipts;
    this.questionProtocol = questionProtocol;
    this.sync = new MobileSync(); this.mutating = false;
    this.draining = false; this.stopUncertain = false; this.drainWaiters = new Set();
    this.drainTimeoutMs = drainTimeoutMs; this.quarantine = quarantine;
  }
  projects() { return [{id:'desktop-fixture', name:'Desktop 联调', path:this.config.projectPath}]; }
  async sessions() { return [(await this.read(this.config.threadId)).session]; }
  target(id) { if (id !== this.config.threadId) throw new ControlError('outside_scope', '未开放这个任务。', 403); }
  permissions(state) {
    // 每次依赖快照的操作都要与启动配置里的权限摘要核对。
    if (!configuredPermissionsMatch(state, this.config))
      throw new ControlError('permissions_changed', 'Desktop 任务权限已变化，请在电脑端重新核对。');
  }
  async read(id, observe = null) {
    // 输入目标任务 ID；输出供手机接口使用的 session、turns、运行观察值。
    // 未知运行状态、没有回合仍拒绝；待处理请求显示为独立卡片，不遮断历史。
    this.target(id);
    const evidenceFor = state => {
      let idleVerified = false;
      try {
        assertIdle(state);
        idleVerified = Array.isArray(state.requests) && (!Object.hasOwn(state,'unconfirmedTurnSubmissions') ||
          (Array.isArray(state.unconfirmedTurnSubmissions) && state.unconfirmedTurnSubmissions.length === 0));
      } catch { /* 读取运行中消息仍允许，但不能解除任务的升级门禁。 */ }
      return {state, idleVerified};
    };
    const state = await this.follower.completeSnapshot(snapshot => {
      this.permissions(snapshot);
      // 第一个可信快照即落交接证据：后续补全历史超时也不能遗忘已观察到的活动。
      observe?.(evidenceFor(snapshot));
    });
    this.permissions(state);
    const {idleVerified} = evidenceFor(state);
    observe?.({state, idleVerified});
    const turns = nativeTurns(state), latest = turns.at(-1), active = activeTurn(state);
    if (!['idle','active'].includes(state.threadRuntimeStatus?.type))
      throw new ControlError('runtime_status_unknown','Desktop 尚未确认任务状态，请在电脑端查看。',503);
    const interaction = this.questions(state);
    if (!latest) {
      // 必须已取得完整、明确空闲的原生快照，才能说“没有记录”；超时不能当空历史。
      if (state.threadRuntimeStatus.type !== 'idle' || !Array.isArray(state.requests)
          || (Object.hasOwn(state,'unconfirmedTurnSubmissions') &&
              (!Array.isArray(state.unconfirmedTurnSubmissions) || state.unconfirmedTurnSubmissions.length)))
        throw new ControlError('history_unavailable','对话记录尚未就绪，请稍后重试获取。',503);
      throw new ControlError('history_empty','这条对话暂无历史记录。请在 Desktop 确认原对话；有记录后点击“重试”获取。',409);
    }
    const status = active ? 'RUNNING'
      : latest?.status === 'failed' ? 'FAILED' : latest?.status === 'interrupted' ? 'INTERRUPTED' : 'COMPLETED';
    return {
      [READ_EVIDENCE]: {state, idleVerified},
      session: { id, project_id: this.config.projectId ?? 'desktop-fixture', title: this.config.title, status, active_turn_id: active,
        mode: state.latestCollaborationMode?.mode ?? 'default', updated_at: latest?.turnStartedAtMs ?? latest?.startedAtMs ?? 0 },
      thread: { id, cwd: this.config.projectPath, turns },
      observation: { state: active ? 'running' : 'idle', source: 'desktop-ipc',
        lastActivityAt: latest?.turnStartedAtMs ?? 0, lastItemType: latest?.items?.at(-1)?.type ?? '' },
      pendingQuestions: interaction.groups, pendingApprovals: [],
      controlNotice: interaction.requiresDesktop ? QUESTION_NOTICE : null,
      usage: await readUsage(this.config),
      capabilities: {questions:this.questionProtocol,asyncQuestions:false,approvals:false,mode:false,implementPlan:false,create:false},
    };
  }
  questions(state) {
    const projected=nativeQuestions(state,{enabled:this.questionProtocol});
    const groups=projected.groups.map(publicQuestion);
    const records=this.receipts.questionRecords(this.config.threadId);
    for(const record of records) {
      const q=record.question;
      let outcome=record.state==='accepted'?record.outcome:null;
      if(!outcome && !this.quarantine) {
        outcome=answerEvidence(state,record);
        if(outcome) this.receipts.accept(record.key,record.digest,q.turnId,q,outcome);
      }
      let card=groups.find(g=>g.id===q.id);
      if(card && card.revision!==q.revision) {
        if(card.status==='closed' && questionProof(card.questions)===q.questionProof) card.revision=q.revision;
        else {
          Object.assign(card,{status:'desktop_required',notice:'题目内容已变化，旧回答不能用于新题。请在电脑核对。'});
          continue;
        }
      }
      if(!card && (!outcome || q.turnId===nativeTurns(state).at(-1)?.id)) {
        card={id:q.id,revision:q.revision,turnId:q.turnId,kind:'user-input',questions:[]};groups.push(card);
      }
      if(card) Object.assign(card,{status:outcome??'unknown',notice:outcome==='accepted'
        ?'Desktop 已记录这份答案。':outcome==='closed'?'已在电脑或其他设备处理，未重复提交。'
        :'提交结果待核对。答案不会重发，请刷新；仍无法确认时在电脑查看。'});
    }
    return {...projected,groups};
  }
  async answer(id,body) {
    this.target(id);
    if(!this.questionProtocol) throw new ControlError('question_unsupported','当前 Desktop 版本尚未验证远程答题，请在电脑处理。',501);
    if(!object(body) || !/^[a-f0-9]{64}$/.test(body.questionId??'') || !/^[a-f0-9]{64}$/.test(body.revision??'')
      || Object.keys(body).some(k=>!['questionId','revision','answers'].includes(k)) || !object(body.answers)
      || !Object.keys(body.answers).length || Object.keys(body.answers).length>3
      || Object.values(body.answers).some(v=>typeof v!=='string'||!v.trim()||v.length>8000))
      throw new ControlError('invalid_answer','问题编号无效。',400);
    return this.mutate(async()=>{
      const state=await this.follower.snapshot();this.permissions(state);
      if(!['active','idle'].includes(state.threadRuntimeStatus?.type) || !Array.isArray(state.requests)
        || (state.unconfirmedTurnSubmissions!=null && (!Array.isArray(state.unconfirmedTurnSubmissions)||state.unconfirmedTurnSubmissions.length)))
        throw new ControlError('runtime_status_unknown','执行状态尚未确认，未提交答案。',503);
      // 读取及提交均先用原生已完成答题证据核对旧回执，绝不自动重放。
      const view=this.questions(state);
      const old=this.receipts.questionRecords(id).find(r=>r.question.id===body.questionId);
      if(old) {
        const current=view.groups.find(q=>q.id===body.questionId);
        const proof=answerProof(Object.fromEntries(Object.entries(body.answers).map(([id,v])=>[id,[v]])));
        if((current && current.revision!==old.question.revision)
          || body.revision!==old.question.revision || proof!==old.question.answerProof)
          throw new ControlError('question_changed','这份回答与原提交不同，请刷新核对；未重新发送。');
        return {status:old.state==='accepted'?old.outcome:'unknown',notice:view.groups.find(q=>q.id===body.questionId)?.notice??'请刷新核对。'};
      }
      const q=nativeQuestions(state,{enabled:true}).groups.find(q=>q.id===body.questionId);
      if(!q || q.status==='closed') return {status:'closed',notice:'问题已处理或失效，请刷新；未发送答案。'};
      if(q.status!=='pending' || q.kind!=='user-input')
        throw new ControlError('question_unsupported',q.notice || QUESTION_NOTICE,409);
      const {questionId,...answerBody}=body;
      const response=validateAnswer(q,answerBody);
      this.receipts.assertCertain();
      const key=this.receipts.key(id,'question:'+q.id), digest=this.receipts.digest(JSON.stringify([q.revision,response]));
      const question={threadId:id,id:q.id,revision:q.revision,turnId:q.turnId,nativeId:q.nativeId,
        questionProof:questionProof(q.questions),
        answerProof:answerProof(Object.fromEntries(Object.entries(response.answers).map(([id,v])=>[id,v.answers])))};
      this.receipts.begin(key,digest,question);
      try {
        await this.follower.forward('thread-follower-submit-user-input',{requestId:q.nativeId,response},state);
        // IPC成功仅说明owner处理了请求。必须另读新快照并匹配completed答案记录。
        const after=await this.follower.snapshot();this.permissions(after);
        const card=this.questions(after).groups.find(item=>item.id===q.id);
        return {status:['accepted','closed'].includes(card?.status)?card.status:'unknown',notice:card?.notice??'提交结果待核对，请刷新。'};
      } catch {
        return {status:'unknown',notice:'提交结果待核对。请刷新核对，不会重复发送。'};
      }
    });
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
      const pendingStop = this.beforeStop?.(this.config, body.expectedTurnId);
      try {
        await this.follower.forward('thread-follower-interrupt-turn', { mode: 'user-stop', expectedTurnId: body.expectedTurnId }, state);
        this.afterStop?.(this.config, pendingStop);
      }
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
        return reply(200, {projects:await service.projects()});
      if (req.method === 'GET' && url.pathname === '/api/sessions')
        return reply(200, {sessions:await service.sessions()});
      if (req.method === 'GET' && url.pathname === '/api/activity') return reply(200, {activity:[]});
      const questionRoute=/^\/api\/sessions\/([A-Za-z0-9-]+)\/questions\/([a-f0-9]{64})\/answer$/.exec(url.pathname);
      if(questionRoute && req.method==='POST') {
        const body=await bodyOf(req);
        if(!object(body) || Object.hasOwn(body,'questionId')) throw new ControlError('invalid_answer','回答格式无效。',400);
        return reply(200,await service.answer(questionRoute[1],{...body,questionId:questionRoute[2]}));
      }
      const match = /^\/api\/sessions\/([A-Za-z0-9-]+)\/(mobile-sync|mobile-items|mobile-tasks|signals|messages|stop)(?:\/([a-f0-9]{32}))?$/.exec(url.pathname);
      if (!match) throw new ControlError('unsupported_route', '本轮仅支持已授权 Desktop 任务的文字交互；其他操作请在 Desktop 完成。', 501);
      const [, id, route, itemKey] = match;
      if (req.method === 'POST' && route === 'messages') return reply(200, {result:await service.send(id, await bodyOf(req))});
      if (req.method === 'POST' && route === 'stop') return reply(200, await service.stop(id, await bodyOf(req)));
      if (req.method !== 'GET' || !['mobile-sync','mobile-items','mobile-tasks','signals'].includes(route))
        throw new ControlError('unsupported_route', '不支持这项操作。', 405);
      // Only the mobile screen's explicit open/retry carries this one-shot intent.
      // Ordinary sync polling, history pages, OTA and details remain observation-only.
      const openValues=url.searchParams.getAll('open');
      if(openValues.length && (route!=='mobile-sync'||openValues.length!==1||openValues[0]!=='1'||url.searchParams.get('older')==='1'))
        throw new ControlError('invalid_open_request','打开对话请求无效，请重新进入。',400);
      const source = typeof service.readForMobile==='function'
        ? await service.readForMobile(id, {allowDesktopOpen:openValues.length===1}) : await service.read(id);
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
