import { readFileSync, realpathSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { createServer } from 'node:net';
import { createHash } from 'node:crypto';
import { Follower } from './follower.js';
import { Service, httpServer } from './service.js';
import { Receipts } from './receipts.js';
import { verifyCompatibility } from './compatibility.js';
import { VERSION } from './version.js';

// Credentials enter only through the inherited pipe, never arguments or logs.
async function bootstrap() {
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
let follower, server, lease;
try {
  const config = JSON.parse(readFileSync(resolve(process.argv[2]), 'utf8'));
  if (config.schema !== 1 || !/^[a-f0-9-]{36}$/.test(config.threadId) || !/^[a-f0-9]{64}$/.test(config.permissionsHash)
    || !Number.isInteger(config.port) || config.port < 1024 || config.port > 65535
    || typeof config.title !== 'string' || config.title.length > 100) throw new Error('invalid_configuration');
  config.projectPath = realpathSync(config.projectPath);
  config.dataDir = realpathSync(config.dataDir);
  await verifyCompatibility(config);
  const auth = await bootstrap();
  const scope = createHash('sha256').update(config.dataDir.toLowerCase()).digest('hex').slice(0,32);
  lease = createServer(socket => socket.destroy());
  await new Promise((yes,no) => { lease.once('error',no); lease.listen('\\\\.\\pipe\\desktop-remote-lite-'+scope,yes); });
  follower = new Follower(config);
  server = httpServer(new Service(config,follower,new Receipts(join(config.dataDir,'receipts'))),auth.token,auth.instance,auth.controlToken);
  await new Promise((yes,no) => { server.once('error',no); server.listen(config.port,'127.0.0.1',yes); });
  console.log(JSON.stringify({schema:1,state:'listening',port:config.port,version:VERSION,scope:'one-desktop-task'}));
  const close = () => { server.close(); lease.close(); follower.close(); };
  process.on('SIGINT',close); process.on('SIGTERM',close);
} catch (e) {
  console.error(JSON.stringify({schema:1,state:'stopped',code:e.code && /^[a-z_]{1,60}$/.test(e.code)?e.code:'startup_failed'}));
  server?.close(); lease?.close(); follower?.close(); process.exitCode=1;
}
