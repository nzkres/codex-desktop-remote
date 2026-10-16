// ==================== 先读这里：这个文件到底在做什么 ====================
// 目标：手机继续电脑里已经存在的那条任务。这里负责接上该任务，不负责画手机界面。
// 路径：手机 → 服务器 → 本机任务接口 service.js → 本文件 → Codex Desktop。
// 本文件只处理最后一段“本机程序与 Desktop 通信”，不直接连手机或公网服务器。
//
// 【术语表：后文所有英文先在这里解释】
// Desktop：你电脑上正在运行的 Codex 桌面应用，不是 Windows 的桌面文件夹。
// thread / conversation：协议里对一条任务/对话的称呼。这里两者指同一个原任务。
// threadId / conversationId：任务的唯一标识。前者是配置字段，后者是请求字段。
//   它们都不是任务标题；标题相同也可能是两条不同的任务。
// owner / Desktop owner：当前在 Desktop 本机通信协议中负责处理这条任务请求的客户端。
//   本文件把该客户端的标识字符串存进 this.owner；并不存整个客户端对象。
//   owner 不是你本人、登录账号、文件所有者，也不是操作系统的进程 PID。
//   这里能确认的是协议里的客户端标识，不能据此断言它对应哪个内部窗口/进程。
// clientId：本机通信中的客户端标识；这里的 client 不是专指 Android 手机。
// handledByClientId：响应里的“这次请求由哪个客户端处理”字段，用它取得 owner 标识。
//   举例（虚构）：任务 ID 是 task-A，负责它的客户端标识是 desktop-client-B。
//   前者回答“哪条任务”，后者回答“现在把操作交给谁”。二者不要混淆。
// Follower：跟随者。本类只观察、请求控制原任务，不取代 Desktop 拥有这条任务。
// IPC：Inter-Process Communication，进程间通信，即同一电脑上程序之间交换消息。
//   本项目通过 Windows 命名管道 \\.\pipe\codex-ipc 通信；不是公网 HTTP 地址。
// snapshot / 快照：Desktop 在某次消息中给出的任务状态对象，例如任务 ID、目录、回合。
//   快照是“那一刻的状态”，不是数据库备份，也不保证下一秒状态仍然不变。
// state：本对象保存的最近一次通过检查的快照；断线时清空，防止继续使用过时数据。
// revision：Desktop 发来的快照版本号。数字小于已接受版本时，当成旧消息忽略。
// serial：本连接器自己计数的“合格快照接收次数”；与 Desktop 的 revision 不一样。
// request：发一条请求并等待对应响应，例如询问谁负责任务。
// broadcast：协议中的通知消息；本文件用目标列表把通知发给 owner，不是发往互联网所有人。
// event / 事件：程序内部的通知机制。on 注册回调，emit 发出通知，off 移除回调。
//   fresh-snapshot 是本程序内部事件名，不是手机请求地址，也不是 Desktop 请求方法。
// Promise：代表“未来成功返回结果，或者失败”的 JavaScript 对象，不是结果本身。
// async / await：async 函数返回 Promise；await 暂停当前异步步骤等待结果，不把整个程序卡住。
// callback / 回调：先登记一个函数，等收到消息、超时或断线时再调用它。
// turn / 回合：任务中的一次执行过程；新回合仍在原任务中，并不是新建任务。
//
// 【阅读导航】constructor 准备 → snapshot 发起读取 → readFresh 找到 owner 并等待
// → receive 核对消息 → 返回 state；需要操作时调用 forward；退出时调用 close。
// 【边界】本文件核对任务 ID/目录/消息来源，详细任务权限还由 service.js 核对。
// =======================================================================

// 原任务跟随器：发现 Desktop owner，接收实时快照，只转发允许的几种原生操作。
// 任务执行权仍归 Desktop；只有明确失联后重新发现并验证新快照，才允许切换 owner。

// import 表示从其他模块取工具。realpathSync 解析磁盘上的实际路径（包括链接目标）；
// resolve 把路径整理为绝对路径；object 判断普通对象；nativeTurns 检查/转换原生回合。
import { realpathSync } from 'node:fs';
import { resolve, toNamespacedPath } from 'node:path';
import { DesktopIpc, DESKTOP_VERSIONS, ControlError, object } from './vendor/desktop-ipc.js';
import { nativeTurns } from './vendor/desktop-state.js';

// Follower only: no app-server, ownership handlers, resume or task creation.
export class Follower {
  // 输入：固定任务配置及 Desktop IPC 客户端；缓存 owner 和最后一次可信快照。
  // 第 1 步：new Follower(config) 时执行。config 必须由外部提供，不在这里搜索任务。
  // ipc 可由测试传入假通信对象；没传时创建真实 DesktopIpc。undefined 使用默认管道，
  // 12000 是底层请求等待时间（毫秒）；hostScopedFollowerRequests 是协议版本适配选项，
  // 表示按带主机信息的 follower 请求处理，不是允许连接远程电脑的开关。
  constructor(config, ipc = new DesktopIpc(undefined, 12000, { hostScopedFollowerRequests: true })) {
    // this 表示“这一个 Follower 实例”。this.config 保存它自己要跟随的任务配置。
    this.config = config;
    this.ipc = ipc;
    // null 表示还不知道任务由谁负责；发现成功后存入 handledByClientId 字符串。
    this.owner = null;
    // 以 -1 初始化，给第一次收到的正常快照版本留出比较起点。
    this.revision = -1;
    // 从 0 开始计数；每接到合格快照加 1，用来判断本次等待有没有收到新消息。
    this.serial = 0;
    // 保存正在进行的读取 Promise，避免多个调用者同时触发相同发现流程。
    this.refresh = null;
    this.state = null;
    // owner/revision 只在同一连接代际有效；失联后必须重新发现并读取完整快照。
    this.generation = 0;
    this.awaiting = null;
    this.ipc.on('disconnected', () => this.invalidate());
    // m 是收到的消息对象；箭头函数 m => ... 表示收到消息后调用 receive(m)。
    this.ipc.on('broadcast', m => this.receive(m));
  }
  // 第 2 个工具：确认“这份状态属于配置中的原任务和目录”。返回 true/false。
  // 名字虽叫 authorized，这里并没有检查全部写入/网络权限；不要误把它当完整授权。
  authorized(state) {
    // 同时核对任务 ID、真实路径和路径写法；路径无法解析就拒绝该快照。
    // ?. 是可选访问：state 为空时不会因为访问 id 崩溃；!== 表示严格不相等。
    // cwd 是 current working directory，即任务的工作目录；projectPath 是配置中允许的目录。
    if (state?.id !== this.config.threadId || typeof state.cwd !== 'string') return false;
    // 两种路径比较都要通过：实际落盘位置相同，整理后的路径也相同；统一小写用于 Windows 比较。
    // try/catch 表示路径读取失败时返回 false，不尝试猜测或修复目录。
    try { return toNamespacedPath(realpathSync.native(state.cwd)).toLowerCase() === toNamespacedPath(realpathSync.native(this.config.projectPath)).toLowerCase()
      && toNamespacedPath(resolve(state.cwd)).toLowerCase() === toNamespacedPath(resolve(this.config.projectPath)).toLowerCase(); }
    catch { return false; }
  }
  invalidate() {
    this.generation++;
    this.state = null;
    this.owner = null;
    this.revision = -1;
    this.awaiting = null;
  }
  // 第 3 步（被事件调用）：此函数是收件员，m 包含 method、version、params 等字段。
  // method 是消息种类，version 是该消息的协议版本（不是软件版本），params 是具体内容。
  receive(m) {
    // 只接收当前 owner、local 主机、目标任务、正确协议版本的完整快照。
    // owner 断开或 IPC 断开会清空缓存；旧 revision、错误历史结构均被忽略。
    const p = m.params;
    if (!object(p)) return;
    // 如果通知说“负责本任务的客户端已断开”，就清掉旧快照并唤醒等待者报错。
    if (m.method === 'client-status-changed' && p.clientId === this.owner && p.status === 'disconnected') {
      this.invalidate();
      this.ipc.emit('owner-lost');
      return;
    }
    // 失效状态只能由本次订阅重建；已验证连接中的后续完整快照仍会替换旧状态，
    // 使读取与转发之间的状态变化能够让 forward 的快照身份检查失败。
    const awaiting = this.awaiting;
    if ((!awaiting && !this.state) || (awaiting && awaiting.generation !== this.generation)) return;
    const owner = awaiting?.owner ?? this.owner;
    // 普通状态通知必须同时满足：任务状态消息、协议版本匹配、local（本机）、原任务、原 owner。
    // 任一不符就 return 忽略，避免把另一个任务的内容当作本任务。
    if (m.method !== 'thread-stream-state-changed' || m.version !== DESKTOP_VERSIONS[m.method]
      || p.hostId !== 'local' || p.conversationId !== this.config.threadId || m.sourceClientId !== owner) return;
    // change 包含变化类型、版本号和状态内容。这里只接受 snapshot 类型，不在此拼接局部补丁。
    const change = p.change;
    if (!object(change) || change.type !== 'snapshot' || !Number.isSafeInteger(change.revision)
      || change.revision < this.revision || !this.authorized(change.conversationState)) return;
    // 再检查回合结构能否解析；即使来源对了，内容结构不对也不采用。
    // revision 相等仍可接受：serial 记录收到新消息，不等于任务内容一定发生了变化。
    try { nativeTurns(change.conversationState); } catch { return; }
    this.revision = change.revision;
    // conversationState 才是真正的任务状态内容；保存后通知 readFresh 的等待者。
    this.state = change.conversationState;
    this.serial++;
    this.ipc.emit('fresh-snapshot');
  }
  // 外部通常从这里请求新状态；返回 Promise，调用者需要 await 才能得到 state。
  snapshot() {
    // 并发读取共用一次 readFresh Promise，结束后释放，后续调用重新取实时状态。
    // 如果读取还在进行，直接返回同一个 Promise；不是直接返回缓存 this.state。
    if (this.refresh) return this.refresh;
    // finally 无论成功还是失败都会执行，让下一次读取能够重新开始。
    this.refresh = this.readFresh().finally(() => { this.refresh = null; });
    return this.refresh;
  }
  // 第 4 步：真正发起一次“连 Desktop → 找任务负责人 → 要新快照”的读取。
  async readFresh() {
    // 先连 Desktop 并发现执行端，再订阅快照；输出新状态，超时/断线则报明确错误。
    // 发现结果必须声明支持非可信 App 输入；owner 身份变化时要求电脑端重新核对。
    // ready() 会建立/复用本机 IPC 连接并完成通信初始化。这里还没有证明原任务可用。
    // ControlError 是项目的错误对象：错误码用于程序判断，中文用于显示，503 表示暂不可用。
    // 每次发现前撤销旧快照；发现失败也不能用上一次状态继续转发。
    this.state = null;
    try { await this.ipc.ready(); }
    catch { throw new ControlError('desktop_unavailable', '请保持 Codex Desktop 打开，消息未发送。', 503); }
    const generation = this.generation;
    let r;
    // thread-owner-discovery 字面意思是“寻找任务负责人”。未指定目标客户端，因为还不知道 owner。
    // hostId=local 限定这台电脑；conversationId 取配置的 threadId，不用任务标题查找。
    // r 是完整响应；失败不一定是任务不存在，也可能是任务没有活动处理端或响应超时。
    try { r = await this.ipc.request('thread-owner-discovery', { hostId: 'local', conversationId: this.config.threadId }); }
    catch { throw new ControlError('owner_unavailable', '请在 Desktop 打开这条对话；若仍无法接续，请在电脑发送一条消息后重试。', 503); }
    if (generation !== this.generation)
      throw new ControlError('desktop_unavailable', 'Desktop 连接已断开。', 503);
    // supportsUntrustedAppInput 意为“支持接收外部应用输入”。untrusted 是协议里的信任分类，
    // 不是说手机恶意，也不是授予无限权限；还必须通过 service.js 的任务权限核对。
    if (typeof r.handledByClientId !== 'string' || r.result?.supportsUntrustedAppInput !== true)
      throw new ControlError('owner_unverified', '无法核对执行端，操作已阻止。', 503);
    // 同一连接中 owner 突然改变仍是冲突；明确断线后 owner 已清空，可以重新发现。
    if (this.owner && this.owner !== r.handledByClientId)
      throw new ControlError('owner_changed', '任务执行端已改变，请在电脑端重新核对连接。', 409);
    this.owner = r.handledByClientId;
    // 记住等待开始前的接收次数。后面只接受计数超过它的快照消息。
    const serial = this.serial;
    const awaiting = { owner: this.owner, generation };
    this.awaiting = awaiting;
    // accept/reject 是 Promise 提供的成功/失败回调。调用 accept(state) 让 await 得到状态；
    // 调用 reject(error) 则让 await 抛出错误。下面先登记监听，再发订阅通知，避免漏接快速响应。
    return new Promise((accept, reject) => {
      // 只接受订阅之后序号增加的快照；8 秒内没有新快照便拒绝。
      // 统一收尾：取消超时定时器、移除本次临时监听，然后完成 Promise。
      // 不移除监听会让以后消息继续触发旧回调，造成重复处理或占用内存。
      const finish = (err) => {
        clearTimeout(timer);
        this.ipc.off('fresh-snapshot', fresh);
        this.ipc.off('disconnected', lost);
        this.ipc.off('owner-lost', lost);
        if (this.awaiting === awaiting) this.awaiting = null;
        err ? reject(err) : accept(this.state);
      };
      // 只有 serial 增加才完成，避免读取前已有的旧快照让本次检查立即通过。
      const fresh = () => { if (this.serial > serial) finish(); };
      const lost = () => finish(new ControlError('desktop_unavailable', 'Desktop 连接已断开。', 503));
      // 8000 毫秒是本次等待快照的上限，与构造器里底层请求的 12000 毫秒不同。
      const timer = setTimeout(() => finish(new ControlError('snapshot_unavailable', '未读到实时任务状态，请刷新。', 503)), 8000);
      this.ipc.on('fresh-snapshot', fresh);
      this.ipc.on('disconnected', lost);
      this.ipc.on('owner-lost', lost);
      // following=true 表示“开始跟随”。[this.owner] 是只含一个目标标识的数组。
      // broadcast 不直接返回任务内容；内容随后以事件到达 receive()，再触发 fresh-snapshot。
      try { this.ipc.broadcast('thread-stream-following-changed', {
        hostId: 'local', conversationId: this.config.threadId, following: true,
      }, [this.owner]); } catch { lost(); }
    });
  }
  // 第 5 步：转发操作。method 选择动作，params 是动作参数，返回的是操作响应里的 result。
  // 调用者（service.js）负责先核对权限、忙闲和回执；本函数不会独立完成这些业务检查。
  async forward(method, params, expectedState = this.state) {
    // 只允许启动、停止、加载历史和回答原问题，且始终定向已核实 owner。
    // 返回 Desktop 响应的 result；调用方负责处理回复丢失造成的未知结果。
    if (!this.state || this.state !== expectedState || this.awaiting || !this.authorized(this.state))
      throw new ControlError('owner_unavailable', '执行端状态已失效，请刷新。', 503);
    // 回答使用原requestId；不开放异步steer、创建任务或修改权限入口。
    if (!['thread-follower-start-turn', 'thread-follower-interrupt-turn', 'thread-follower-load-complete-history', 'thread-follower-submit-user-input'].includes(method))
      throw new ControlError('unsupported_control', '本轮请在 Desktop 完成这项操作。', 501);
    // ...params 把传入参数展开；后写 conversationId 强制使用原任务 ID，防止参数改换任务。
    // 第三个参数 this.owner 把请求定向给已发现的客户端；不是每次随意选择执行器。
    // 得到 result 只代表收到响应，不能在这里断言模型已经完成整轮任务。
    return (await this.ipc.request(method, { ...params, conversationId: this.config.threadId }, this.owner)).result;
  }
  // 第 6 步：供历史读取使用的完整状态入口。先读取，再检查 Desktop 是否明确标记历史未加载完。
  async completeSnapshot(verify = () => {}) {
    // 若 Desktop 标记历史未完整，先请求补全再取一次新快照；仍不完整则拒绝展示。
    let state = await this.snapshot();
    verify(state);
    // 严格比较 false：只有明确说“不完整”才请求补全；字段缺失并不在此单独证明历史完整。
    if (state.turnHistory?.history?.isComplete === false) {
      // 请求 Desktop 加载已有历史，不创建新任务、不启动新一轮模型。随后重新读取检查结果。
      await this.forward('thread-follower-load-complete-history', {}, state);
      state = await this.snapshot();
      verify(state);
      if (state.turnHistory?.history?.isComplete === false)
        throw new ControlError('incomplete_history', 'Desktop 历史尚未加载完整，请稍后刷新。', 503);
    }
    return state;
  }
  // 关闭本对象的 IPC 通信连接；不是关闭 Codex Desktop，也不是发送停止模型命令。
  close() { this.ipc.close(); }
}
