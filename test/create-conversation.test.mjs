import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {TaskService} from '../src/tasks.js';
import {Receipts} from '../src/receipts.js';
import {httpServer} from '../src/service.js';
const A='00000000-1111-2222-3333-444444444444',N='00000000-1111-2222-3333-555555555555';
function setup(t) {
 const dir=mkdtempSync(join(tmpdir(),'cdr-create-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));
 const config={taskScope:'all-local',dataDir:dir};
 const receipts=new Receipts(join(dir,'receipts'));let creates=0,opens=0,inputs=0;
 const state={id:N,cwd:dir,threadRuntimeStatus:{type:'idle'},requests:[],unconfirmedTurnSubmissions:[],
  currentPermissions:{sandboxPolicy:{type:'readOnly'},approvalPolicy:'on-request'},turns:[]};
 const options={verifyCreate:async()=>{},discover:async cfg=>[
  {threadId:A,projectPath:dir,title:'Existing',updatedAt:1},
  ...(cfg.createdThreadIds?.includes(N)?[{threadId:N,projectPath:dir,title:'New',updatedAt:2}]:[])],
  createFollower:cfg=>({ipc:{ready:async()=>{}},completeSnapshot:async()=>({...state,id:cfg.threadId}),snapshot:async()=>({...state,id:cfg.threadId}),
   forward:async()=>{inputs++;return {result:{turn:{id:'real-ack'}}};},close:()=>{}}),
  createEmpty:async(cfg,path,title,onId)=>{creates++;onId(N,false);onId(N,true);return N;},
  prepareDesktop:async()=>{opens++;}};
 const service=new TaskService(config,receipts,options);t.after(()=>service.close());
 return {dir,config,receipts,state,options,service,counts:()=>({creates,opens,inputs})};
}
const body={title:'New',createRequestId:'stable-request'};
async function project(f){return (await f.service.creationProjects())[0].id;}
test('create, durable receipt, native empty read and first send reuse existing chain; duplicate creates once',async t=>{
 const f=setup(t),pid=await project(f);
 const result=await f.service.create(pid,body);assert.equal(result.session.id,N);
 assert.equal((await f.service.create(pid,body)).replayed,true);
 assert.deepEqual(f.counts(),{creates:1,opens:1,inputs:0});
 f.receipts.assertCertain();
 const mobile=await f.service.read(N);assert.equal(mobile.session.status,'IDLE');assert.equal(mobile.thread.turns.length,0);
 assert.equal((await f.service.creationProjects()).length,1);
 const ack=await f.service.send(N,{clientMessageId:'first-message',text:'synthetic'});
 assert.equal(ack.turnId,'real-ack');await f.service.send(N,{clientMessageId:'first-message',text:'synthetic'});
 assert.equal(f.counts().inputs,1);
});
test('lost HTTP acknowledgement and process restart return original ID, content conflict never creates',async t=>{
 const f=setup(t),pid=await project(f);await f.service.create(pid,body);f.service.close();
 const next=new TaskService(f.config,new Receipts(f.receipts.directory),f.options);t.after(()=>next.close());
 assert.equal((await next.create(pid,body)).session.id,N);
 await assert.rejects(next.create(pid,{...body,title:'Changed'}),{code:'create_conflict'});
 assert.equal(f.counts().creates,1);
});
test('unknown creation survives restart, prevents re-create, sending, OTA and clean disconnect',async t=>{
 const f=setup(t),pid=await project(f);f.service.createEmpty=async()=>{throw new Error('lost-native-ack');};
 await assert.rejects(f.service.create(pid,body));
 const next=new TaskService(f.config,new Receipts(f.receipts.directory),f.options);t.after(()=>next.close());
 await assert.rejects(next.create(pid,body),{code:'create_unknown'});
 await assert.rejects(next.create(pid,{...body,createRequestId:'another'}),{code:'send_unknown'});
 await assert.rejects(next.send(A,{clientMessageId:'new',text:'x'}),{code:'send_unknown'});
 await assert.rejects(next.updateStatus(),{code:'send_unknown'});
 await assert.rejects(next.prepareDisconnect(),{code:'send_unknown'});
 assert.equal(f.counts().creates,0);
});
test('persisted empty thread with pending handoff retries adoption only, without another app-server',async t=>{
 const f=setup(t),pid=await project(f);f.service.prepareDesktop=async()=>{throw new Error('desktop-not-ready');};
 await assert.rejects(f.service.create(pid,body));assert.equal(f.counts().creates,1);
 const next=new TaskService(f.config,f.receipts,f.options);t.after(()=>next.close());
 assert.equal((await next.create(pid,body)).session.id,N);assert.equal(f.counts().creates,1);
});
test('simultaneous taps and disconnect fence cannot admit a second creation or forget first outcome',async t=>{
 const f=setup(t),pid=await project(f);let resume;
 const wait=new Promise(r=>resume=r);const original=f.service.createEmpty;
 f.service.createEmpty=async(...args)=>{await wait;return original(...args);};
 const first=f.service.create(pid,body);
 await new Promise(r=>setImmediate(r));
 await assert.rejects(f.service.create(pid,body),{code:'operation_busy'});
 await assert.rejects(f.service.prepareDisconnect(),{code:'operation_busy'});
 resume();await assert.rejects(first,{code:'disconnect_preparing'});
 assert.equal(f.receipts.creationRecords()[0].state,'pending');
});
test('only selected computer verified project IDs; selected-only, quarantine and invalid body refuse before creating',async t=>{
 const f=setup(t),pid=await project(f);
 for(const bad of [{...body,path:'C:/other'},{...body,mode:'plan'},{...body,title:''}])
  await assert.rejects(f.service.create(pid,bad),{code:'invalid_create'});
 await assert.rejects(f.service.create('arbitrary',body),{code:'outside_scope'});
 f.service.options.quarantine=true;await assert.rejects(f.service.create(pid,body),{code:'update_quarantine'});
 f.service.options.quarantine=false;f.service.automatic=false;
 await assert.rejects(f.service.create(pid,body),{code:'create_unsupported'});
 assert.equal(f.counts().creates,0);
});
test('HTTP existing relay project routes expose create capability and authenticated stable create',async t=>{
 const f=setup(t),token='a'.repeat(64),server=httpServer(f.service,token,'b'.repeat(32));
 await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(()=>new Promise(r=>server.close(r)));
 const url='http://127.0.0.1:'+server.address().port;
 assert.equal((await fetch(url+'/api/projects')).status,401);
 const headers={Authorization:'Bearer '+token,'Content-Type':'application/json'};
 const meta=await (await fetch(url+'/api/projects',{headers})).json();assert.equal(meta.create,true);
 const r=await fetch(url+'/api/projects/'+meta.creationProjects[0].id+'/sessions',{method:'POST',headers,body:JSON.stringify(body)});
 assert.equal(r.status,200);assert.equal((await r.json()).session.id,N);assert.equal(f.counts().inputs,0);
});
