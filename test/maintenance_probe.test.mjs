import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, readdirSync, renameSync, rmSync, symlinkSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { permissionDigest } from '../src/service.js';
import { checkUpdate } from '../scripts/update_probe.mjs';

const script = new URL('../scripts/update_probe.mjs', import.meta.url);

function fixture(t, baseline='guardian_subagent', current='auto_review') {
 const home=mkdtempSync(join(tmpdir(),'maintenance-probe-synthetic-'));
 t.after(()=>rmSync(home,{recursive:true,force:true}));
 const dataDir=join(home,'task');
 const receipts=join(dataDir,'receipts');
 mkdirSync(receipts,{recursive:true});
 const permissions={approvalPolicy:'on-request',approvalsReviewer:baseline,
  sandboxPolicy:{type:'readOnly',networkAccess:false}};
 const state={currentPermissions:{...permissions,approvalsReviewer:current},
  threadRuntimeStatus:{type:'idle'},requests:[],unconfirmedTurnSubmissions:[]};
 const config={dataDir,permissionsHash:permissionDigest({currentPermissions:permissions})};
 let snapshots=0, forwards=0;
 const follower={completeSnapshot:async()=>{snapshots++;return state;},
  forward:async()=>{forwards++;throw new Error('unexpected_model_input');}};
 return {home,dataDir,receipts,state,config,follower,
  counts:()=>({snapshots,forwards})};
}

for(const [before,after] of [['guardian_subagent','auto_review'],['auto_review','guardian_subagent']])
 test(`alias-only accepts ${before} to ${after} from one fresh snapshot`,async t=>{
  const f=fixture(t,before,after);
  const originalState=structuredClone(f.state), originalConfig=structuredClone(f.config);
  const entries=readdirSync(f.receipts);
  const result=await checkUpdate(f.config,f.follower,true);
  assert.deepEqual(Object.keys(result).sort(),
   ['idle','version','permissionsVerified','receiptsCertain','reviewerAliasOnly'].sort());
  assert.equal(result.idle,true);
  assert.equal(result.permissionsVerified,true);
  assert.equal(result.receiptsCertain,true);
  assert.equal(result.reviewerAliasOnly,true);
  assert.deepEqual(f.counts(),{snapshots:1,forwards:0});
  assert.deepEqual(f.state,originalState);
  assert.deepEqual(f.config,originalConfig);
  assert.deepEqual(readdirSync(f.receipts),entries);
 });

test('pinned Desktop may omit its empty unconfirmed list without mutating the snapshot',async t=>{
 const f=fixture(t);
 delete f.state.unconfirmedTurnSubmissions;
 const originalState=structuredClone(f.state);
 const result=await checkUpdate(f.config,f.follower,true);
 assert.equal(result.reviewerAliasOnly,true);
 assert.equal(result.idle,true);
 assert.equal(Object.hasOwn(f.state,'unconfirmedTurnSubmissions'),false);
 assert.deepEqual(f.state,originalState);
 assert.deepEqual(f.counts(),{snapshots:1,forwards:0});
});

test('normal matching digest is not maintenance evidence; default keeps four fields',async t=>{
 const f=fixture(t,'auto_review','auto_review');
 await assert.rejects(checkUpdate(f.config,f.follower,true),/reviewer_alias_not_proven/);
 assert.deepEqual(f.counts(),{snapshots:1,forwards:0});
 const normal=await checkUpdate(f.config,f.follower);
 assert.deepEqual(Object.keys(normal).sort(),
  ['idle','version','permissionsVerified','receiptsCertain'].sort());
 assert.equal(normal.idle,true);
 assert.deepEqual(f.counts(),{snapshots:2,forwards:0});
});

test('an alias plus a real permission change is rejected',async t=>{
 const f=fixture(t);
 f.state.currentPermissions.approvalPolicy='never';
 await assert.rejects(checkUpdate(f.config,f.follower,true),/reviewer_alias_not_proven/);
 assert.deepEqual(f.counts(),{snapshots:1,forwards:0});
});

for(const [name,change] of [
 ['missing requests',s=>delete s.requests],
 ['malformed requests',s=>s.requests={}],
 ['pending request',s=>s.requests=[{}]],
 ['null own unconfirmed',s=>s.unconfirmedTurnSubmissions=null],
 ['undefined own unconfirmed',s=>s.unconfirmedTurnSubmissions=undefined],
 ['malformed unconfirmed',s=>s.unconfirmedTurnSubmissions='unknown'],
 ['unconfirmed submission',s=>s.unconfirmedTurnSubmissions=[{}]],
]) test(`alias-only refuses ${name}`,async t=>{
 const f=fixture(t);change(f.state);
 await assert.rejects(checkUpdate(f.config,f.follower,true),/pending_desktop_action/);
 assert.deepEqual(f.counts(),{snapshots:1,forwards:0});
});

test('unknown receipt refuses maintenance without altering the record',async t=>{
 const f=fixture(t);
 const record=join(f.receipts,'a'.repeat(64)+'.json');
 const pending=JSON.stringify({schema:1,state:'pending'});
 writeFileSync(record,pending);
 await assert.rejects(checkUpdate(f.config,f.follower,true),{code:'send_unknown'});
 assert.deepEqual(f.counts(),{snapshots:1,forwards:0});
 assert.deepEqual(readdirSync(f.receipts),['a'.repeat(64)+'.json']);
});

for(const [name,create] of [
 ['temporary receipt',directory=>writeFileSync(join(directory,'pending.tmp'),'synthetic')],
 ['non-JSON receipt',directory=>writeFileSync(join(directory,'unexpected.txt'),'synthetic')],
 ['subdirectory',directory=>mkdirSync(join(directory,'unfinished'))],
]) test(`maintenance refuses ${name} in receipt directory`,async t=>{
 const f=fixture(t);
 create(f.receipts);
 const before=readdirSync(f.receipts);
 await assert.rejects(checkUpdate(f.config,f.follower,true),/receipt_directory_unverified/);
 assert.deepEqual(readdirSync(f.receipts),before);
 assert.deepEqual(f.counts(),{snapshots:0,forwards:0});
});

test('missing receipt directory fails closed and is not created',async t=>{
 const f=fixture(t);
 rmSync(f.receipts,{recursive:true});
 await assert.rejects(checkUpdate(f.config,f.follower,true));
 assert.deepEqual(readdirSync(f.dataDir),[]);
 assert.deepEqual(f.counts(),{snapshots:0,forwards:0});
});

test('linked receipt directory is rejected without creating records',async t=>{
 const f=fixture(t);
 const target=join(f.dataDir,'real-receipts');
 renameSync(f.receipts,target);
 try { symlinkSync(target,f.receipts,process.platform==='win32'?'junction':'dir'); }
 catch { t.skip('directory symlink privilege unavailable'); return; }
 await assert.rejects(checkUpdate(f.config,f.follower,true),/unsafe_receipt_directory/);
 assert.deepEqual(readdirSync(target),[]);
 assert.deepEqual(f.counts(),{snapshots:0,forwards:0});
});

test('unknown CLI flag gets only the fixed failure output',()=>{
 const result=spawnSync(process.execPath,[fileURLToPath(script),'nonexistent.json','--unknown'],
  {encoding:'utf8',timeout:5000});
 assert.equal(result.status,1);
 assert.deepEqual(JSON.parse(result.stdout.trim()),
  {idle:false,code:'active_or_unverified_desktop_task'});
});
