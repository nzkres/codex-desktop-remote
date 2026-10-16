import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { tmpdir } from 'node:os';
import { mkdtempSync, mkdirSync, rmSync } from 'node:fs';
import { join,toNamespacedPath } from 'node:path';
import { Follower } from '../src/follower.js';
import { Service, permissionDigest } from '../src/service.js';
import { Receipts } from '../src/receipts.js';
import { checkUpdate } from '../scripts/update_probe.mjs';

function deferred() {
 let resolve, reject;
 const promise=new Promise((yes,no)=>{resolve=yes;reject=no;});
 return {promise,resolve,reject};
}

class Ipc extends EventEmitter {
 owner='desktop-1'; calls=[]; subscriptions=0; revision=1; auto=true; permissions={sandboxPolicy:{type:'readOnly'},approvalPolicy:'on-request'};
 async ready() {}
 async request(method,params,target) {
   this.calls.push({method,params,target});
   if(target && target!==this.owner) throw new Error('wrong-owner');
   if(method==='thread-follower-start-turn') return {handledByClientId:this.owner,result:{result:{turn:{id:'synthetic-turn'}}}};
   return {handledByClientId:this.owner,result:{supportsUntrustedAppInput:true}};
 }
 broadcast(method,params,targets) {
   this.subscriptions++;
   if(this.auto && method==='thread-stream-following-changed') queueMicrotask(()=>this.emit('broadcast',this.message()));
 }
 message() {return {method:'thread-stream-state-changed',version:11,sourceClientId:this.owner,params:{hostId:'local',conversationId:'test',change:{type:'snapshot',revision:this.revision,conversationState:{id:'test',cwd:tmpdir(),turns:[],threadRuntimeStatus:{type:'idle'},currentPermissions:this.permissions}}}};}
 close(){this.emit('disconnected');}
}

test('Windows namespaced local directories retain native path and permission checks',async t=>{
 const dir=mkdtempSync(join(tmpdir(),'namespace-owner-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));
 const other=join(dir,'other');mkdirSync(other);
 const ipc=new Ipc(),config={threadId:'test',projectPath:toNamespacedPath(dir)};
 const f=new Follower(config,ipc);
 ipc.message=()=>({method:'thread-stream-state-changed',version:11,sourceClientId:ipc.owner,
  params:{hostId:'local',conversationId:'test',change:{type:'snapshot',revision:1,conversationState:{id:'test',cwd:dir,turns:[],threadRuntimeStatus:{type:'idle'},currentPermissions:ipc.permissions}}}});
 assert.equal((await f.snapshot()).id,'test');assert.equal(f.authorized({id:'test',cwd:toNamespacedPath(dir)}),true);
 assert.equal(f.authorized({id:'test',cwd:other}),false);assert.equal(f.authorized({id:'another',cwd:dir}),false);
 const normal=permissionDigest({currentPermissions:{sandboxPolicy:{type:'workspaceWrite',writableRoots:[dir]},approvalPolicy:'on-request'}});
 assert.equal(permissionDigest({currentPermissions:{sandboxPolicy:{type:'workspaceWrite',writableRoots:[toNamespacedPath(dir)]},approvalPolicy:'on-request'}}),normal);
 f.close();
});
test('fresh snapshot and controls target discovered owner; never registers ownership handlers',async()=>{
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);
 assert.equal((await f.snapshot()).id,'test');await f.forward('thread-follower-start-turn',{});
 assert.equal(ipc.calls.at(-1).target,'desktop-1');assert.equal(ipc.handlers,undefined);f.close();
});
test('owner change blocks rediscovery and disconnected state cannot forward',async()=>{
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);await f.snapshot();
 ipc.owner='different-owner';await assert.rejects(f.snapshot(),{code:'owner_changed'});
 await assert.rejects(f.forward('thread-follower-start-turn',{}),{code:'owner_unavailable'});
 ipc.emit('disconnected');await assert.rejects(f.forward('thread-follower-start-turn',{}),{code:'owner_unavailable'});f.close();
});
test('late discovery response after disconnection cannot install an old owner',async()=>{
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);
 const discovery=deferred(),original=ipc.request.bind(ipc);
 let hold=true;
 ipc.request=(method,params,target)=>hold && method==='thread-owner-discovery'
   ? discovery.promise : original(method,params,target);
 const pending=f.snapshot();await new Promise(setImmediate);
 ipc.emit('disconnected');ipc.owner='desktop-2';ipc.revision=0;
 discovery.resolve({handledByClientId:'desktop-1',result:{supportsUntrustedAppInput:true}});
 await assert.rejects(pending,{code:'desktop_unavailable'});
 assert.equal(f.owner,null);assert.equal(f.state,null);assert.equal(ipc.subscriptions,0);
 hold=false;
 assert.equal((await f.snapshot()).id,'test');assert.equal(f.owner,'desktop-2');f.close();
});
test('snapshot wait rejects on disconnection and a later read can revalidate',async()=>{
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);
 ipc.auto=false;const pending=f.snapshot();await new Promise(setImmediate);
 ipc.emit('disconnected');
 await assert.rejects(pending,{code:'desktop_unavailable'});
 assert.equal(f.awaiting,null);assert.equal(f.state,null);
 ipc.owner='desktop-2';ipc.revision=0;ipc.auto=true;
 assert.equal((await f.snapshot()).id,'test');assert.equal(f.owner,'desktop-2');f.close();
});
test('ready completing after a disconnect uses the newly initialized connection',async()=>{
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);
 const ready=deferred();ipc.ready=()=>ready.promise;
 const pending=f.snapshot();await new Promise(setImmediate);
 ipc.emit('disconnected');ipc.owner='desktop-2';ipc.revision=0;
 ready.resolve();
 assert.equal((await pending).id,'test');assert.equal(f.owner,'desktop-2');f.close();
});
test('disconnected Desktop can revalidate a different owner from a lower revision',async()=>{
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);
 const old=await f.snapshot(),delayed=ipc.message();
 ipc.emit('disconnected');
 await assert.rejects(f.forward('thread-follower-start-turn',{},old),{code:'owner_unavailable'});
 ipc.owner='desktop-2';ipc.revision=0;ipc.auto=false;
 const pending=f.snapshot();await new Promise(setImmediate);
 f.receive(delayed);assert.equal(f.state,null);
 const patch=ipc.message();patch.params.change.type='patch';f.receive(patch);assert.equal(f.state,null);
 const wrong=ipc.message();wrong.params.change.conversationState.cwd='Z:/outside';f.receive(wrong);assert.equal(f.state,null);
 const otherTask=ipc.message();otherTask.params.change.conversationState.id='other';f.receive(otherTask);assert.equal(f.state,null);
 const fresh=ipc.message();f.receive(fresh);
 assert.equal(await pending,fresh.params.change.conversationState);
 await assert.rejects(f.forward('thread-follower-start-turn',{},old),{code:'owner_unavailable'});
 await f.forward('thread-follower-start-turn',{},fresh.params.change.conversationState);
 assert.equal(ipc.calls.at(-1).target,'desktop-2');f.close();
});
test('same owner can restart its revision only after IPC disconnection',async()=>{
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);
 await f.snapshot();ipc.revision=0;ipc.auto=false;
 const waiting=f.snapshot();await new Promise(setImmediate);
 const lower=ipc.message();f.receive(lower);assert.equal(f.revision,1);assert.equal(f.state,null);
 ipc.revision=1;f.receive(ipc.message());await waiting;
 ipc.emit('disconnected');assert.equal(f.state,null);
 ipc.revision=0;ipc.auto=true;
 assert.equal((await f.snapshot()).id,'test');assert.equal(f.revision,0);f.close();
});
test('owner-lost notice permits rediscovery but delayed old-owner snapshot stays rejected',async()=>{
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);
 await f.snapshot();const delayed=ipc.message();
 f.receive({method:'client-status-changed',params:{clientId:'desktop-1',status:'disconnected'}});
 ipc.owner='desktop-2';ipc.revision=0;ipc.auto=false;
 const pending=f.snapshot();await new Promise(setImmediate);
 f.receive(delayed);assert.equal(f.state,null);
 f.receive(ipc.message());assert.equal((await pending).id,'test');
 assert.equal(f.owner,'desktop-2');f.close();
});
test('new owner still needs matching permissions and cannot replay an unknown receipt',async t=>{
 const dir=mkdtempSync(join(tmpdir(),'owner-reconnect-test-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);
 const permissionsHash=permissionDigest({currentPermissions:ipc.permissions});
 const receipts=new Receipts(dir),service=new Service({threadId:'test',projectPath:tmpdir(),permissionsHash,title:'test'},f,receipts);
 await f.snapshot();ipc.emit('disconnected');ipc.owner='desktop-2';ipc.revision=0;
 ipc.permissions={sandboxPolicy:{type:'workspaceWrite'},approvalPolicy:'on-request'};
 const message={text:'hello',clientMessageId:'first'};
 await assert.rejects(service.send('test',message),{code:'permissions_changed'});
 assert.equal(ipc.calls.filter(c=>c.method==='thread-follower-start-turn').length,0);
 ipc.permissions={sandboxPolicy:{type:'readOnly'},approvalPolicy:'on-request'};
 assert.equal((await service.send('test',message)).turnId,'synthetic-turn');
 assert.equal(ipc.calls.at(-1).target,'desktop-2');
 receipts.begin(receipts.key('test','uncertain'),receipts.digest('sent before disconnect'));
 await assert.rejects(service.send('test',{...message,clientMessageId:'replacement'}),{code:'send_unknown'});
 assert.equal(ipc.calls.filter(c=>c.method==='thread-follower-start-turn').length,1);f.close();
});
test('changed permissions block complete-history forwarding before a Desktop request',async t=>{
 const dir=mkdtempSync(join(tmpdir(),'owner-history-test-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);
 const permissionsHash=permissionDigest({currentPermissions:ipc.permissions});
 const service=new Service({threadId:'test',projectPath:tmpdir(),permissionsHash,title:'test'},f,new Receipts(dir));
 ipc.emit('disconnected');ipc.owner='desktop-2';ipc.revision=0;
 ipc.permissions={sandboxPolicy:{type:'workspaceWrite'},approvalPolicy:'on-request'};
 const message=ipc.message.bind(ipc);
 ipc.message=()=>{const m=message();m.params.change.conversationState.turnHistory={kind:'canonical',history:{isComplete:false,islands:[],entitiesByKey:{}}};return m;};
 await assert.rejects(service.read('test'),{code:'permissions_changed'});
 assert.equal(ipc.calls.filter(c=>c.method==='thread-follower-load-complete-history').length,0);f.close();
});
test('alias-only OTA probe checks full permissions before loading incomplete history',async t=>{
 const dataDir=mkdtempSync(join(tmpdir(),'owner-update-probe-test-'));t.after(()=>rmSync(dataDir,{recursive:true,force:true}));
 mkdirSync(join(dataDir,'receipts'));
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);
 const expected={approvalPolicy:'on-request',approvalsReviewer:'guardian_subagent',sandboxPolicy:{type:'readOnly'}};
 const config={threadId:'test',projectPath:tmpdir(),dataDir,
  permissionsHash:permissionDigest({currentPermissions:expected})};
 let incomplete=true;
 const message=ipc.message.bind(ipc);
 ipc.message=()=>{
  const m=message(),state=m.params.change.conversationState;
  state.requests=[];state.unconfirmedTurnSubmissions=[];
  state.turnHistory={kind:'canonical',history:{isComplete:!incomplete,islands:[],entitiesByKey:{}}};
  return m;
 };
 ipc.permissions={...expected,approvalsReviewer:'auto_review',sandboxPolicy:{type:'workspaceWrite'}};
 await assert.rejects(checkUpdate(config,f,true),/permissions_unverified/);
 assert.equal(ipc.calls.filter(c=>c.method==='thread-follower-load-complete-history').length,0);
 ipc.permissions={...expected,approvalsReviewer:'auto_review'};
 const request=ipc.request.bind(ipc);
 ipc.request=(method,params,target)=>{
  if(method==='thread-follower-load-complete-history') incomplete=false;
  return request(method,params,target);
 };
 assert.equal((await checkUpdate(config,f,true)).reviewerAliasOnly,true);
 assert.equal(ipc.calls.filter(c=>c.method==='thread-follower-load-complete-history').length,1);
 f.close();
});
for(const change of ['new snapshot','IPC disconnect'])
 test(`${change} between Service permission check and forward leaves an unknown receipt without sending`,async t=>{
  const dir=mkdtempSync(join(tmpdir(),'owner-send-race-test-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));
  const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);
  const receipts=new Receipts(dir),permissionsHash=permissionDigest({currentPermissions:ipc.permissions});
  const service=new Service({threadId:'test',projectPath:tmpdir(),permissionsHash,title:'test'},f,receipts);
  const permissions=service.permissions.bind(service);let inject=true;
  service.permissions=state=>{
   permissions(state);
   if(!inject) return;
   inject=false;
   if(change==='IPC disconnect') ipc.emit('disconnected');
   else {ipc.revision=2;ipc.emit('broadcast',ipc.message());}
  };
  const first={text:'not sent',clientMessageId:'first'};
  await assert.rejects(service.send('test',first),{code:'send_unknown'});
  assert.equal(ipc.calls.filter(c=>c.method==='thread-follower-start-turn').length,0);
  await assert.rejects(service.send('test',{text:'retry',clientMessageId:'replacement'}),{code:'send_unknown'});
  assert.equal(ipc.calls.filter(c=>c.method==='thread-follower-start-turn').length,0);
  f.close();
 });
test('wrong host/owner/thread/path/revision broadcasts never replace valid snapshot',async()=>{
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);await f.snapshot();const original=f.state;
 for(const alter of [m=>m.params.hostId='remote',m=>m.sourceClientId='alien',m=>m.params.conversationId='other',m=>m.params.change.conversationState.cwd='Z:/outside',m=>m.params.change.revision=0,m=>m.version=99]){
   const m=ipc.message();alter(m);f.receive(m);assert.equal(f.state,original);
 }
 ipc.revision=2;f.receive(ipc.message());
 await assert.rejects(f.forward('thread-follower-start-turn',{},original),{code:'owner_unavailable'});
 await assert.rejects(f.forward('thread-follower-update-thread-settings',{}),{code:'unsupported_control'});f.close();
});
