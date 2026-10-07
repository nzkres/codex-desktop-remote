// No model input, no changes to permissions/configuration, no identity adoption.
import { readFileSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { Follower } from '../src/follower.js';
import { Service } from '../src/service.js';
import { Receipts } from '../src/receipts.js';
import { verifyCompatibility } from '../src/compatibility.js';
let follower;
try {
  const config = JSON.parse(readFileSync(resolve(process.argv[2]), 'utf8'));
  if (!/^[a-f0-9]{64}$/.test(config.permissionsHash)) throw new Error('permissions_unverified');
  await verifyCompatibility(config);
  follower = new Follower(config);
  const service = new Service(config, follower, new Receipts(join(config.dataDir, 'receipts')));
  console.log(JSON.stringify(await service.updateStatus()));
} catch {
  console.log(JSON.stringify({idle:false,code:'active_or_unverified_desktop_task'}));
  process.exitCode = 1;
} finally { follower?.close(); }
