import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, readdirSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { permissionDigest } from '../src/service.js';
import { diagnoseDesktop } from '../scripts/diagnose_desktop.mjs';

function fixture(t) {
  const dataDir = mkdtempSync(join(tmpdir(), 'desktop-diagnostic-'));
  t.after(() => rmSync(dataDir, { recursive:true, force:true }));
  const receipts = join(dataDir, 'receipts'); mkdirSync(receipts);
  const state = { id:'synthetic', currentPermissions:{approvalPolicy:'on-request',
    sandboxPolicy:{type:'readOnly', networkAccess:false}},
    requests:[], threadRuntimeStatus:{type:'idle'}, turns:[] };
  const config = { threadId:state.id, dataDir, permissionsHash:permissionDigest(state) };
  let closed=0, reads=0;
  const deps = { verify:async()=>({ok:true}), createFollower:()=>({
    completeSnapshot:async()=>{ reads++; return state; }, close:()=>{closed++;},
    forward:()=>{throw new Error('must_never_send');},
  }) };
  return {state,config,deps,receipts,counts:()=>({closed,reads})};
}

test('fresh successful probe remains diagnostic only and does not mutate data',async t=>{
  const f=fixture(t), original=structuredClone({state:f.state,config:f.config});
  const r=await diagnoseDesktop(f.config,f.deps);
  assert.equal(r.taskReady,true); assert.equal(r.reconnectAuthorized,false);
  assert.deepEqual(f.counts(),{closed:1,reads:1});
  assert.deepEqual({state:f.state,config:f.config},original);
  assert.deepEqual(readdirSync(f.receipts),[]);
});
test('changed permissions are reported, never rebased',async t=>{
  const f=fixture(t); f.state.currentPermissions.sandboxPolicy.networkAccess=true;
  const before=structuredClone(f.config);
  const r=await diagnoseDesktop(f.config,f.deps);
  assert.equal(r.code,'permissions_changed'); assert.equal(r.taskReady,false);
  assert.deepEqual(f.config,before);
});
test('unknown compatibility error is redacted before any IPC',async t=>{
  const f=fixture(t);f.deps.verify=async()=>{throw new Error('private-path-and-token');};
  const r=await diagnoseDesktop(f.config,f.deps);
  assert.equal(r.code,'desktop_diagnostic_failed');
  assert.equal(JSON.stringify(r).includes('private-path'),false);
  assert.deepEqual(f.counts(),{closed:0,reads:0});
});
test('active task and unknown receipt both block without replay',async t=>{
  const f=fixture(t);f.state.threadRuntimeStatus.type='active';
  assert.equal((await diagnoseDesktop(f.config,f.deps)).code,'thread_busy');
  f.state.threadRuntimeStatus.type='idle';
  writeFileSync(join(f.receipts,'a'.repeat(64)+'.json'),JSON.stringify({state:'pending'}));
  assert.equal((await diagnoseDesktop(f.config,f.deps)).code,'send_unknown');
  assert.equal(readdirSync(f.receipts).length,1);
});
test('a different task never reaches permission or receipt checks',async t=>{
  const f=fixture(t);f.state.id='another';
  assert.equal((await diagnoseDesktop(f.config,f.deps)).code,'outside_scope');
  assert.deepEqual(f.counts(),{closed:1,reads:1});
});
