import test from 'node:test';
import assert from 'node:assert/strict';
import { EventEmitter } from 'node:events';
import { tmpdir } from 'node:os';
import { Follower } from '../src/follower.js';

class Ipc extends EventEmitter {
 owner='desktop-1'; calls=[];
 async ready() {}
 async request(method,params,target) { this.calls.push({method,params,target});return {handledByClientId:this.owner,result:{supportsUntrustedAppInput:true}}; }
 broadcast(method,params,targets) {
   if(method==='thread-stream-following-changed') queueMicrotask(()=>this.emit('broadcast',this.message()));
 }
 message() {return {method:'thread-stream-state-changed',version:11,sourceClientId:this.owner,params:{hostId:'local',conversationId:'test',change:{type:'snapshot',revision:1,conversationState:{id:'test',cwd:tmpdir(),turns:[],threadRuntimeStatus:{type:'idle'}}}}};}
 close(){this.emit('disconnected');}
}
test('fresh snapshot and controls target discovered owner; never registers ownership handlers',async()=>{
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);
 assert.equal((await f.snapshot()).id,'test');await f.forward('thread-follower-start-turn',{});
 assert.equal(ipc.calls.at(-1).target,'desktop-1');assert.equal(ipc.handlers,undefined);f.close();
});
test('owner change blocks rediscovery and disconnected state cannot forward',async()=>{
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);await f.snapshot();
 ipc.owner='different-owner';await assert.rejects(f.snapshot(),{code:'owner_changed'});
 ipc.emit('disconnected');await assert.rejects(f.forward('thread-follower-start-turn',{}),{code:'owner_unavailable'});f.close();
});
test('wrong host/owner/thread/path/revision broadcasts never replace valid snapshot',async()=>{
 const ipc=new Ipc(),f=new Follower({threadId:'test',projectPath:tmpdir()},ipc);await f.snapshot();const original=f.state;
 for(const alter of [m=>m.params.hostId='remote',m=>m.sourceClientId='alien',m=>m.params.conversationId='other',m=>m.params.change.conversationState.cwd='Z:/outside',m=>m.params.change.revision=0,m=>m.version=99]){
   const m=ipc.message();alter(m);f.receive(m);assert.equal(f.state,original);
 }
 await assert.rejects(f.forward('thread-follower-update-thread-settings',{}),{code:'unsupported_control'});f.close();
});
