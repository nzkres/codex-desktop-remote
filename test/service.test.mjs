import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, mkdirSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { randomBytes } from 'node:crypto';
import { Service, httpServer, permissionDigest } from '../src/service.js';
import { Receipts } from '../src/receipts.js';

function fixture(t) {
 const dir=mkdtempSync(join(tmpdir(),'desktop-lite-test-'));
 t.after(()=>rmSync(dir,{recursive:true,force:true}));
 const id='00000000-1111-2222-3333-444444444444';
 const state={id,cwd:dir,threadRuntimeStatus:{type:'idle'},currentPermissions:{sandboxPolicy:{type:'readOnly'},approvalPolicy:'on-request'},
 requests:[],unconfirmedTurnSubmissions:[],turns:[{turnId:'first-turn',status:'completed',items:[{id:'a',type:'userMessage',content:[{type:'text',text:'hello'}]}, {id:'b',type:'agentMessage',text:'ready'}]}]};
 const calls=[];
 const follower={snapshot:async()=>state,completeSnapshot:async()=>state,forward:async(method,params)=>{calls.push({method,params});return {result:{turn:{id:'next-turn'}}};}};
 const config={threadId:id,projectPath:dir,title:'Synthetic test',permissionsHash:permissionDigest(state)};
 const receipts=new Receipts(dir);
 return {dir,id,state,calls,follower,config,receipts,service:new Service(config,follower,receipts)};
}
const message={text:'Only reply TEST_OK',clientMessageId:'message-1'};
function deferred() {
 let resolve, reject;
 const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});
 return {promise,resolve,reject};
}
test('APP HTTP contract: auth, same native thread, bounded sync and acknowledged POST',async t=>{
 const f=fixture(t),token=randomBytes(32).toString('hex');
 const server=httpServer(f.service,token,'1'.repeat(32));
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 t.after(()=>new Promise(r=>server.close(r)));
 const base='http://127.0.0.1:'+server.address().port;
 assert.equal((await fetch(base+'/api/sessions')).status,401);
 const get=async p=>{const r=await fetch(base+p,{headers:{Authorization:'Bearer '+token}});assert.equal(r.status,200);return r.json();};
 assert.equal((await get('/api/sessions')).sessions[0].id,f.id);
 const page=await get(`/api/sessions/${f.id}/mobile-sync`);
 assert.equal(page.protocol,1);assert.equal(page.items[1].item.text,'ready');assert.equal(page.session.id,f.id);
 assert.equal((await get(`/api/sessions/${f.id}/mobile-sync?cursor=${page.cursor}`)).unchanged,true);
 const r=await fetch(`${base}/api/sessions/${f.id}/messages`,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(message)});
 assert.equal(r.status,200);assert.equal((await r.json()).result.turnId,'next-turn');
 assert.deepEqual(f.calls[0].params.turnStart,{request:{threadId:f.id,input:[{type:'text',text:message.text,text_elements:[]}],clientUserMessageId:message.clientMessageId},context:{inheritThreadSettings:true}});
});
test('known receipt survives restart and never starts another turn',async t=>{
 const f=fixture(t);await f.service.send(f.id,message);
 const again=new Service(f.config,f.follower,new Receipts(f.dir));
 assert.equal((await again.send(f.id,message)).replayed,true);assert.equal(f.calls.length,1);
 await assert.rejects(again.send(f.id,{...message,text:'changed'}),{code:'message_conflict'});
});
test('response loss and restart retain unknown without replay',async t=>{
 const f=fixture(t);f.follower.forward=async()=>{f.calls.push('sent');throw new Error('connection_lost');};
 await assert.rejects(f.service.send(f.id,message),{code:'send_unknown'});
 const again=new Service(f.config,f.follower,new Receipts(f.dir));
 await assert.rejects(again.send(f.id,message),{code:'send_unknown'});assert.equal(f.calls.length,1);
});
test('unknown send freezes the task even if the APP supplies a new message ID',async t=>{
 const f=fixture(t);f.follower.forward=async()=>{f.calls.push('sent');throw new Error('lost_ack');};
 await assert.rejects(f.service.send(f.id,message),{code:'send_unknown'});
 const again=new Service(f.config,f.follower,new Receipts(f.dir));
 await assert.rejects(again.send(f.id,{...message,clientMessageId:'replacement-id'}),{code:'send_unknown'});
 await assert.rejects(again.send(f.id,{text:'next task',clientMessageId:'next-id'}),{code:'send_unknown'});
 assert.equal(f.calls.length,1);
});
for(const [name,alter] of [
 ['active',s=>s.threadRuntimeStatus={type:'active'}],['pending approval',s=>s.requests=[{method:'item/fileChange/requestApproval'}]],
 ['unconfirmed turn',s=>s.unconfirmedTurnSubmissions=[{}]],['unknown status',s=>delete s.threadRuntimeStatus],
 ['permissions changed',s=>s.currentPermissions.sandboxPolicy.type='workspaceWrite'],['missing permissions',s=>delete s.currentPermissions],
])test(`blocks ${name} without sending`,async t=>{const f=fixture(t);alter(f.state);await assert.rejects(f.service.send(f.id,message));assert.equal(f.calls.length,0);});
test('wrong thread and caller supplied permission overrides fail closed',async t=>{
 const f=fixture(t);await assert.rejects(f.service.send('other-thread',message),{code:'outside_scope'});
 await assert.rejects(f.service.send(f.id,{...message,sandboxPolicy:{type:'dangerFullAccess'}}),{code:'invalid_message'});
 assert.equal(f.calls.length,0);
});
test('stop matches the current native turn only',async t=>{
 const f=fixture(t);f.state.turns.push({turnId:'running-turn',status:'inProgress',items:[]});
 await assert.rejects(f.service.stop(f.id,{expectedTurnId:'first-turn'}),{code:'stale_turn'});assert.equal(f.calls.length,0);
 await f.service.stop(f.id,{expectedTurnId:'running-turn'});
 assert.deepEqual(f.calls[0],{method:'thread-follower-interrupt-turn',params:{mode:'user-stop',expectedTurnId:'running-turn'}});
});
test('concurrent sends have one admission',async t=>{
 const f=fixture(t);let unblock;f.follower.snapshot=()=>new Promise(r=>{unblock=()=>r(f.state);});
 const first=f.service.send(f.id,message);await assert.rejects(f.service.send(f.id,{...message,clientMessageId:'second'}),{code:'operation_busy'});
 unblock();await first;assert.equal(f.calls.length,1);
});
test('a covered cwd does not enlarge permission scope, but an external directory does',t=>{
 const f=fixture(t), child=join(f.dir,'child');mkdirSync(child);
 f.state.currentPermissions.sandboxPolicy={type:'workspaceWrite',networkAccess:false,writableRoots:[f.dir]};
 const before=permissionDigest(f.state);
 f.state.currentPermissions.sandboxPolicy.writableRoots=[child,f.dir];assert.equal(permissionDigest(f.state),before);
 f.state.currentPermissions.sandboxPolicy.writableRoots=[f.dir,tmpdir()];assert.notEqual(permissionDigest(f.state),before);
 f.state.currentPermissions.sandboxPolicy.writableRoots=[f.dir];f.state.currentPermissions.sandboxPolicy.networkAccess=true;assert.notEqual(permissionDigest(f.state),before);
});
test('unknown live state and pending Desktop action never claim completion',async t=>{
 const f=fixture(t);delete f.state.threadRuntimeStatus;await assert.rejects(f.service.read(f.id),{code:'runtime_status_unknown'});
 f.state.threadRuntimeStatus={type:'idle'};f.state.requests=[{method:'item/tool/requestUserInput'}];
 await assert.rejects(f.service.read(f.id),{code:'desktop_action_required'});
});
test('authenticated prepare-disconnect is repeatable and permanently fences new controls',async t=>{
 const f=fixture(t),token=randomBytes(32).toString('hex'),controlToken=randomBytes(32).toString('hex'),instance='2'.repeat(32);
 const server=httpServer(f.service,token,instance,controlToken);
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 t.after(()=>new Promise(r=>server.close(r)));
 const url=`http://127.0.0.1:${server.address().port}/api/connector/prepare-disconnect`;
 assert.equal((await fetch(url,{method:'POST'})).status,401);
 assert.equal((await fetch(url,{method:'POST',headers:{Authorization:'Bearer '+'0'.repeat(64)}})).status,401);
 assert.equal((await fetch(url,{method:'POST',headers:{Authorization:'Bearer '+token}})).status,401);
 assert.equal((await fetch(`http://127.0.0.1:${server.address().port}/api/sessions`,{headers:{Authorization:'Bearer '+controlToken}})).status,401);
 assert.equal(f.service.draining,false);
 const post=()=>fetch(url,{method:'POST',headers:{Authorization:'Bearer '+controlToken}});
 for(let i=0;i<2;i++){
  const response=await post();assert.equal(response.status,200);
  assert.deepEqual(await response.json(),{instance,drained:true});
 }
 await assert.rejects(f.service.send(f.id,message),{code:'disconnect_preparing'});
 f.state.turns.push({turnId:'running',status:'inProgress',items:[]});
 await assert.rejects(f.service.stop(f.id,{expectedTurnId:'running'}),{code:'disconnect_preparing'});
 assert.equal(f.calls.length,0);
});
test('prepare-disconnect is unavailable without a distinct control token',async t=>{
 const f=fixture(t),token=randomBytes(32).toString('hex');
 assert.throws(()=>httpServer(f.service,token,'2'.repeat(32),token),/invalid_control_token/);
 const server=httpServer(f.service,token,'2'.repeat(32));
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 t.after(()=>new Promise(r=>server.close(r)));
 const base=`http://127.0.0.1:${server.address().port}`;
 const response=await fetch(base+'/api/connector/prepare-disconnect',{
  method:'POST',headers:{Authorization:'Bearer '+token},
 });
 assert.equal(response.status,404);
 assert.equal(f.service.draining,false);
 assert.equal((await fetch(base+'/api/sessions',{headers:{Authorization:'Bearer '+token}})).status,200);
});
test('prepare-disconnect waits for an admitted send, then checks its receipt',async t=>{
 const f=fixture(t),entered=deferred(),release=deferred();
 f.follower.forward=async(method,params)=>{f.calls.push({method,params});entered.resolve();return release.promise;};
 const sending=f.service.send(f.id,message);await entered.promise;
 const draining=f.service.prepareDisconnect();
 await assert.rejects(f.service.send(f.id,{...message,clientMessageId:'later'}),{code:'disconnect_preparing'});
 release.resolve({result:{turn:{id:'accepted-turn'}}});
 assert.deepEqual(await sending,{turnId:'accepted-turn'});
 assert.deepEqual(await draining,{drained:true});
 assert.equal(f.calls.length,1);
});
test('prepare-disconnect waits for an admitted stop and refuses an unknown stop result',async t=>{
 const f=fixture(t),entered=deferred(),release=deferred();
 f.state.turns.push({turnId:'running',status:'inProgress',items:[]});
 f.follower.forward=async(method,params)=>{f.calls.push({method,params});entered.resolve();return release.promise;};
 const stopping=f.service.stop(f.id,{expectedTurnId:'running'});await entered.promise;
 const draining=f.service.prepareDisconnect();
 await assert.rejects(f.service.stop(f.id,{expectedTurnId:'running'}),{code:'disconnect_preparing'});
 release.reject(new Error('synthetic lost reply'));
 await assert.rejects(stopping,{code:'stop_unknown'});
 await assert.rejects(draining,{code:'drain_uncertain'});
 await assert.rejects(f.service.prepareDisconnect(),{code:'drain_uncertain'});
 assert.equal(f.calls.length,1);
});
test('prepare-disconnect waits for an admitted stop that receives a confirmed reply',async t=>{
 const f=fixture(t),entered=deferred(),release=deferred();
 f.state.turns.push({turnId:'running',status:'inProgress',items:[]});
 f.follower.forward=async(method,params)=>{f.calls.push({method,params});entered.resolve();return release.promise;};
 const stopping=f.service.stop(f.id,{expectedTurnId:'running'});await entered.promise;
 const draining=f.service.prepareDisconnect();
 release.resolve({ok:true});
 assert.deepEqual(await stopping,{ok:true});
 assert.deepEqual(await draining,{drained:true});
 assert.equal(f.calls.length,1);
});
test('a previously unknown stop independently blocks a later drain',async t=>{
 const f=fixture(t);
 f.state.turns.push({turnId:'running',status:'inProgress',items:[]});
 f.follower.forward=async()=>{f.calls.push('stop-sent');throw new Error('synthetic lost reply');};
 await assert.rejects(f.service.stop(f.id,{expectedTurnId:'running'}),{code:'stop_unknown'});
 await assert.rejects(f.service.prepareDisconnect(),{code:'drain_uncertain'});
 assert.equal(f.calls.length,1);
});
test('prepare-disconnect refuses an unknown send even with a new message ID',async t=>{
 const f=fixture(t);f.follower.forward=async()=>{f.calls.push('sent');throw new Error('synthetic lost reply');};
 await assert.rejects(f.service.send(f.id,message),{code:'send_unknown'});
 await assert.rejects(f.service.prepareDisconnect(),{code:'send_unknown'});
 await assert.rejects(f.service.send(f.id,{...message,clientMessageId:'later'}),{code:'disconnect_preparing'});
 assert.equal(f.calls.length,1);
});
test('drain timeout remains fenced and can only succeed after the admitted send settles',async t=>{
 const f=fixture(t),entered=deferred(),release=deferred();
 const service=new Service(f.config,f.follower,f.receipts,{drainTimeoutMs:10});
 f.follower.forward=async()=>{f.calls.push('sent');entered.resolve();return release.promise;};
 const sending=service.send(f.id,message);await entered.promise;
 await assert.rejects(service.prepareDisconnect(),{code:'drain_timeout'});
 await assert.rejects(service.send(f.id,{...message,clientMessageId:'later'}),{code:'disconnect_preparing'});
 release.resolve({result:{turn:{id:'accepted-turn'}}});
 await sending;
 assert.deepEqual(await service.prepareDisconnect(),{drained:true});
 assert.equal(f.calls.length,1);
});
