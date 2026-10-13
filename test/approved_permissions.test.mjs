import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { Service, httpServer, permissionDigest, permissionDigests, configuredPermissionsMatch } from '../src/service.js';
import { Receipts } from '../src/receipts.js';
import { checkUpdate } from '../scripts/update_probe.mjs';
import { diagnoseDesktop } from '../scripts/diagnose_desktop.mjs';

function fixture(t) {
 const root=mkdtempSync(join(tmpdir(),'approved-permissions-'));
 t.after(()=>rmSync(root,{recursive:true,force:true}));
 const receipts=new Receipts(join(root,'receipts'));
 const primary={approvalPolicy:'on-request',approvalsReviewer:'auto_review',sandboxPolicy:{type:'workspaceWrite',writableRoots:[root],networkAccess:false,excludeSlashTmp:true,excludeTmpdirEnvVar:true}};
 const alternate=structuredClone(primary);alternate.approvalsReviewer='guardian_subagent';
 alternate.sandboxPolicy.excludeSlashTmp=false;alternate.sandboxPolicy.excludeTmpdirEnvVar=false;
 const state={id:'00000000-1111-2222-3333-444444444444',cwd:root,currentPermissions:primary,
  threadRuntimeStatus:{type:'idle'},requests:[],unconfirmedTurnSubmissions:[],
  turns:[{turnId:'first',status:'completed',items:[{id:'a',type:'agentMessage',text:'PHONE_ALPHA9_OK'}]}]};
 const hash=p=>permissionDigest({currentPermissions:p});
 const config={threadId:state.id,projectPath:root,title:'Synthetic approved settings',dataDir:root,
  permissionsHash:hash(primary),approvedAlternatePermissionsHash:hash(alternate)};
 let forwards=0,snapshots=0;
 const follower={snapshot:async()=>{snapshots++;return state;},completeSnapshot:async validate=>{snapshots++;validate?.(state);return state;},
  forward:async()=>{forwards++;return {result:{turn:{id:'turn-'+forwards}}};},close:()=>{}};
 return {root,primary,alternate,state,config,receipts,follower,counts:()=>({forwards,snapshots})};
}

test('two explicitly approved states alternate across reads, sends, OTA and diagnostics without rewriting configuration',async t=>{
 const f=fixture(t),original=structuredClone(f.config),service=new Service(f.config,f.follower,f.receipts);
 let n=0;
 for(const scope of [f.primary,f.alternate,f.primary,f.alternate]) {
  f.state.currentPermissions=structuredClone(scope);const before=structuredClone(f.state);
  assert.equal((await service.read(f.state.id)).session.id,f.state.id);
  assert.equal((await service.send(f.state.id,{text:'synthetic',clientMessageId:'id-'+n++})).turnId,'turn-'+n);
  assert.equal((await checkUpdate(f.config,f.follower)).permissionsVerified,true);
  const report=await diagnoseDesktop(f.config,{verify:async()=>{},createFollower:()=>f.follower});
  assert.equal(report.taskReady,true);assert.equal(report.checks.permissions.verified,true);
  assert.deepEqual(f.state,before);assert.deepEqual(f.config,original);
 }
 assert.equal(f.counts().forwards,4);
});

test('a third scope, new network access or unapproved directory blocks reads, sends and OTA',async t=>{
 const f=fixture(t),service=new Service(f.config,f.follower,f.receipts);
 const sibling=join(f.root,'other');mkdirSync(sibling);
 const third=structuredClone(f.alternate);third.approvalPolicy='never';
 const network=structuredClone(f.alternate);network.sandboxPolicy.networkAccess=true;
 const directory=structuredClone(f.alternate);directory.sandboxPolicy.writableRoots=[sibling];
 for(const p of [third,network,directory]) {
  f.state.currentPermissions=p;
  await assert.rejects(service.read(f.state.id),{code:'permissions_changed'});
  await assert.rejects(service.send(f.state.id,{text:'never forwarded',clientMessageId:'blocked'}),{code:'permissions_changed'});
  await assert.rejects(checkUpdate(f.config,f.follower),{code:'permissions_changed'});
  const report=await diagnoseDesktop(f.config,{verify:async()=>{},createFollower:()=>f.follower});
  assert.equal(report.taskReady,false);assert.equal(report.checks.permissions.code,'permissions_changed');
 }
 assert.equal(f.counts().forwards,0);
});

for(const [name,value] of [['null',null],['empty',''],['uppercase','A'.repeat(64)],['short','a'],['array',[]],['object',{}],['boolean',true]])
 test('invalid alternate '+name+' is rejected before reading a task',async t=>{
  const f=fixture(t);f.config.approvedAlternatePermissionsHash=value;
  assert.throws(()=>new Service(f.config,f.follower,f.receipts),{code:'permissions_unverified'});
  await assert.rejects(checkUpdate(f.config,f.follower),{code:'permissions_unverified'});
  assert.equal(f.counts().snapshots,0);
 });

test('missing alternate preserves single-digest behavior; duplicate or explicit undefined is invalid',async t=>{
 const f=fixture(t);delete f.config.approvedAlternatePermissionsHash;
 assert.deepEqual(permissionDigests(f.config),[f.config.permissionsHash]);
 assert.equal(configuredPermissionsMatch(f.state,f.config),true);
 f.state.currentPermissions=f.alternate;assert.equal(configuredPermissionsMatch(f.state,f.config),false);
 for(const value of [undefined,f.config.permissionsHash]){
  f.config.approvedAlternatePermissionsHash=value;
  assert.throws(()=>permissionDigests(f.config),{code:'permissions_unverified'});
 }
});

test('alternate cannot be presented as alias-only maintenance proof',async t=>{
 const f=fixture(t);f.state.currentPermissions=f.alternate;
 await assert.rejects(checkUpdate(f.config,f.follower,true),/reviewer_alias_not_proven/);
 assert.equal(f.counts().snapshots,0);assert.equal(f.counts().forwards,0);
});

test('accepted alternate still refuses active tasks and uncertain receipts',async t=>{
 const f=fixture(t);f.state.currentPermissions=f.alternate;
 f.state.threadRuntimeStatus={type:'active'};await assert.rejects(checkUpdate(f.config,f.follower));
 f.state.threadRuntimeStatus={type:'idle'};
 const key=f.receipts.key(f.state.id,'unknown'),hash=f.receipts.digest('unknown');f.receipts.begin(key,hash);
 const before=readFileSync(join(f.root,'receipts',key+'.json'));
 await assert.rejects(checkUpdate(f.config,f.follower),{code:'send_unknown'});
 assert.deepEqual(readFileSync(join(f.root,'receipts',key+'.json')),before);
 assert.equal(f.counts().forwards,0);
});

test('permissions changing after history load are checked again',async t=>{
 const f=fixture(t),service=new Service(f.config,f.follower,f.receipts);
 f.follower.completeSnapshot=async validate=>{validate(f.state);const next=structuredClone(f.state);next.currentPermissions.sandboxPolicy.networkAccess=true;return next;};
 await assert.rejects(service.read(f.state.id),{code:'permissions_changed'});
 await assert.rejects(service.updateStatus(),{code:'permissions_changed'});
 await assert.rejects(service.read('other-task'),{code:'outside_scope'});
 assert.equal(f.counts().forwards,0);
});

test('authenticated mobile-sync shows a desktop reply after a switch to the approved alternate',async t=>{
 const f=fixture(t),service=new Service(f.config,f.follower,f.receipts),token='c'.repeat(64);
 const server=httpServer(service,token,'d'.repeat(32));await new Promise(r=>server.listen(0,'127.0.0.1',r));
 t.after(()=>new Promise(r=>server.close(r)));
 const url='http://127.0.0.1:'+server.address().port+'/api/sessions/'+f.state.id+'/mobile-sync';
 const first=await fetch(url,{headers:{Authorization:'Bearer '+token}});assert.equal(first.status,200);const page=await first.json();
 f.state.currentPermissions=f.alternate;
 f.state.turns.push({turnId:'desktop',status:'completed',items:[{id:'b',type:'agentMessage',text:'DESKTOP_ALPHA9_CONTINUE_OK'}]});
 const response=await fetch(url+'?cursor='+encodeURIComponent(page.cursor),{headers:{Authorization:'Bearer '+token}});
 assert.equal(response.status,200);assert.match(JSON.stringify(await response.json()),/DESKTOP_ALPHA9_CONTINUE_OK/);
 f.state.currentPermissions={...f.alternate,approvalPolicy:'never'};
 assert.equal((await fetch(url,{headers:{Authorization:'Bearer '+token}})).status,409);
 assert.equal(f.counts().forwards,0);
});
