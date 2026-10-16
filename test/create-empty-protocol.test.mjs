import test from 'node:test';
import assert from 'node:assert/strict';
import {EventEmitter} from 'node:events';
import {Writable,PassThrough} from 'node:stream';
import {createEmptyConversation,CREATE_ORIGINATOR} from '../src/create-empty.js';
import {mkdtempSync,rmSync} from 'node:fs';
import {join} from 'node:path';
import {tmpdir} from 'node:os';
const id='00000000-1111-2222-3333-444444444444';
function fixture(t,{blockName=false,toolRequest=false}={}) {
 const dir=mkdtempSync(join(tmpdir(),'cdr-empty-protocol-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));
 const child=new EventEmitter(),methods=[],saved=[];let exited=false;
 child.stdout=new PassThrough();child.kill=()=>{exited=true;child.emit('close',1);};
 child.stdin=new Writable({write(chunk,encoding,callback){
  const m=JSON.parse(chunk.toString());methods.push(m.method);
  if(m.method==='thread/name/set')assert.deepEqual(saved,[{id,persisted:false,exited:false}]);
  queueMicrotask(()=>{
   if(m.method==='initialized')return;
   if(m.method==='thread/name/set'&&blockName)return;
   if(toolRequest&&m.method==='thread/start'){child.stdout.write(JSON.stringify({id:'approval',method:'item/permissions/requestApproval',params:{}})+'\n');return;}
   const thread={id,ephemeral:false,cwd:dir,originator:CREATE_ORIGINATOR,turns:[],name:'New',status:{type:'idle'}};
   child.stdout.write(JSON.stringify({id:m.id,result:['thread/start','thread/read'].includes(m.method)?{thread}:{}})+'\n');
  });callback();
 }});
 child.stdin.on('finish',()=>queueMicrotask(()=>{exited=true;child.emit('close',0);}));
 const launch=(exe,args,options)=>{
  assert.deepEqual(args,['app-server','--listen','stdio://']);assert.equal(options.cwd,dir);
  assert.equal(options.stdio[2],'ignore');assert.equal(Object.keys(options.env).some(k=>k.startsWith('CODEX_APP_TOOLS_')),false);
  return child;
 };
 return {dir,methods,saved,run:()=>createEmptyConversation({desktopRoot:dir},dir,'New',(i,p)=>saved.push({id:i,persisted:p,exited}),{launch,verify:async()=>{},timeoutMs:80})};
}
test('creation-only allowlist; ID saves before naming; durable status only after helper exit',async t=>{
 const f=fixture(t);assert.equal(await f.run(),id);
 assert.deepEqual(f.methods,['initialize','initialized','thread/start','thread/name/set','thread/read']);
 assert.deepEqual(f.saved,[{id,persisted:false,exited:false},{id,persisted:true,exited:true}]);
});
test('name timeout keeps known ID uncertain and terminates helper without a model turn',async t=>{
 const f=fixture(t,{blockName:true});await assert.rejects(f.run(),{code:'create_unknown'});
 assert.equal(f.saved.length,1);assert.equal(f.saved[0].persisted,false);assert.equal(f.methods.includes('turn/start'),false);
});
test('unsolicited tool or approval request is never answered or granted',async t=>{
 const f=fixture(t,{toolRequest:true});await assert.rejects(f.run(),{code:'create_unknown'});
 assert.equal(f.saved.length,0);assert.equal(f.methods.includes('turn/start'),false);
});
