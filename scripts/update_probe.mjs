// OTA 只读任务预检：读取已有回执与新鲜 Desktop 快照，检查权限、空闲及未知操作。
// 不创建缺失的回执目录、不发送模型输入；失败由更新器延期处理。

// No model input, no changes to permissions/configuration, no identity adoption.
import { lstatSync, readFileSync, readdirSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { pathToFileURL } from 'node:url';
import { Follower } from '../src/follower.js';
import { Service, permissionDigest, permissionsMatch } from '../src/service.js';
import { Receipts } from '../src/receipts.js';
import { verifyCompatibility } from '../src/compatibility.js';

export async function checkUpdate(config, follower, reviewerAliasOnly = false) {
  if (!/^[a-f0-9]{64}$/.test(config.permissionsHash) || typeof reviewerAliasOnly !== 'boolean')
    throw new Error('permissions_unverified');
  const directory = join(config.dataDir, 'receipts');
  const info = lstatSync(directory); // Missing is a refusal, never a mkdir.
  if (!info.isDirectory() || info.isSymbolicLink()) throw new Error('unsafe_receipt_directory');
  const receipts = Object.create(Receipts.prototype);
  receipts.directory = directory;
  if (!reviewerAliasOnly)
    return new Service(config, follower, receipts).updateStatus();
  if (readdirSync(directory,{withFileTypes:true}).some(entry =>
      !entry.isFile() || !/^[a-f0-9]{64}\.json$/.test(entry.name)))
    throw new Error('receipt_directory_unverified');

  // The maintenance check consumes exactly one fresh native snapshot. Service
  // observes that captured object again; it cannot fetch a different state.
  const state = await follower.completeSnapshot();
  // Pinned Desktop 26.930.7945.0 deletes an empty
  // unconfirmedTurnSubmissions list from its snapshot. An own field, when
  // present, must still be an explicit empty array; null/undefined is unknown.
  if (!Array.isArray(state?.requests) || state.requests.length !== 0
      || (Object.hasOwn(state, 'unconfirmedTurnSubmissions')
          && (!Array.isArray(state.unconfirmedTurnSubmissions)
              || state.unconfirmedTurnSubmissions.length !== 0)))
    throw new Error('pending_desktop_action');
  if (permissionDigest(state) === config.permissionsHash
      || !permissionsMatch(state, config.permissionsHash))
    throw new Error('reviewer_alias_not_proven');
  const readonlyFollower = { completeSnapshot: async () => state };
  const result = await new Service(config, readonlyFollower, receipts).updateStatus();
  return { ...result, reviewerAliasOnly: true };
}

async function main(argv = process.argv.slice(2)) {
  let follower;
  try {
    if (argv.length < 1 || argv.length > 2
        || (argv.length === 2 && argv[1] !== '--reviewer-alias-only'))
      throw new Error('invalid_probe_arguments');
    const config = JSON.parse(readFileSync(resolve(argv[0]), 'utf8'));
    if (!/^[a-f0-9]{64}$/.test(config.permissionsHash)) throw new Error('permissions_unverified');
    await verifyCompatibility(config);
    follower = new Follower(config);
    console.log(JSON.stringify(await checkUpdate(config, follower, argv.length === 2)));
  } catch {
    console.log(JSON.stringify({idle:false,code:'active_or_unverified_desktop_task'}));
    process.exitCode = 1;
  } finally { follower?.close(); }
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href)
  await main();
