// Read-only operator diagnostic. A successful task probe never authorizes cold recovery.
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';
import { verifyCompatibility } from '../src/compatibility.js';
import { Follower } from '../src/follower.js';
import { checkUpdate } from './update_probe.mjs';

const PUBLIC_CODES = new Set([
  'invalid_config', 'unsupported_platform', 'unsupported_version', 'hash_mismatch',
  'missing_file', 'io_error', 'reparse_path', 'invalid_file', 'invalid_manifest',
  'desktop_unavailable', 'owner_unavailable', 'owner_unverified', 'owner_changed',
  'snapshot_unavailable', 'incomplete_history', 'permissions_changed', 'permissions_unverified',
  'thread_busy', 'runtime_status_unknown', 'send_unknown', 'update_uncertain',
  'unsafe_receipt_directory', 'outside_scope',
]);

export async function diagnoseDesktop(config, {
  verify = verifyCompatibility, createFollower = cfg => new Follower(cfg),
} = {}) {
  const report = { schema: 1, readOnly: true, modelInputs: 0, phase: 'compatibility',
    taskReady: false, reconnectAuthorized: false };
  let follower;
  try {
    await verify(config);
    report.compatible = true;
    report.phase = 'snapshot';
    follower = createFollower(config);
    const state = await follower.completeSnapshot();
    if (state?.id !== config.threadId) throw Object.assign(new Error(), { code: 'outside_scope' });
    report.sameTask = true;
    report.phase = 'permissions_idle_receipts';
    // Reuse the captured fresh snapshot: no second read can change the assessment mid-check.
    report.probe = await checkUpdate(config, { completeSnapshot: async () => state });
    report.taskReady = true;
    report.phase = 'complete';
  } catch (error) {
    const code = error?.code ?? error?.message;
    report.code = PUBLIC_CODES.has(code) ? code : 'desktop_diagnostic_failed';
  } finally {
    follower?.close();
  }
  return report;
}

async function main(args) {
  let report;
  try {
    if (args.length !== 1 && !(args.length === 3 && args[1] === '--desktop-root'))
      throw new Error('invalid_arguments');
    const config = JSON.parse(readFileSync(resolve(args[0]), 'utf8'));
    // Override only in memory. Never modify installed config, permission hash or identity.
    if (args.length === 3) config.desktopRoot = resolve(args[2]);
    report = await diagnoseDesktop(config);
  } catch {
    report = { schema: 1, readOnly: true, taskReady: false, reconnectAuthorized: false,
      code: 'desktop_diagnostic_failed' };
  }
  console.log(JSON.stringify(report));
  process.exitCode = report.taskReady ? 0 : 1;
}
if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href)
  await main(process.argv.slice(2));
