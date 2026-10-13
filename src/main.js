// 本机 Node 服务入口：读取任务配置 → 校验 Desktop → 接收启动凭据 → 建立独占管道 → HTTP 监听。
// 它是手机请求的本地适配器，不启动另一个模型执行器。

import { readFileSync, realpathSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { createServer } from 'node:net';
import { createHash } from 'node:crypto';
import { TaskService, taskConfigs } from './tasks.js';
import { httpServer, permissionDigests } from './service.js';
import { Receipts } from './receipts.js';
import { verifyCompatibility } from './compatibility.js';
import { VERSION } from './version.js';

// 启动顺序：读取受限配置并核对本机 Desktop 文件，再从 stdin 接收凭据、占用单实例管道，
// 最后创建跟随器和仅监听 127.0.0.1 的 HTTP 服务。任何一步失败都进入末尾的清理分支。
// Credentials enter only through the inherited pipe, never arguments or logs.
async function bootstrap() {
  // 输入：父进程写入的一段 JSON；输出：带 token/controlToken 的启动参数。
  // 最多读取 1 KiB，并用 5 秒计时器限制等待；普通 token 与控制 token 必须不同。
  let data = '';
  const timer = setTimeout(() => { console.error('bootstrap_timeout'); process.exit(1); }, 5000);
  for await (const chunk of process.stdin) {
    data += chunk;
    if (Buffer.byteLength(data) > 1024) throw new Error('invalid_bootstrap');
  }
  clearTimeout(timer);
  const value = JSON.parse(data);
  if (value.schema !== 1 || !/^[a-f0-9]{64}$/.test(value.controlToken)
    || value.controlToken === value.token) throw new Error('invalid_bootstrap');
  return value;
}
let service, server, lease;
try {
  // argv[2] 指向配置文件；任务编号、权限摘要、端口和标题先做形状检查。
  // realpathSync 固定项目与数据目录的真实路径，兼容性检查失败时不启动服务。
  const config = JSON.parse(readFileSync(resolve(process.argv[2]), 'utf8'));
  if (config.schema !== 1 || !/^[a-f0-9-]{36}$/.test(config.threadId) || !/^[a-f0-9]{64}$/.test(config.permissionsHash)
    || !Number.isInteger(config.port) || config.port < 1024 || config.port > 65535
    || typeof config.title !== 'string' || config.title.length > 100) throw new Error('invalid_configuration');
  permissionDigests(config);
  taskConfigs(config);
  config.projectPath = realpathSync(config.projectPath);
  config.dataDir = realpathSync(config.dataDir);
  await verifyCompatibility(config);
  const auth = await bootstrap();
  const scope = createHash('sha256').update(config.dataDir.toLowerCase()).digest('hex').slice(0,32);
  // 管道名由数据目录派生，已有实例占用同一管道时 listen 会失败。
  lease = createServer(socket => socket.destroy());
  await new Promise((yes,no) => { lease.once('error',no); lease.listen('\\\\.\\pipe\\desktop-remote-lite-'+scope,yes); });
  service = new TaskService(config, new Receipts(join(config.dataDir,'receipts')), {quarantine:auth.quarantine === true});
  server = httpServer(service,auth.token,auth.instance,auth.controlToken);
  // HTTP 端口只绑定本机回环地址；listening 状态表示本地服务已监听，不代表 Desktop 执行能力已核实。
  await new Promise((yes,no) => { server.once('error',no); server.listen(config.port,'127.0.0.1',yes); });
  console.log(JSON.stringify({schema:1,state:'listening',port:config.port,version:VERSION,scope:'explicit-desktop-tasks'}));
  const close = () => { server.close(); lease.close(); service.close(); };
  process.on('SIGINT',close); process.on('SIGTERM',close);
} catch (e) {
  // 启动异常只输出有限错误码，关闭已经创建的资源并设置失败退出码。
  console.error(JSON.stringify({schema:1,state:'stopped',code:e.code && /^[a-z_]{1,60}$/.test(e.code)?e.code:'startup_failed'}));
  server?.close(); lease?.close(); service?.close(); process.exitCode=1;
}
