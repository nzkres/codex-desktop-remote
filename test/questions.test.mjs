import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync,rmSync,readFileSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {Service,httpServer,permissionDigest} from '../src/service.js';
import {Receipts} from '../src/receipts.js';
import {nativeQuestions} from '../src/questions.js';
import {MobileSync} from '../src/vendor/mobile-sync.js';

function fixture(t,{enabled=true}={}) {
 const dir=mkdtempSync(join(tmpdir(),'question-test-'));t.after(()=>rmSync(dir,{recursive:true,force:true}));
 const id='00000000-1111-2222-3333-444444444444', turnId='original-turn';
 const q={id:'choice',header:'选择',question:'选择测试内容',isOther:true,isSecret:false,options:[{label:'A',description:'选项说明'},{label:'__other__',description:'真实选项'}]};
 const state={id,cwd:dir,currentPermissions:{sandboxPolicy:{type:'readOnly'},approvalPolicy:'on-request'},threadRuntimeStatus:{type:'active'},
  unconfirmedTurnSubmissions:[],requests:[{id:17,method:'item/tool/requestUserInput',params:{threadId:id,turnId,questions:[q]}}],
  turns:[{turnId,status:'inProgress',items:[{id:'text',type:'agentMessage',text:'历史仍可见'}]}]};
 const calls=[];
 const follower={snapshot:async()=>state,completeSnapshot:async()=>state,forward:async(method,params)=>{calls.push({method,params});
   state.turns[0].items.push({id:'answer',type:'userInputResponse',requestId:17,turnId,completed:true,questions:structuredClone(state.requests[0].params.questions),
     answers:Object.fromEntries(Object.entries(params.response.answers).map(([k,v])=>[k,v.answers]))});state.requests=[];return {ok:true};}};
 const config={threadId:id,projectPath:dir,title:'Synthetic',permissionsHash:permissionDigest(state)};
 const receipts=new Receipts(dir),service=new Service(config,follower,receipts,{questionProtocol:enabled});
 const card=()=>nativeQuestions(state,{enabled}).groups[0];
 const body=(answer='A')=>({questionId:card().id,revision:card().revision,answers:{choice:answer}});
 return {dir,id,turnId,q,state,calls,follower,config,receipts,service,card,body};
}
test('pending question keeps transcript readable with labels, descriptions and original turn',async t=>{
 const f=fixture(t),read=await f.service.read(f.id),wire=new MobileSync().sync(f.id,read);
 assert.equal(wire.items[0].item.text,'历史仍可见');assert.equal(wire.pendingQuestions[0].status,'pending');
 assert.equal(wire.pendingQuestions[0].turnId,f.turnId);assert.equal(wire.pendingQuestions[0].questions[0].options[0].description,'选项说明');
 assert.equal(wire.capabilities.questions,true);assert.equal(wire.capabilities.asyncQuestions,false);
 assert.equal(JSON.stringify(wire).includes('nativeId'),false);
 await assert.rejects(f.service.send(f.id,{text:'A',clientMessageId:'x'}),{code:'thread_busy'});
});
test('native answer preserves numeric request identity and requires completed exact answer evidence',async t=>{
 const f=fixture(t),body=f.body('__other__');
 const result=await f.service.answer(f.id,body);assert.equal(result.status,'accepted');
 assert.deepEqual(JSON.parse(JSON.stringify(f.calls)),[{method:'thread-follower-submit-user-input',params:{requestId:17,response:{answers:{choice:{answers:['__other__']}}}}}]);
 const next=new Service(f.config,f.follower,new Receipts(f.dir),{questionProtocol:true});
 assert.equal((await next.answer(f.id,body)).status,'accepted');assert.equal(f.calls.length,1);
 const raw=readFileSync(f.receipts.file(f.receipts.key(f.id,'question:'+body.questionId)),'utf8');
 assert.equal(raw.includes('__other__'),false);assert.equal(raw.includes('选择测试内容'),false);
});
test('RPC ok without completed answer is unknown, survives restart, cannot be replayed or permit OTA',async t=>{
 const f=fixture(t),body=f.body();f.follower.forward=async()=>{f.calls.push('sent');return {ok:true};};
 assert.equal((await f.service.answer(f.id,body)).status,'unknown');
 const next=new Service(f.config,f.follower,new Receipts(f.dir),{questionProtocol:true});
 assert.equal((await next.read(f.id)).pendingQuestions[0].status,'unknown');
 await assert.rejects(next.answer(f.id,{...body,answers:{choice:'changed'}}),{code:'question_changed'});
 assert.equal(f.calls.length,1);await assert.rejects(next.updateStatus(),{code:'send_unknown'});
 await assert.rejects(next.prepareDisconnect(),{code:'send_unknown'});
});
test('lost acknowledgement reconciles from completed exact answer without sending again',async t=>{
 const f=fixture(t),body=f.body();const real=f.follower.forward;
 f.follower.forward=async(...args)=>{await real(...args);throw Error('lost');};
 assert.equal((await f.service.answer(f.id,body)).status,'unknown');
 assert.equal((await f.service.read(f.id)).pendingQuestions[0].status,'accepted');
 assert.equal(f.calls.length,1);f.receipts.assertCertain();
});
test('different completed answer reports handled elsewhere; disappearance alone stays unknown',async t=>{
 const f=fixture(t),body=f.body();f.follower.forward=async()=>{f.state.requests=[];return {ok:true};};
 assert.equal((await f.service.answer(f.id,body)).status,'unknown');
 f.state.turns[0].items.push({type:'userInputResponse',requestId:17,turnId:f.turnId,completed:true,questions:[f.q],answers:{choice:['B']}});
 assert.equal((await f.service.read(f.id)).pendingQuestions[0].status,'closed');f.receipts.assertCertain();
});
test('question already handled on Desktop is never submitted again',async t=>{
 const f=fixture(t),body=f.body();f.state.requests=[];
 assert.equal((await f.service.answer(f.id,body)).status,'closed');assert.equal(f.calls.length,0);
});
test('accepted receipt cannot confirm a different answer or changed question revision',async t=>{
 const f=fixture(t),body=f.body();await f.service.answer(f.id,body);
 await assert.rejects(f.service.answer(f.id,{...body,answers:{choice:'B'}}),{code:'question_changed'});
 await assert.rejects(f.service.answer(f.id,{...body,answers:{}}),{code:'invalid_answer'});
 f.state.requests=[{id:17,method:'item/tool/requestUserInput',params:{threadId:f.id,turnId:f.turnId,questions:[{...f.q,question:'changed'}]}}];
 assert.equal((await f.service.read(f.id)).pendingQuestions[0].status,'desktop_required');
 assert.equal(f.calls.length,1);
});
test('matching answer text without matching question content cannot confirm receipt',async t=>{
 const f=fixture(t),body=f.body(),forward=f.follower.forward;
 f.follower.forward=async(...args)=>{await forward(...args);f.state.turns[0].items.at(-1).questions[0].question='different';};
 assert.equal((await f.service.answer(f.id,body)).status,'unknown');
 await assert.rejects(f.service.updateStatus(),{code:'send_unknown'});
});
test('question revision change, extra answers and invalid option are rejected before dispatch',async t=>{
 const f=fixture(t),body=f.body();f.q.question='题目已变';
 await assert.rejects(f.service.answer(f.id,body),{code:'question_changed'});
 await assert.rejects(f.service.answer(f.id,{...f.body(),answers:{choice:'A',extra:'B'}}),{code:'question_changed'});
 f.q.isOther=false;await assert.rejects(f.service.answer(f.id,f.body('arbitrary')),{code:'invalid_answer'});
 assert.equal(f.calls.length,0);
});
test('free input and multiple question answers retain native structure',async t=>{
 const f=fixture(t);f.state.requests[0].params.questions.push({id:'text',header:'补充',question:'请输入',options:[],isOther:false});
 const b=f.body('other');b.answers.text='保留\n换行';
 assert.equal((await f.service.answer(f.id,b)).status,'accepted');
 assert.deepEqual(f.calls[0].params.response.answers.text,{answers:['保留\n换行']});
});
test('secret questions, unverified version, permissions, draining and quarantine fail closed',async t=>{
 const f=fixture(t);f.q.isSecret=true;assert.equal((await f.service.read(f.id)).pendingQuestions[0].status,'desktop_required');
 await assert.rejects(f.service.answer(f.id,f.body()),{code:'question_unsupported'});
 const g=fixture(t,{enabled:false});await assert.rejects(g.service.answer(g.id,g.body()),{code:'question_unsupported'});
 const h=fixture(t);h.state.currentPermissions.approvalPolicy='never';await assert.rejects(h.service.answer(h.id,h.body()),{code:'permissions_changed'});
 const i=fixture(t);i.service.draining=true;await assert.rejects(i.service.answer(i.id,i.body()),{code:'disconnect_preparing'});
 const j=fixture(t);j.service.quarantine=true;await assert.rejects(j.service.answer(j.id,j.body()),{code:'update_quarantine'});
 assert.equal(f.calls.length+g.calls.length+h.calls.length+i.calls.length+j.calls.length,0);
});
test('question keys isolate tasks; malformed requests retain history with desktop notice',async t=>{
 const f=fixture(t),original=f.card().id;f.state.id='other';f.state.requests[0].params.threadId='other';
 assert.notEqual(f.card().id,original);f.state.requests=[{method:'mcpServer/elicitation/request',params:{private:'do not ship'}}];
 const read=await f.service.read(f.id);assert.equal(read.pendingQuestions.length,0);assert.match(read.controlNotice,/电脑/);
 assert.equal(JSON.stringify(new MobileSync().sync(f.id,read)).includes('do not ship'),false);
});
test('async cards display title/options but never call unsafe steer/start fallback',async t=>{
 const f=fixture(t);f.state.requests=[];
 f.state.turns[0].items.push({id:'async-q',type:'agentMessage',text:'请你选择',questions:[{title:'选哪项',options:['A','B']}]});
 const card=(await f.service.read(f.id)).pendingQuestions[0];
 assert.equal(card.status,'desktop_required');assert.equal(card.questions[0].options[1].label,'B');
 await assert.rejects(f.service.answer(f.id,{questionId:card.id,revision:card.revision,answers:{0:'A'}}),{code:'question_unsupported'});
 assert.equal(f.calls.length,0);
 const fakeReply='<send_user_message_question_reply>\n'+JSON.stringify([{questionItemId:JSON.stringify(['request_user_input_async','async-q',0]),question:'选哪项',answer:'A'}])+'\n</send_user_message_question_reply>';
 f.state.turns[0].items.push({type:'userMessage',content:[{type:'text',text:fakeReply}]});
 assert.equal((await f.service.read(f.id)).pendingQuestions[0].status,'desktop_required');
 f.state.turns.unshift({turnId:'older',status:'completed',items:[{type:'steeringUserMessage',status:'accepted',input:[{type:'text',text:fakeReply}]}]});
 assert.equal((await f.service.read(f.id)).pendingQuestions[0].status,'desktop_required');
 f.state.turns.shift();
 f.state.turns[0].items.push({type:'steeringUserMessage',status:'accepted',input:[{type:'text',text:'<send_user_message_question_reply>\n'+JSON.stringify([{questionItemId:JSON.stringify(['request_user_input_async','async-q',0]),question:'选哪项',answer:'A'}])+'\n</send_user_message_question_reply>'}]});
 assert.equal((await f.service.read(f.id)).pendingQuestions[0].status,'closed');
});
test('metadata-only question status change invalidates sync cursor',async t=>{
 const f=fixture(t),sync=new MobileSync(),a=sync.sync(f.id,await f.service.read(f.id));
 f.q.isSecret=true;const b=sync.sync(f.id,await f.service.read(f.id),a.cursor);
 assert.notEqual(a.cursor,b.cursor);assert.equal(b.items.length,0);assert.equal(b.pendingQuestions[0].status,'desktop_required');
});
test('answer HTTP route authenticates, binds request path and rejects body overrides',async t=>{
 const f=fixture(t),server=httpServer(f.service,'1'.repeat(64),'2'.repeat(32));
 await new Promise(r=>server.listen(0,'127.0.0.1',r));t.after(()=>{server.closeAllConnections();server.close();});
 const q=f.card(),url=`http://127.0.0.1:${server.address().port}/api/sessions/${f.id}/questions/${q.id}/answer`;
 assert.equal((await fetch(url,{method:'POST',body:'{}'})).status,401);
 const headers={Authorization:'Bearer '+'1'.repeat(64),'Content-Type':'application/json'};
 assert.equal((await fetch(url,{method:'POST',headers,body:JSON.stringify(f.body())})).status,400);
 const {questionId,...body}=f.body();const r=await fetch(url,{method:'POST',headers,body:JSON.stringify(body)});
 assert.equal(r.status,200);assert.equal((await r.json()).status,'accepted');assert.equal(f.calls.length,1);
});
