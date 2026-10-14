import test from 'node:test';
import assert from 'node:assert/strict';
import {ensureDesktopConversation} from '../src/desktop-open.js';
const id='00000000-1111-2222-3333-444444444444';
const deferred=()=>{let resolve;const promise=new Promise(r=>resolve=r);return {resolve,promise};};
function fixture() {
 let available=false;const calls=[];
 const follower={generation:0,ipc:{ready:async()=>{},request:async(method,params)=>{
  calls.push(method);assert.equal(params.conversationId,id);
  if(!available)throw Object.assign(new Error('no-client-found'),{remote:true});
  return {handledByClientId:'desktop',result:{supportsUntrustedAppInput:true}};
 }}};
 const options={guard:()=>{},refresh:async()=>calls.push('refresh'),verify:async()=>calls.push('verify'),
  open:async(_cfg,_id,guard)=>{guard();calls.push('open');available=true;},delay:async()=>{},attempts:2};
 return {follower,options,calls,available:x=>available=x,run:()=>ensureDesktopConversation({threadId:id},follower,options)};
}
test('existing owner avoids launch and absent owner launches once then re-discovers',async()=>{
 for(const ready of [true,false]){
  const f=fixture();f.available(ready);assert.deepEqual(await f.run(),{opened:!ready});
  assert.equal(f.calls.filter(c=>c==='open').length,ready?0:1);
  assert.ok(f.calls.every(c=>['thread-owner-discovery','refresh','verify','open'].includes(c)));
 }
});
test('IPC failure or unverified owner never opens',async()=>{
 for(const response of [null,{handledByClientId:'x',result:{}}]){
  const f=fixture();f.follower.ipc.request=async()=>{if(response)return response;throw Error('transport timeout');};
  await assert.rejects(f.run());assert.ok(!f.calls.includes('open'));
 }
});
test('guards are rechecked after asynchronous checks and immediately before launch',async()=>{
 for(const stage of ['verify','refresh','launch']){
  const f=fixture();let draining=false;
  f.options.guard=()=>{if(draining)throw Object.assign(Error(),{code:'disconnect_preparing'});};
  if(stage==='launch')f.options.open=async(_cfg,_id,guard)=>{draining=true;guard();f.calls.push('open');};
  else f.options[stage]=async()=>{draining=true;};
  await assert.rejects(f.run(),{code:'disconnect_preparing'});assert.ok(!f.calls.includes('open'));
 }
});
test('Desktop reconnect invalidates pre-open observation',async()=>{
 const f=fixture();f.options.verify=async()=>f.follower.generation++;
 await assert.rejects(f.run(),{code:'desktop_unavailable'});assert.ok(!f.calls.includes('open'));
});
test('launch pending is bounded, with no repeat launch',async()=>{
 const f=fixture();f.options.open=async()=>f.calls.push('open');
 await assert.rejects(f.run(),{code:'desktop_open_pending'});assert.equal(f.calls.filter(c=>c==='open').length,1);
});
