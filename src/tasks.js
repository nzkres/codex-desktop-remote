// 已授权任务清单：每条任务复用原 Service/Follower；不搜索或自动开放其他对话。
import { realpathSync } from 'node:fs';
import { isAbsolute } from 'node:path';
import { Service, permissionDigests } from './service.js';
import { Follower } from './follower.js';
import { ControlError, object } from './vendor/desktop-ipc.js';
import { VERSION } from './version.js';

export function taskConfigs(config) {
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
  constructor(config, receipts, {createFollower = cfg => new Follower(cfg), ...options} = {}) {
    this.config = config; this.services = new Map(); this.draining = false; this.checking = false; this.writes = 0;
    const configs = taskConfigs(config);
    try {
      for (const task of configs) {
        const follower = createFollower(task);
        try { this.services.set(task.threadId, new Service(task, follower, receipts, options)); }
        catch (error) { follower.close(); throw error; }
      }
    } catch (error) { this.close(); throw error; }
    this.sync = this.services.values().next().value.sync; // MobileSync 自身按任务 ID 隔离游标。
  }
  target(id) {
    const service = this.services.get(id);
    if (!service) throw new ControlError('outside_scope', '未开放这个任务。', 403);
    return service;
  }
  projects() {
    return [...this.services.values()].map(s => ({id:s.config.projectId, name:s.config.title, path:s.config.projectPath}));
  }
  async sessions() {
    // 一个未打开的任务不能让另一条已开放任务从列表消失；进入该任务仍返回真实错误。
    return Promise.all([...this.services.values()].map(async s => {
      try { return (await s.read(s.config.threadId)).session; }
      catch { return {id:s.config.threadId, project_id:s.config.projectId, title:s.config.title,
        status:'UNKNOWN', active_turn_id:null, updated_at:0}; }
    }));
  }
  read(id) { return this.target(id).read(id); }
  async write(id, action, body) {
    const service = this.target(id);
    if (this.draining) throw new ControlError('disconnect_preparing', '连接器正在断开，未发送操作。', 503);
    if (this.checking || this.writes) throw new ControlError('operation_busy', '正在核对全部任务，请稍后刷新。');
    this.writes++;
    try { return await service[action](id, body); } finally { this.writes--; }
  }
  send(id, body) { return this.write(id, 'send', body); }
  stop(id, body) { return this.write(id, 'stop', body); }
  async prepareDisconnect() {
    this.draining = true;
    // 所有任务先一起禁止新写操作，再等待旧操作结束；不停止 Desktop 正在执行的任务。
    for (const service of this.services.values()) service.draining = true;
    await Promise.all([...this.services.values()].map(s => s.prepareDisconnect()));
    return {drained:true};
  }
  async updateStatus() {
    if (this.draining || this.checking || this.writes)
      throw new ControlError('operation_busy', '任务操作尚未完成，更新已延期。');
    this.checking = true;
    try {
      // 任一开放任务忙碌、待审批、权限变化或回执未知，都不得升级。
      for (const service of this.services.values()) await service.updateStatus();
      return {idle:true, version:VERSION, permissionsVerified:true, receiptsCertain:true};
    } finally { this.checking = false; }
  }
  close() { for (const service of this.services.values()) service.follower.close(); }
}
