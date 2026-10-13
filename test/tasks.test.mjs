import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,rmSync,mkdirSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {TaskService,taskConfigs} from '../src/tasks.js';
import {permissionDigest,httpServer} from '../src/service.js';
import {Receipts} from '../src/receipts.js';
import {checkAllTasks,checkUpdate} from '../scripts/update_probe.mjs';
import {diagnoseDesktop} from '../scripts/diagnose_desktop.mjs';
const A='00000000-1111-2222-3333-444444444444',B='00000000-1111-2222-3333-555555555555';
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {promise,resolve};};
function setup(t) {
 const dir=mkdtempSync(join(tmpdir(),'cdr-tasks-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));
 const states=new Map([A,B].map((id,i)=>[id,{id,cwd:dir,threadRuntimeStatus:{type:'idle'},requests:[],unconfirmedTurnSubmissions:[],
  currentPermissions:{sandboxPolicy:{type:i?'workspaceWrite':'readOnly'},approvalPolicy:'on-request'},
  turns:[{turnId:'turn-'+i,status:'completed',items:[{id:'same-item',type:'agentMessage',text:i?'SECOND':'FIRST'}]}]}]));
 const config={schema:1,threadId:A,projectPath:dir,title:'First',permissionsHash:permissionDigest(states.get(A)),dataDir:dir,
  additionalTasks:[{threadId:B,projectPath:dir,title:'Second',permissionsHash:permissionDigest(states.get(B))}]};
 const calls=[],followers=new Map();
 const factory=cfg=>{const f={snapshot:async()=>states.get(cfg.threadId),completeSnapshot:async verify=>{const s=states.get(cfg.threadId);verify?.(s);return s;},
  forward:async(method,params)=>{calls.push({id:cfg.threadId,method,params});return {result:{turn:{id:'ack-'+cfg.threadId}}};},close:()=>{f.closed=true;}};followers.set(cfg.threadId,f);return f;};
 const receipts=new Receipts(join(dir,'receipts')),service=new TaskService(config,receipts,{createFollower:factory});
 t.after(()=>service.close());return {dir,config,states,calls,followers,factory,receipts,service};
}
test('legacy config stays single task and additional task never inherits permission hash',t=>{
 const f=setup(t),legacy={...f.config};delete legacy.additionalTasks;assert.equal(taskConfigs(legacy).length,1);
 legacy.approvedAlternatePermissionsHash='f'.repeat(64);legacy.additionalTasks=f.config.additionalTasks;
 assert.equal(Object.hasOwn(taskConfigs(legacy)[1],'approvedAlternatePermissionsHash'),false);
 delete legacy.additionalTasks[0].permissionsHash;assert.throws(()=>taskConfigs(legacy),{code:'permissions_unverified'});
});
test('reject duplicate IDs, malformed scopes, excessive tasks and runtime overrides',t=>{
 const f=setup(t);
 for(const changes of [{threadId:A},{threadId:'anything'},{node:{path:'other'}},{projectPath:'relative'},{title:''}]) {
  const c=structuredClone(f.config);Object.assign(c.additionalTasks[0],changes);assert.throws(()=>taskConfigs(c));
 }
 for(const value of [null,{},Array(8).fill(f.config.additionalTasks[0])])assert.throws(()=>taskConfigs({...f.config,additionalTasks:value}));
});
test('both tasks are listed with matching project mappings; unavailable task stays unknown',async t=>{
 const f=setup(t);let list=await f.service.sessions();assert.deepEqual(list.map(x=>x.id),[A,B]);
 for(const s of list)assert.ok(f.service.projects().some(p=>p.id===s.project_id));
 f.followers.get(B).completeSnapshot=async()=>{throw new Error('offline');};list=await f.service.sessions();
 assert.equal(list[0].status,'COMPLETED');assert.equal(list[1].status,'UNKNOWN');await assert.rejects(f.service.read(B));
});
test('same message ID across tasks routes separately and replay stays within task',async t=>{
 const f=setup(t),body={text:'test',clientMessageId:'same-id'};
 await f.service.send(A,body);await f.service.send(B,body);await f.service.send(A,body);
 assert.deepEqual(f.calls.map(c=>c.id),[A,B]);assert.equal(f.calls[1].params.turnStart.request.threadId,B);
});
test('wrong permissions cannot borrow primary approval and unlisted task cannot send',async t=>{
 const f=setup(t);f.states.get(B).currentPermissions=f.states.get(A).currentPermissions;
 await assert.rejects(f.service.read(B),{code:'permissions_changed'});
 await assert.rejects(f.service.send(B,{text:'x',clientMessageId:'1'}),{code:'permissions_changed'});
 await assert.rejects(f.service.send('outside',{text:'x',clientMessageId:'1'}),{code:'outside_scope'});assert.equal(f.calls.length,0);
 assert.equal((await f.service.read(A)).session.id,A);
});
test('OTA checks secondary active, approvals, changed scope and uncertain receipts',async t=>{
 const f=setup(t);assert.equal((await f.service.updateStatus()).idle,true);
 f.states.get(B).threadRuntimeStatus.type='active';await assert.rejects(f.service.updateStatus());f.states.get(B).threadRuntimeStatus.type='idle';
 f.states.get(B).requests=[{id:'approval'}];await assert.rejects(f.service.updateStatus());f.states.get(B).requests=[];
 f.receipts.begin(f.receipts.key(B,'unknown'),f.receipts.digest('unknown'));await assert.rejects(f.service.updateStatus(),{code:'send_unknown'});
 assert.equal(f.calls.length,0);
});
test('all-task probe creates and closes both followers and refuses second busy task',async t=>{
 const f=setup(t);f.states.get(B).threadRuntimeStatus.type='active';await assert.rejects(checkAllTasks(f.config,f.factory));
 assert.equal(f.followers.get(A).closed,true);assert.equal(f.followers.get(B).closed,true);
});
test('OTA reserves mutation admission across all task checks',async t=>{
 const f=setup(t),wait=deferred();f.followers.get(A).completeSnapshot=()=>wait.promise;
 const checking=f.service.updateStatus();await assert.rejects(f.service.send(B,{text:'x',clientMessageId:'1'}),{code:'operation_busy'});
 wait.resolve(f.states.get(A));await checking;assert.equal(f.calls.length,0);
});
test('disconnect fences every task before waiting for one in-flight operation',async t=>{
 const f=setup(t),wait=deferred();f.followers.get(A).snapshot=()=>wait.promise;
 const sending=f.service.send(A,{text:'x',clientMessageId:'1'}),draining=f.service.prepareDisconnect();
 await assert.rejects(f.service.send(B,{text:'x',clientMessageId:'2'}),{code:'disconnect_preparing'});
 wait.resolve(f.states.get(A));await sending;assert.deepEqual(await draining,{drained:true});assert.equal(f.calls.length,1);
});
test('unknown receipt in either task blocks clean disconnect without replay',async t=>{
 const f=setup(t);f.receipts.begin(f.receipts.key(B,'unknown'),f.receipts.digest('x'));
 await assert.rejects(f.service.prepareDisconnect(),{code:'send_unknown'});assert.equal(f.calls.length,0);
});
test('HTTP preserves task source, cursor isolation and targeted stop',async t=>{
 const f=setup(t),token='a'.repeat(64),server=httpServer(f.service,token,'b'.repeat(32));
 await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(()=>new Promise(r=>server.close(r)));
 const base='http://127.0.0.1:'+server.address().port,headers={Authorization:'Bearer '+token,'Content-Type':'application/json'};
 const get=async path=>{const r=await fetch(base+path,{headers});assert.equal(r.status,200);return r.json();};
 assert.equal((await fetch(base+'/api/sessions')).status,401);
 assert.equal((await get('/api/sessions')).sessions.length,2);
 const a=await get(`/api/sessions/${A}/mobile-sync`),b=await get(`/api/sessions/${B}/mobile-sync?cursor=${a.cursor}`);
 assert.equal(b.reset,true);assert.equal(b.session.id,B);assert.equal(b.items[0].item.text,'SECOND');assert.notEqual(a.cursor,b.cursor);
 assert.equal((await fetch(base+'/api/sessions/outside/mobile-sync',{headers})).status,403);
 f.states.get(B).threadRuntimeStatus.type='active';f.states.get(B).turns[0].status='inProgress';
 const response=await fetch(base+`/api/sessions/${B}/stop`,{method:'POST',headers,body:JSON.stringify({expectedTurnId:'turn-1'})});
 assert.equal(response.status,200);assert.equal(f.calls.at(-1).id,B);
});

test('diagnostic aggregates secondary permissions and busy state without sending',async t=>{
 const f=setup(t);f.states.get(B).threadRuntimeStatus.type='active';
 const result=await diagnoseDesktop(f.config,{verify:async()=>{},createFollower:f.factory});
 assert.equal(result.taskReady,false);assert.equal(result.tasks.length,2);assert.equal(result.tasks[0].taskReady,true);
 assert.equal(result.tasks[1].checks.idle.verified,false);assert.equal(f.calls.length,0);assert.equal(result.modelInputs,0);
});
test('secondary receipt cannot reuse primary replay and global send admission is serialized',async t=>{
 const f=setup(t),wait=deferred();f.followers.get(A).snapshot=()=>wait.promise;
 const sending=f.service.send(A,{text:'x',clientMessageId:'same'});
 await assert.rejects(f.service.send(B,{text:'x',clientMessageId:'same'}),{code:'operation_busy'});
 wait.resolve(f.states.get(A));await sending;assert.equal(f.calls.length,1);
});

test('single-task and legacy probes refuse a multi-task config instead of ignoring its second task',async t=>{
 const f=setup(t);for(const alias of [false,true])await assert.rejects(checkUpdate(f.config,f.followers.get(A),alias),/all_task_probe_required/);
 assert.equal(f.calls.length,0);
});
