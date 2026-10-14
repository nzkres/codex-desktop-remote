import test from 'node:test';
import assert from 'node:assert/strict';
import {tmpdir} from 'node:os';
import {probeDesktopOpen} from '../scripts/desktop_open_probe.mjs';
const id='00000000-0000-0000-0000-000000000001';
function fixture() {
 const row={threadId:id,projectPath:tmpdir(),title:'Synthetic'};
 const state={id,cwd:tmpdir(),threadRuntimeStatus:{type:'idle'},currentPermissions:{sandboxPolicy:{type:'readOnly'},approvalPolicy:'on-request'},requests:[],unconfirmedTurnSubmissions:[],turns:[]};
 const counts={opened:0,closed:0,requests:[],snapshots:0};
 let hasOwner=false;
 const follower={ipc:{ready:async()=>{},request:async(method,params)=>{
   counts.requests.push({method,params});
   if(!hasOwner) throw Object.assign(new Error('no-client-found'),{remote:true});
   return {handledByClientId:'native-owner',result:{supportsUntrustedAppInput:true}};
 }},authorized:value=>value.id===id&&value.cwd===tmpdir(),snapshot:async()=>{
   counts.snapshots++;return state;
 },close:()=>counts.closed++};
 const options={verify:async()=>{},discover:async()=>[row],createFollower:()=>follower,delay:async()=>{},
  open:async(cfg,target)=>{assert.equal(target,id);counts.opened++;hasOwner=true;}};
 return {row,state,counts,follower,options,owner:value=>hasOwner=value,config:{taskScope:'all-local'}};
}
test('default probe stays read-only when owner is absent',async()=>{
 const f=fixture(),r=await probeDesktopOpen(f.config,id,f.options);
 assert.equal(r.code,'owner_unavailable');assert.equal(f.counts.opened,0);assert.equal(f.counts.closed,1);
});
test('explicit open discovers same native task without model inputs',async()=>{
 const f=fixture(),r=await probeDesktopOpen(f.config,id,{...f.options,allowOpen:true});
 assert.equal(r.code,'verified');assert.equal(r.sameTask,true);assert.equal(r.opened,true);assert.equal(r.modelInputs,0);
 assert.equal(f.counts.opened,1);assert.equal(f.counts.closed,1);
 assert.ok(f.counts.requests.every(r=>r.method==='thread-owner-discovery'&&r.params.conversationId===id&&r.params.hostId==='local'));
});
test('an already available owner is read without navigation',async()=>{
 const f=fixture();f.owner(true);
 const r=await probeDesktopOpen(f.config,id,{...f.options,allowOpen:true});
 assert.equal(r.code,'verified');assert.equal(f.counts.opened,0);
});
test('IPC timeout never triggers navigation',async()=>{
 const f=fixture();f.follower.ipc.request=async()=>{throw new Error('private timeout detail');};
 const r=await probeDesktopOpen(f.config,id,{...f.options,allowOpen:true});
 assert.equal(r.code,'desktop_open_probe_failed');assert.equal(f.counts.opened,0);assert.ok(!JSON.stringify(r).includes('private'));
});
test('only explicit no-client-found means owner absent',async()=>{
 for(const remote of [false,undefined]) {
  const f=fixture();f.follower.ipc.request=async()=>{throw Object.assign(new Error('no-client-found'),{remote});};
  await probeDesktopOpen(f.config,id,{...f.options,allowOpen:true});assert.equal(f.counts.opened,0);
 }
});
test('changed catalog path and invalid ID never launch',async()=>{
 const f=fixture();let n=0;f.options.discover=async()=>[{...f.row,projectPath:++n===1?tmpdir():'C:\\changed'}];
 assert.equal((await probeDesktopOpen(f.config,id,{...f.options,allowOpen:true})).code,'task_directory_changed');
 assert.equal((await probeDesktopOpen(f.config,id+'?prompt=x',{...f.options,allowOpen:true})).code,'outside_scope');
 assert.equal(f.counts.opened,0);
});
test('compatibility rejection and out-of-scope task cannot launch',async()=>{
 for(const options of [{verify:async()=>{throw Object.assign(new Error(),{code:'hash_mismatch'});}},{discover:async()=>[]}]) {
  const f=fixture();const r=await probeDesktopOpen(f.config,id,{...f.options,...options,allowOpen:true});
  assert.notEqual(r.code,'verified');assert.equal(f.counts.opened,0);
 }
});
test('opening URI alone is not success and does not repeatedly launch',async()=>{
 const f=fixture();f.options.open=async()=>{f.counts.opened++;};
 const r=await probeDesktopOpen(f.config,id,{...f.options,allowOpen:true,attempts:3});
 assert.equal(r.code,'desktop_open_pending');assert.equal(r.historyReadable,false);assert.equal(f.counts.opened,1);
});
test('wrong returned task and invalid permissions never count as readable',async()=>{
 for(const mutate of [s=>s.id='wrong',s=>s.currentPermissions=null]) {
  const f=fixture();mutate(f.state);
  const r=await probeDesktopOpen(f.config,id,{...f.options,allowOpen:true});
  assert.notEqual(r.code,'verified');assert.equal(r.historyReadable,false);assert.equal(f.counts.closed,1);
 }
});

test('unknown or incomplete history/runtime is not verified',async()=>{
 for(const mutate of [s=>delete s.turns,s=>s.threadRuntimeStatus={type:'unknown'},s=>s.turnHistory={kind:'canonical',history:{isComplete:false}}]) {
  const f=fixture();f.owner(true);mutate(f.state);
  const r=await probeDesktopOpen(f.config,id,f.options);
  assert.notEqual(r.code,'verified');assert.equal(r.historyReadable,false);assert.equal(f.counts.opened,0);
 }
});
