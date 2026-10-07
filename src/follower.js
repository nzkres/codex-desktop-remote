import { realpathSync } from 'node:fs';
import { resolve } from 'node:path';
import { DesktopIpc, DESKTOP_VERSIONS, ControlError, object } from './vendor/desktop-ipc.js';
import { nativeTurns } from './vendor/desktop-state.js';

// Follower only: no app-server, ownership handlers, resume or task creation.
export class Follower {
  constructor(config, ipc = new DesktopIpc(undefined, 12000, { hostScopedFollowerRequests: true })) {
    this.config = config;
    this.ipc = ipc;
    this.owner = null;
    this.revision = -1;
    this.serial = 0;
    this.refresh = null;
    this.state = null;
    this.ipc.on('disconnected', () => { this.state = null; });
    this.ipc.on('broadcast', m => this.receive(m));
  }
  authorized(state) {
    if (state?.id !== this.config.threadId || typeof state.cwd !== 'string') return false;
    try { return realpathSync(state.cwd).toLowerCase() === realpathSync(this.config.projectPath).toLowerCase()
      && resolve(state.cwd).toLowerCase() === resolve(this.config.projectPath).toLowerCase(); }
    catch { return false; }
  }
  receive(m) {
    const p = m.params;
    if (!object(p)) return;
    if (m.method === 'client-status-changed' && p.clientId === this.owner && p.status === 'disconnected') {
      this.state = null;
      this.ipc.emit('owner-lost');
      return;
    }
    if (m.method !== 'thread-stream-state-changed' || m.version !== DESKTOP_VERSIONS[m.method]
      || p.hostId !== 'local' || p.conversationId !== this.config.threadId || m.sourceClientId !== this.owner) return;
    const change = p.change;
    if (!object(change) || change.type !== 'snapshot' || !Number.isSafeInteger(change.revision)
      || change.revision < this.revision || !this.authorized(change.conversationState)) return;
    try { nativeTurns(change.conversationState); } catch { return; }
    this.revision = change.revision;
    this.state = change.conversationState;
    this.serial++;
    this.ipc.emit('fresh-snapshot');
  }
  snapshot() {
    if (this.refresh) return this.refresh;
    this.refresh = this.readFresh().finally(() => { this.refresh = null; });
    return this.refresh;
  }
  async readFresh() {
    try { await this.ipc.ready(); }
    catch { throw new ControlError('desktop_unavailable', '请保持 Codex Desktop 打开，消息未发送。', 503); }
    let r;
    try { r = await this.ipc.request('thread-owner-discovery', { hostId: 'local', conversationId: this.config.threadId }); }
    catch { throw new ControlError('owner_unavailable', '请在 Desktop 打开测试任务；不会另开执行器。', 503); }
    if (typeof r.handledByClientId !== 'string' || r.result?.supportsUntrustedAppInput !== true)
      throw new ControlError('owner_unverified', '无法核对执行端，操作已阻止。', 503);
    if (this.owner && this.owner !== r.handledByClientId)
      throw new ControlError('owner_changed', '任务执行端已改变，请在电脑端重新核对连接。', 409);
    this.owner = r.handledByClientId;
    const serial = this.serial;
    return new Promise((accept, reject) => {
      const finish = (err) => {
        clearTimeout(timer);
        this.ipc.off('fresh-snapshot', fresh);
        this.ipc.off('disconnected', lost);
        this.ipc.off('owner-lost', lost);
        err ? reject(err) : accept(this.state);
      };
      const fresh = () => { if (this.serial > serial) finish(); };
      const lost = () => finish(new ControlError('desktop_unavailable', 'Desktop 连接已断开。', 503));
      const timer = setTimeout(() => finish(new ControlError('snapshot_unavailable', '未读到实时任务状态，请刷新。', 503)), 8000);
      this.ipc.on('fresh-snapshot', fresh);
      this.ipc.on('disconnected', lost);
      this.ipc.on('owner-lost', lost);
      try { this.ipc.broadcast('thread-stream-following-changed', {
        hostId: 'local', conversationId: this.config.threadId, following: true,
      }, [this.owner]); } catch { lost(); }
    });
  }
  async forward(method, params) {
    if (!this.state || !this.authorized(this.state))
      throw new ControlError('owner_unavailable', '执行端状态已失效，请刷新。', 503);
    if (!['thread-follower-start-turn', 'thread-follower-interrupt-turn', 'thread-follower-load-complete-history'].includes(method))
      throw new ControlError('unsupported_control', '本轮请在 Desktop 完成这项操作。', 501);
    return (await this.ipc.request(method, { ...params, conversationId: this.config.threadId }, this.owner)).result;
  }
  async completeSnapshot() {
    let state = await this.snapshot();
    if (state.turnHistory?.history?.isComplete === false) {
      await this.forward('thread-follower-load-complete-history', {});
      state = await this.snapshot();
      if (state.turnHistory?.history?.isComplete === false)
        throw new ControlError('incomplete_history', 'Desktop 历史尚未加载完整，请稍后刷新。', 503);
    }
    return state;
  }
  close() { this.ipc.close(); }
}
