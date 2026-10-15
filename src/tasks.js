// 两种共享模式：旧配置保留选定任务；用户授权 all-local 后按本机目录自动发现。
import { realpathSync } from 'node:fs';
import { isAbsolute, toNamespacedPath } from 'node:path';
import { Service, permissionDigests, READ_EVIDENCE } from './service.js';
import { Follower } from './follower.js';
import { ControlError, object } from './vendor/desktop-ipc.js';
import { VERSION } from './version.js';
import {readCatalog, taskScope, taskId, localProjectPath} from './catalog.js';
import {MobileSync} from './vendor/mobile-sync.js';
import {Activity} from './activity.js';
import {ensureDesktopConversation} from './desktop-open.js';

export function taskConfigs(config) {
  if (taskScope(config) === 'all-local') return [];
  const extra = Object.hasOwn(config, 'additionalTasks') ? config.additionalTasks : [];
  if (!Array.isArray(extra) || extra.length > 7)
    throw new ControlError('invalid_task_scope', '已授权任务清单无效。', 400);
  const fields = new Set(['threadId','projectPath','title','permissionsHash','approvedAlternatePermissionsHash']);
  const ids = new Set();
  return [config, ...extra].map((task, index) => {
    if (!object(task) || (index && Object.keys(task).some(key => !fields.has(key)))
      || typeof task.threadId !== 'string' || !/^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(task.threadId)
      || ids.has(task.threadId) || typeof task.title !== 'string' || !task.title.trim() || task.title.length > 100
      || typeof task.projectPath !== 'string' || !isAbsolute(task.projectPath))
      throw new ControlError('invalid_task_scope', '已授权任务清单无效。', 400);
    permissionDigests(task); // 备用任务不能继承主任务权限摘要。
    let projectPath;
    try { projectPath = realpathSync(task.projectPath); }
    catch { throw new ControlError('invalid_task_scope', '已授权任务目录无法核对。', 400); }
    ids.add(task.threadId);
    // 只共享机器运行配置与同一份按 threadId 分隔的回执库。
    const { additionalTasks, approvedAlternatePermissionsHash, ...base } = config;
    const result = {...base, ...task, projectPath,
      projectId: index === 0 ? 'desktop-fixture' : 'desktop-' + task.threadId};
    delete result.additionalTasks;
    return result;
  });
}

export class TaskService {
  constructor(config, receipts, {createFollower = cfg => new Follower(cfg), discover = readCatalog, prepareDesktop = ensureDesktopConversation, ...options} = {}) {
    this.config = config; this.services = new Map(); this.draining = false; this.checking = false; this.writes = 0;
    this.discover = discover; this.createFollower = createFollower; this.prepareDesktop = prepareDesktop; this.desktopOpening = null; this.receipts = receipts; this.options = options;
    this.automatic = taskScope(config) === 'all-local'; this.catalog = new Map(); this.closed = false; this.readers = new Map();
    this.activity = new Activity(receipts);
    if (this.automatic) this.options = {...options,
      beforeStop:(task,turn)=>this.activity.beginStop(task,turn),
      afterStop:(task,operation)=>this.activity.confirmStop(task,operation)};
    const configs = taskConfigs(config);
    try {
      for (const task of configs) {
        const follower = createFollower(task);
        try { this.services.set(task.threadId, new Service(task, follower, receipts, options)); }
        catch (error) { follower.close(); throw error; }
      }
    } catch (error) { this.close(); throw error; }
    this.sync = this.services.values().next().value?.sync ?? new MobileSync(); // MobileSync 自身按任务 ID 隔离游标。
  }
  async refreshCatalog() {
    if (!this.automatic) return;
    if (this.closed) throw new ControlError('disconnect_preparing', '连接器已关闭。', 503);
    // 同时到达的列表/读取共用一次目录查询，查询失败不回退到过期授权列表。
    if (this.refreshing) return this.refreshing;
    this.refreshing = (async () => {
      const rows = await this.discover(this.config), next = new Map();
      if (!Array.isArray(rows) || rows.length > 10000) throw new ControlError('catalog_unavailable', '对话目录无法核对。', 503);
      for (const row of rows) {
        if (!taskId(row.threadId) || next.has(row.threadId) || typeof row.projectPath !== 'string')
          throw new ControlError('catalog_unavailable', '对话目录无法核对。', 503);
        // 远端 Linux 等非本机目录不能通过 Windows 本地 follower 执行。
        if (!localProjectPath(row.projectPath)) continue;
        next.set(row.threadId, {...row, title:typeof row.title === 'string' && row.title.trim() ? row.title.slice(0,200) : '未命名对话',
          projectId:'desktop-'+row.threadId});
      }
      if (this.closed) throw new ControlError('disconnect_preparing', '连接器已关闭。', 503);
      this.catalog = next;
      this.prune();
    })().finally(() => { this.refreshing = null; });
    return this.refreshing;
  }
  prune() {
    if (!this.automatic || this.writes || this.checking) return;
    for (const [id, service] of this.services) {
      if ((this.catalog.has(id) && this.services.size <= 16) || this.readers.get(id)
          || service.mutating || service.stopUncertain) continue;
      service.follower.close(); this.services.delete(id);
    }
  }
  async automaticTarget(id) {
    await this.refreshCatalog();
    if (this.draining) throw new ControlError('disconnect_preparing', '连接器正在断开。', 503);
    const row = this.catalog.get(id);
    if (!row) throw new ControlError('outside_scope', '这个对话不在本机 Desktop 目录中，请刷新。', 403);
    const existing = this.services.get(id);
    if (existing) {
      if (existing.config.projectPath !== row.projectPath)
        throw new ControlError('task_directory_changed', 'Desktop 对话目录已变化，请重新连接。', 409);
      existing.config.title = row.title;
      this.services.delete(id); this.services.set(id, existing);
      return existing;
    }
    const {additionalTasks, permissionsHash, approvedAlternatePermissionsHash, ...base} = this.config;
    const cfg = {...base, ...row};
    const follower = this.createFollower(cfg);
    try {
      const service = new Service(cfg, follower, this.receipts, this.options);
      this.services.set(id, service); return service;
    } catch (error) { follower.close(); throw error; }
  }
  target(id) {
    if (this.automatic) return this.automaticTarget(id);
    const service = this.services.get(id);
    if (!service) throw new ControlError('outside_scope', '未开放这个任务。', 403);
    return service;
  }
  projects() {
    if (this.automatic) return this.refreshCatalog().then(() => [...this.catalog.values()].map(row =>
      ({id:row.projectId, name:row.title, path:row.projectPath})));
    return [...this.services.values()].map(s => ({id:s.config.projectId, name:s.config.title, path:s.config.projectPath}));
  }
  async sessions() {
    if (this.automatic) {
      await this.refreshCatalog();
      // 列表不为几十条历史启动 follower；状态没有实时确认时明确 UNKNOWN。
      return [...this.catalog.values()].map(row => ({id:row.threadId, project_id:row.projectId,
        title:row.title, status:'UNKNOWN', active_turn_id:null, updated_at:Number(row.updatedAt)*1000 || 0}));
    }
    // 一个未打开的任务不能让另一条已开放任务从列表消失；进入该任务仍返回真实错误。
    return Promise.all([...this.services.values()].map(async s => {
      try { return (await s.read(s.config.threadId)).session; }
      catch { return {id:s.config.threadId, project_id:s.config.projectId, title:s.config.title,
        status:'UNKNOWN', active_turn_id:null, updated_at:0}; }
    }));
  }
  // 只由手机显式 open=1 触发；后台 read 默认不打开任何 Desktop 页面。
  async prepareDesktopRead(service) {
    const id=service.config.threadId;
    const guard=()=>{
      if(this.closed||this.draining) throw new ControlError('disconnect_preparing','连接器正在断开，未打开对话。',503);
      if(this.options.quarantine) throw new ControlError('update_quarantine','更新验证中，未打开对话。',503);
      if(this.checking||this.writes) throw new ControlError('operation_busy','任务操作尚未完成，请稍后重试。');
      this.receipts.assertCertain();this.activity.assertCertain();
    };
    guard();
    if(this.desktopOpening) {
      if(this.desktopOpening.id!==id) throw new ControlError('operation_busy','正在打开另一条对话，请稍后重试。');
      return this.desktopOpening.promise;
    }
    const pending={id};this.desktopOpening=pending;
    pending.promise=(async()=>{
      await this.prepareDesktop(service.config,service.follower,{guard,onOpen:()=>{
        guard();this.activity.change(service.config,true); // 未取得快照的打开也要阻止更新猜测空闲。
      },refresh:async()=>{
        await this.refreshCatalog();guard();
        const row=this.catalog.get(id);
        if(!row) throw new ControlError('outside_scope','这个对话已不在本机目录中，请刷新。',403);
        if(row.projectPath!==service.config.projectPath) throw new ControlError('task_directory_changed','Desktop 对话目录已变化，请重新连接。',409);
      }});
      guard();
    })().finally(()=>{if(this.desktopOpening===pending)this.desktopOpening=null;});
    return pending.promise;
  }
  readForMobile(id, options) { return this.read(id, options); }
  async read(id, {allowDesktopOpen=false}={}) {
    if (this.checking) throw new ControlError('operation_busy', '更新检查中，请稍后刷新对话。');
    this.readers.set(id, (this.readers.get(id) ?? 0) + 1);
    try {
      const service = await this.target(id);
      if(allowDesktopOpen && this.automatic) await this.prepareDesktopRead(service);
      const result = await service.read(id, evidence => {
        // 已拿到原生快照再记活动；包含随后因待审批、空历史或状态未知而报错的读取。
        // 断开期间已开始的读取也必须落盘，更新隔离探针则始终只读。
        if (this.automatic && !this.options.quarantine && !evidence.idleVerified)
          this.activity.change(service.config, true, service.stopUncertain);
      });
      if (this.automatic && !this.checking && !this.writes && !this.draining && !this.options.quarantine) {
        // 未核对空闲时保持交接记录；读失败不能把运行中的任务遗忘。
        const evidence = result[READ_EVIDENCE];
        const current = service.follower.state === undefined || service.follower.state === evidence?.state;
        const mayForget = evidence?.idleVerified && current && this.readers.get(id) === 1;
        this.activity.change(service.config, !mayForget || service.stopUncertain, service.stopUncertain);
      }
      return result;
    }
    finally {
      const remaining = this.readers.get(id) - 1;
      if (remaining) this.readers.set(id, remaining); else this.readers.delete(id);
      this.prune();
    }
  }
  async write(id, action, body) {
    if (this.options.quarantine) throw new ControlError('update_quarantine', '更新验证中，未发送操作。', 503);
    if (this.automatic) this.activity.assertCertain();
    if (this.draining) throw new ControlError('disconnect_preparing', '连接器正在断开，未发送操作。', 503);
    if (this.checking || this.writes) throw new ControlError('operation_busy', '正在核对全部任务，请稍后刷新。');
    this.writes++;
    try { const service = this.automatic ? await this.target(id) : this.target(id); if (this.automatic) this.activity.change(service.config, true);
      try { return await service[action](id, body); }
      catch (error) {
        if (this.automatic && service.stopUncertain) this.activity.change(service.config,true,true);
        throw error;
      } } finally { this.writes--; this.prune(); }
  }
  send(id, body) { return this.write(id, 'send', body); }
  stop(id, body) { return this.write(id, 'stop', body); }
  answer(id, body) { return this.write(id, 'answer', body); }
  async prepareDisconnect() {
    this.draining = true;
    // 所有任务先一起禁止新写操作，再等待旧操作结束；不停止 Desktop 正在执行的任务。
    for (const service of this.services.values()) service.draining = true;
    // 在途读取还可能取得活动任务快照。保留断开栅栏，让调用方稍后重试，不能先报安全退出。
    if (this.readers.size)
      throw new ControlError('drain_timeout', '仍在读取对话，请稍后重试断开。', 503);
    this.receipts.assertCertain();
    if (this.automatic) this.activity.assertCertain();
    await Promise.all([...this.services.values()].map(s => s.prepareDisconnect()));
    this.receipts.assertCertain();
    if (this.automatic) this.activity.assertCertain();
    return {drained:true};
  }
  async updateStatus() {
    if (this.draining || this.checking || this.writes || this.readers.size)
      throw new ControlError('operation_busy', '任务操作尚未完成，更新已延期。');
    this.checking = true;
    try {
      this.receipts.assertCertain();
      if (this.automatic) {
        // 标题目录不是已接入任务。保留迁移前两条原任务门禁，并检查持久化的手机操作任务。
        // 不枚举未打开的历史，也绝不把“没有执行端”说成空闲。
        await this.refreshCatalog();
        const anchors = this.config.threadId ? taskConfigs({...this.config,taskScope:'selected'}) : [];
        const tasks = new Map(anchors.map(t=>[t.threadId,t]));
        for (const task of this.activity.entries()) {
          const anchor = tasks.get(task.threadId);
          if (anchor && toNamespacedPath(realpathSync(anchor.projectPath)).toLowerCase() !==
              toNamespacedPath(realpathSync(task.projectPath)).toLowerCase())
            throw new ControlError('task_directory_changed','手机任务目录与原任务绑定不一致，更新已延期。');
          tasks.set(task.threadId, anchor ? {...anchor,uncertain:task.uncertain} : task);
        }
        for (const task of tasks.values()) {
          if (task.uncertain) throw new ControlError('update_uncertain','上次停止结果尚未确认，更新已延期。');
          const cfg = {...this.config,...task,taskScope:'all-local'};
          delete cfg.additionalTasks;
          const follower = this.createFollower(cfg);
          try { await new Service(cfg,follower,this.receipts,this.options).updateStatus(); }
          finally { follower.close(); }
        }
        return {idle:true, version:VERSION, permissionsVerified:true, receiptsCertain:true};
      }
      // 任一开放任务忙碌、待审批、权限变化或回执未知，都不得升级。
      for (const service of this.services.values()) await service.updateStatus();
      return {idle:true, version:VERSION, permissionsVerified:true, receiptsCertain:true};
    } finally { this.checking = false; }
  }
  close() { this.closed = true; for (const service of this.services.values()) service.follower.close(); }
}
