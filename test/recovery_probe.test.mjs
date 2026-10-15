import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,mkdirSync,writeFileSync,readFileSync,rmSync} from 'node:fs';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
import {recoveryProof} from '../scripts/recovery_probe.mjs';
test('recovery refuses unknown send and stop receipts before consulting Desktop',async t=>{
  const dataDir=mkdtempSync(join(tmpdir(),'recovery-'));t.after(()=>rmSync(dataDir,{recursive:true,force:true}));
  const receipts=join(dataDir,'receipts');mkdirSync(receipts);
  let checks=0;const deps={verify:async()=>{},check:async()=>{checks++;return {tasksVerified:true,permissionsVerified:true,receiptsCertain:true,runningTaskCount:0};}};
  const file=join(receipts,'a'.repeat(64)+'.json');
  writeFileSync(file,JSON.stringify({schema:1,state:'pending'}));
  const before=readFileSync(file);
  await assert.rejects(recoveryProof({dataDir},deps));assert.equal(checks,0);assert.deepEqual(readFileSync(file),before);
  writeFileSync(file,JSON.stringify({schema:1,state:'accepted',turnId:'synthetic'}));
  writeFileSync(join(dataDir,'active-tasks.json'),JSON.stringify({schema:1,tasks:[{threadId:'11111111-1111-1111-1111-111111111111',projectPath:dataDir,title:'synthetic',uncertain:true}]}));
  await assert.rejects(recoveryProof({dataDir},deps));assert.equal(checks,0);
});
test('fresh complete recovery proof is required without changing receipt bytes',async t=>{
  const dataDir=mkdtempSync(join(tmpdir(),'recovery-'));t.after(()=>rmSync(dataDir,{recursive:true,force:true}));
  mkdirSync(join(dataDir,'receipts'));
  const deps={verify:async()=>{},check:async()=>({tasksVerified:false,permissionsVerified:true,receiptsCertain:true,runningTaskCount:0})};
  await assert.rejects(recoveryProof({dataDir},deps),/recovery_task_unverified/);
  deps.check=async()=>({tasksVerified:true,permissionsVerified:true,receiptsCertain:true,runningTaskCount:1});
  assert.equal((await recoveryProof({dataDir},deps)).modelInputs,0);
});
