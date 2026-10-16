import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {TaskService} from '../src/tasks.js';
import {Receipts} from '../src/receipts.js';
import {httpServer} from '../src/service.js';
import {taskScope,linkedConversations} from '../src/catalog.js';
const A='00000000-1111-2222-3333-444444444444', B='00000000-1111-2222-3333-555555555555';
function fixture(t) {
 const dir=mkdtempSync(join(tmpdir(),'cdr-auto-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));
 const rows=[{threadId:A,title:'First',projectPath:dir,updatedAt:1}];
 const states=new Map([A,B].map(id=>[id,{id,cwd:dir,requests:[],unconfirmedTurnSubmissions:[],threadRuntimeStatus:{type:'idle'},
 currentPermissions:{sandboxPolicy:{type:'readOnly'},approvalPolicy:'on-request'},turns:[{turnId:'old',status:'completed',items:[]}]}]));
 const calls=[],followers=new Map(),receipts=new Receipts(join(dir,'receipts'));
 const service=new TaskService({taskScope:'all-local',dataDir:dir},receipts,{discover:async()=>rows.slice(),createFollower:cfg=>{
 const f={snapshot:async()=>states.get(cfg.threadId),completeSnapshot:async verify=>{const s=states.get(cfg.threadId);verify?.(s);return s;},
 forward:async(method,params)=>{calls.push({id:cfg.threadId,method,params});return {result:{turn:{id:'new'}}};},close:()=>{f.closed=true;}};
 followers.set(cfg.threadId,f);return f;}});
 t.after(()=>service.close());return {dir,rows,states,calls,followers,receipts,service};
}
test('new conversations appear on next refresh without restart or follower creation',async t=>{
 const f=fixture(t);assert.equal((await f.service.sessions()).length,1);assert.equal(f.followers.size,0);
 f.rows.push({threadId:B,title:'Second',projectPath:f.dir,updatedAt:2});
 assert.equal((await f.service.sessions()).length,2);assert.equal(f.followers.size,0);
 const projects=await f.service.projects();assert.equal(projects.length,2);
 assert.equal((await f.service.read(B)).session.title,'Second');assert.equal(f.followers.size,1);
});
test('send inherits each live Desktop permission without saved hashes or permission overrides',async t=>{
 const f=fixture(t);await f.service.send(A,{text:'one',clientMessageId:'1'});
 f.states.get(A).currentPermissions={sandboxPolicy:{type:'workspaceWrite'},approvalPolicy:'never'};
 await f.service.send(A,{text:'two',clientMessageId:'2'});
 assert.equal(f.calls.length,2);assert.deepEqual(f.calls[1].params.turnStart.context,{inheritThreadSettings:true});
 assert.equal(Object.hasOwn(f.calls[1].params.turnStart.request,'sandboxPolicy'),false);
 delete f.states.get(A).currentPermissions;
 await assert.rejects(f.service.send(A,{text:'three',clientMessageId:'3'}),{code:'permissions_unverified'});
});
test('catalog failure or removal cannot fall back to an already followed task',async t=>{
 const f=fixture(t);await f.service.read(A);f.rows.length=0;
 await assert.rejects(f.service.send(A,{text:'x',clientMessageId:'1'}),{code:'outside_scope'});
 f.service.discover=async()=>{throw new Error('db unavailable');};await assert.rejects(f.service.read(A));assert.equal(f.calls.length,0);
});
test('unavailable historical task does not hide list or claim completed',async t=>{
 const f=fixture(t);const target=await f.service.target(A);target.follower.snapshot=target.follower.completeSnapshot=async()=>{throw new Error('owner unavailable');};
 assert.equal((await f.service.sessions())[0].status,'UNKNOWN');await assert.rejects(f.service.read(A));
});
test('unknown receipt and active task still refuse new messages and OTA',async t=>{
 const f=fixture(t);f.states.get(A).threadRuntimeStatus.type='active';await assert.rejects(f.service.send(A,{text:'x',clientMessageId:'1'}));
 await assert.rejects(f.service.updateStatus());f.states.get(A).threadRuntimeStatus.type='idle';
 f.receipts.begin(f.receipts.key(A,'unknown'),f.receipts.digest('x'));
 await assert.rejects(f.service.send(A,{text:'y',clientMessageId:'2'}),{code:'send_unknown'});
 await assert.rejects(f.service.updateStatus(),{code:'send_unknown'});assert.equal(f.calls.length,0);
});
test('disconnect during discovery cannot create a late follower or send after drain',async t=>{
 const f=fixture(t);let resolve;f.service.discover=()=>new Promise(r=>resolve=r);
 const send=f.service.send(A,{text:'x',clientMessageId:'1'});await f.service.prepareDisconnect();resolve(f.rows);
 await assert.rejects(send,{code:'disconnect_preparing'});assert.equal(f.followers.size,0);assert.equal(f.calls.length,0);
});
test('renames update titles, directory changes are refused and invalid scopes do not expand access',async t=>{
 const f=fixture(t);await f.service.read(A);f.rows[0].title='Renamed';assert.equal((await f.service.read(A)).session.title,'Renamed');
 f.rows[0].projectPath=join(f.dir,'other');await assert.rejects(f.service.read(A),{code:'task_directory_changed'});
 assert.equal(taskScope({}),'selected');assert.throws(()=>taskScope({taskScope:'everything'}));
});
test('HTTP automatic catalog and new task direct navigation use authenticated same-task routing',async t=>{
 const f=fixture(t),token='a'.repeat(64),server=httpServer(f.service,token,'b'.repeat(32));
 await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(()=>new Promise(r=>server.close(r)));
 const url='http://127.0.0.1:'+server.address().port,headers={Authorization:'Bearer '+token};
 assert.equal((await fetch(url+'/api/sessions')).status,401);
 assert.equal((await (await fetch(url+'/api/projects',{headers})).json()).projects.length,1);
 f.rows.push({threadId:B,title:'New',projectPath:f.dir,updatedAt:2});
 const r=await fetch(url+`/api/sessions/${B}/mobile-sync`,{headers});assert.equal(r.status,200);assert.equal((await r.json()).session.id,B);
});

test('list and projects fetch only metadata; opening one never reads unrelated history',async t=>{
 const f=fixture(t);f.rows.push({threadId:B,title:'Second',projectPath:f.dir,updatedAt:2});
 await f.service.sessions();await f.service.projects();assert.equal(f.followers.size,0);
 await f.service.read(A);assert.deepEqual([...f.followers.keys()],[A]);assert.equal(f.calls.length,0);
});
test('empty confirmed history differs from unreadable, active or uncertain history',async t=>{
 const f=fixture(t);f.states.get(A).turns=[];
 await assert.rejects(f.service.read(A),{code:'history_empty'});
 f.states.get(A).threadRuntimeStatus.type='active';await assert.rejects(f.service.read(A),{code:'history_unavailable'});
 f.states.get(A).threadRuntimeStatus.type='idle';f.states.get(A).unconfirmedTurnSubmissions=[{}];
 await assert.rejects(f.service.read(A),{code:'history_unavailable'});
 const service=await f.service.target(A);service.follower.completeSnapshot=async()=>{throw Object.assign(new Error('offline'),{code:'snapshot_unavailable'});};
 await assert.rejects(f.service.read(A),{code:'snapshot_unavailable'});assert.equal((await f.service.sessions()).length,1);
});
test('task removed from metadata releases idle follower and never becomes an empty conversation',async t=>{
 const f=fixture(t);await f.service.read(A);const follower=f.followers.get(A);f.rows.length=0;
 assert.equal((await f.service.sessions()).length,0);assert.equal(follower.closed,true);
 await assert.rejects(f.service.read(A),{code:'outside_scope'});
});

test('titles-only historical tasks do not create active work or block connector update',async t=>{
 const f=fixture(t);await f.service.sessions();assert.equal((await f.service.updateStatus()).idle,true);assert.equal(f.followers.size,0);
});
test('active phone task persists across restart and blocks update until native idle confirmation',async t=>{
 const f=fixture(t);await f.service.send(A,{text:'x',clientMessageId:'1'});
 f.states.get(A).threadRuntimeStatus.type='active';await assert.rejects(f.service.updateStatus(),{code:'thread_busy'});
 assert.equal(f.service.activity.entries().length,1);
 await f.service.read(A);assert.equal(f.service.activity.entries().length,1);
 f.states.get(A).threadRuntimeStatus.type='idle';await f.service.read(A);assert.equal(f.service.activity.entries().length,0);
 assert.equal((await f.service.updateStatus()).idle,true);
});
test('an active task observed on mobile blocks update even when not sent from mobile',async t=>{
 const f=fixture(t);f.states.get(A).threadRuntimeStatus.type='active';await f.service.read(A);
 await assert.rejects(f.service.updateStatus(),{code:'thread_busy'});
});

test('unknown stop remains a durable OTA refusal even after native task goes idle',async t=>{
 const f=fixture(t);f.states.get(A).threadRuntimeStatus.type='active';f.states.get(A).turns[0].status='inProgress';
 const target=await f.service.target(A);target.follower.forward=async()=>{throw new Error('lost ack');};
 await assert.rejects(f.service.stop(A,{expectedTurnId:'old'}),{code:'stop_unknown'});
 assert.equal(f.service.activity.entries()[0].uncertain,true);
 f.states.get(A).threadRuntimeStatus.type='idle';f.states.get(A).turns[0].status='completed';
 await f.service.read(A);await assert.rejects(f.service.updateStatus(),{code:'update_uncertain'});
});

test('quarantine rejects before any durable activity write',async t=>{
 const f=fixture(t);f.service.options.quarantine=true;
 await assert.rejects(f.service.send(A,{text:'x',clientMessageId:'1'}),{code:'update_quarantine'});
 assert.equal(f.service.activity.entries().length,0);assert.equal(f.calls.length,0);
});
test('stop pending exists before IPC and is only acknowledged after success',async t=>{
 const f=fixture(t);f.states.get(A).threadRuntimeStatus.type='active';f.states.get(A).turns[0].status='inProgress';
 const target=await f.service.target(A);target.follower.forward=async()=>{
   assert.equal(f.service.activity.entries()[0].uncertain,true);return {};
 };
 await f.service.stop(A,{expectedTurnId:'old'});assert.equal(f.service.activity.entries()[0].uncertain,false);
});
test('persisted pending stop survives a new TaskService and cannot be erased by idle history',async t=>{
 const f=fixture(t);f.service.activity.beginStop(f.rows[0],'old');
 const restarted=new TaskService({taskScope:'all-local',dataDir:f.dir},f.receipts,{discover:async()=>f.rows,createFollower:f.service.createFollower});
 t.after(()=>restarted.close());await restarted.read(A);
 assert.equal(restarted.activity.entries()[0].uncertain,true);
 await assert.rejects(restarted.updateStatus(),{code:'update_uncertain'});
});
test('activity cannot replace an original task directory binding',async t=>{
 const f=fixture(t);f.service.config.threadId=A;f.service.config.projectPath=f.dir;f.service.config.title='Anchor';f.service.config.permissionsHash='a'.repeat(64);
 f.service.activity.change({...f.rows[0],projectPath:tmpdir()},true);
 await assert.rejects(f.service.updateStatus(),{code:'task_directory_changed'});
});

test('an in-flight history read fences update until its active observation is recorded',async t=>{
 const f=fixture(t),target=await f.service.target(A);let release;
 target.follower.completeSnapshot=()=>new Promise(r=>release=r);
 const reading=f.service.read(A);while(!release)await new Promise(r=>setImmediate(r));
 await assert.rejects(f.service.updateStatus(),{code:'operation_busy'});
 f.states.get(A).threadRuntimeStatus.type='active';release(f.states.get(A));await reading;
 assert.equal(f.service.activity.entries().length,1);
});
test('concurrent idle and active snapshots never use a shared idle flag',async t=>{
 const f=fixture(t),target=await f.service.target(A),pending=[];
 target.follower.completeSnapshot=()=>new Promise(r=>pending.push(r));
 const one=f.service.read(A),two=f.service.read(A);while(pending.length<2)await new Promise(r=>setImmediate(r));
 const idle=structuredClone(f.states.get(A)),active=structuredClone(idle);active.threadRuntimeStatus.type='active';
 target.follower.state=active;pending[0](idle);pending[1](active);await Promise.all([one,two]);
 assert.equal(f.service.activity.entries().length,1);
});


test('restart with no followers cannot drain past unknown durable stop or send',async t=>{
 const f=fixture(t);f.service.activity.beginStop(f.rows[0],'old');
 const restarted=new TaskService({taskScope:'all-local',dataDir:f.dir},f.receipts,{discover:async()=>f.rows,createFollower:f.service.createFollower});
 t.after(()=>restarted.close());assert.equal(restarted.services.size,0);
 await assert.rejects(restarted.prepareDisconnect(),{code:'update_uncertain'});
 const g=fixture(t);g.receipts.begin(g.receipts.key(A,'unknown'),g.receipts.digest('x'));
 await assert.rejects(g.service.prepareDisconnect(),{code:'send_unknown'});
});
test('active snapshot is tracked before pending approval or missing history errors',async t=>{
 const f=fixture(t);f.states.get(A).threadRuntimeStatus.type='active';f.states.get(A).requests=[{}];
 assert.match((await f.service.read(A)).controlNotice,/电脑/);
 assert.equal(f.service.activity.entries().length,1);
 const g=fixture(t);g.states.get(A).threadRuntimeStatus.type='active';g.states.get(A).turns=[];
 await assert.rejects(g.service.read(A),{code:'history_unavailable'});
 assert.equal(g.service.activity.entries().length,1);
});
test('disconnect fences and refuses an in-flight read until its activity is saved',async t=>{
 const f=fixture(t),target=await f.service.target(A);let release;
 f.service.options.drainTimeoutMs=25;
 target.follower.completeSnapshot=()=>new Promise(r=>release=r);
 const reading=f.service.read(A);while(!release)await new Promise(r=>setImmediate(r));
 await assert.rejects(f.service.prepareDisconnect(),{code:'drain_timeout'});
 f.states.get(A).threadRuntimeStatus.type='active';release(f.states.get(A));await reading;
 assert.equal(f.service.activity.entries().length,1);
 assert.deepEqual(await f.service.prepareDisconnect(),{drained:true});
});

test('verified active first snapshot survives failure while loading complete history',async t=>{
 const f=fixture(t),target=await f.service.target(A);f.states.get(A).threadRuntimeStatus.type='active';
 target.follower.completeSnapshot=async verify=>{verify(f.states.get(A));throw Object.assign(new Error('history failed'),{code:'incomplete_history'});};
 await assert.rejects(f.service.read(A),{code:'incomplete_history'});
 assert.equal(f.service.activity.entries().length,1);
});

test('only explicit mobile open requests prepare Desktop, not polling/list/OTA',async t=>{
 const f=fixture(t);let opens=0;f.service.prepareDesktop=async()=>{opens++;};
 await f.service.sessions();await f.service.projects();await f.service.read(A);await f.service.updateStatus();
 assert.equal(opens,0);await f.service.read(A,{allowDesktopOpen:true});assert.equal(opens,1);
});
test('same-task simultaneous opens share one preparation and another task is refused',async t=>{
 const f=fixture(t);f.rows.push({threadId:B,title:'Second',projectPath:f.dir});
 let release;const gate=new Promise(r=>release=r);let entered;const start=new Promise(r=>entered=r);let opens=0;
 f.service.prepareDesktop=async()=>{opens++;entered();await gate;};
 const first=f.service.read(A,{allowDesktopOpen:true});await start;
 const second=f.service.read(A,{allowDesktopOpen:true});
 await assert.rejects(f.service.read(B,{allowDesktopOpen:true}),{code:'operation_busy'});
 await assert.rejects(f.service.updateStatus(),{code:'operation_busy'});
 release();await Promise.all([first,second]);assert.equal(opens,1);
});
test('disconnect during preparation rejects late launch and update/quarantine refuse intent',async t=>{
 const f=fixture(t);let release;const gate=new Promise(r=>release=r);let entered;const start=new Promise(r=>entered=r);let launches=0;
 f.service.options.drainTimeoutMs=25;
 f.service.prepareDesktop=async(_cfg,_fol,{guard})=>{entered();await gate;guard();launches++;};
 const read=f.service.read(A,{allowDesktopOpen:true});await start;
 await assert.rejects(f.service.prepareDisconnect(),{code:'drain_timeout'});release();
 await assert.rejects(read,{code:'disconnect_preparing'});assert.equal(launches,0);
 f.service.draining=false;f.service.options.quarantine=true;
 await assert.rejects(f.service.read(A,{allowDesktopOpen:true}),{code:'update_quarantine'});
});

test('disconnect waits for admitted read and fences phone polling until activity is saved',async t=>{
 const f=fixture(t),target=await f.service.target(A);let release;
 target.follower.completeSnapshot=()=>new Promise(r=>release=r);
 const reading=f.service.read(A);while(!release)await new Promise(r=>setImmediate(r));
 let drained=false;const closing=f.service.prepareDisconnect().then(value=>{drained=true;return value;});
 await assert.rejects(f.service.read(A),{code:'disconnect_preparing'});
 assert.equal(drained,false);
 f.states.get(A).threadRuntimeStatus.type='active';release(f.states.get(A));await reading;
 assert.deepEqual(await closing,{drained:true});assert.equal(f.service.activity.entries().length,1);
 assert.equal(f.service.readDrainWaiters.size,0);assert.equal(f.calls.length,0);
});
test('directory removal before opening rejects and unknown receipts never open',async t=>{
 const f=fixture(t);let launched=0;
 f.service.prepareDesktop=async(_cfg,_fol,{refresh})=>{f.rows.length=0;await refresh();launched++;};
 await assert.rejects(f.service.read(A,{allowDesktopOpen:true}),{code:'outside_scope'});assert.equal(launched,0);
 f.rows.push({threadId:A,title:'First',projectPath:f.dir});f.receipts.begin(f.receipts.key(A,'unknown'),f.receipts.digest('x'));
 await assert.rejects(f.service.read(A,{allowDesktopOpen:true}),{code:'send_unknown'});assert.equal(launched,0);
});
test('HTTP opens only authenticated explicit mobile-sync and rejects ambiguous flags',async t=>{
 const f=fixture(t);let opens=0;f.service.prepareDesktop=async()=>{opens++;};
 const token='a'.repeat(64),server=httpServer(f.service,token,'b'.repeat(32));
 await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(()=>new Promise(r=>server.close(r)));
 const base='http://127.0.0.1:'+server.address().port,headers={Authorization:'Bearer '+token};
 assert.equal((await fetch(base+`/api/sessions/${A}/mobile-sync?open=1`)).status,401);assert.equal(opens,0);
 assert.equal((await fetch(base+`/api/sessions/${A}/mobile-sync`,{headers})).status,200);assert.equal(opens,0);
 for(const path of ['mobile-sync?open=1&open=1','mobile-sync?open=0','mobile-sync?open=1&older=1','signals?open=1'])
  assert.equal((await fetch(base+`/api/sessions/${A}/${path}`,{headers})).status,400);
 assert.equal((await fetch(base+`/api/sessions/${A}/mobile-sync?open=1`,{headers})).status,200);assert.equal(opens,1);
});

test('pending native open persists activity until fresh mobile idle evidence arrives',async t=>{
 const f=fixture(t);
 f.service.prepareDesktop=async(_cfg,_fol,{onOpen})=>{onOpen();throw Object.assign(new Error(),{code:'desktop_open_pending'});};
 await assert.rejects(f.service.read(A,{allowDesktopOpen:true}),{code:'desktop_open_pending'});
 assert.equal(f.service.activity.entries().length,1);assert.equal(f.service.activity.entries()[0].threadId,A);
 f.states.get(A).threadRuntimeStatus.type='active';await assert.rejects(f.service.updateStatus());
 f.states.get(A).threadRuntimeStatus.type='idle';await f.service.read(A);
 assert.equal(f.service.activity.entries().length,0);
});

test('explicit existing conversation admits empty native history without a creation or send receipt',async t=>{
 const f=fixture(t);
 const admission={threadId:A,projectPath:f.dir,title:'Existing',originator:'probe',threadSource:'probe',allowEmptyHistory:true};
 f.service.config.linkedConversations=[admission];f.rows[0].linkedEmptyHistory=true;f.states.get(A).turns=[];
 const result=await f.service.read(A);
 assert.equal(result.session.id,A);assert.equal(result.session.status,'IDLE');assert.deepEqual(result.thread.turns,[]);
 assert.equal(f.receipts.creationRecords().length,0);assert.equal(f.calls.length,0);
 f.states.get(A).threadRuntimeStatus.type='active';
 await assert.rejects(f.service.read(A),{code:'history_unavailable'});
 f.states.get(A).threadRuntimeStatus.type='idle';delete f.states.get(A).currentPermissions;
 await assert.rejects(f.service.read(A),{code:'permissions_unverified'});
});

test('empty-history admission needs both exact local approval and catalog binding and is revoked on refresh',async t=>{
 const f=fixture(t);f.states.get(A).turns=[];f.rows[0].linkedEmptyHistory=true;
 await assert.rejects(f.service.read(A),{code:'history_empty'});
 const row={threadId:A,projectPath:f.dir,title:'Existing',originator:'probe',threadSource:'probe',allowEmptyHistory:true};
 f.service.config.linkedConversations=[row];await f.service.read(A);
 f.service.config.linkedConversations=[{...row,allowEmptyHistory:false}];
 await assert.rejects(f.service.read(A),{code:'history_empty'});
 f.service.config.linkedConversations=[{...row,projectPath:join(f.dir,'different')}];
 await assert.rejects(f.service.read(A),{code:'history_empty'});
 assert.throws(()=>linkedConversations({linkedConversations:[row,row]}),{code:'invalid_task_scope'});
 assert.throws(()=>linkedConversations({linkedConversations:[{...row,threadSource:'subagent'}]}),{code:'invalid_task_scope'});
 assert.equal(f.receipts.creationRecords().length,0);assert.equal(f.calls.length,0);
});
