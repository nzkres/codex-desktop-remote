// Synthetic question round trip: never opens Desktop IPC or reads real identities.
import assert from 'node:assert/strict';
import {mkdtempSync,rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {Service,permissionDigest} from '../src/service.js';
import {Receipts} from '../src/receipts.js';
const dir=mkdtempSync(join(tmpdir(),'cdr-question-demo-'));
try {
 const id='00000000-1111-2222-3333-444444444444',turnId='demo-turn';
 const questions=[{id:'choice',header:'合成问题',question:'请选择演示项',isOther:true,options:[{label:'A',description:'仅测试'}]}];
 const state={id,cwd:dir,threadRuntimeStatus:{type:'active'},currentPermissions:{sandboxPolicy:{type:'readOnly'},approvalPolicy:'on-request'},
  unconfirmedTurnSubmissions:[],requests:[{id:1,method:'item/tool/requestUserInput',params:{threadId:id,turnId,questions}}],
  turns:[{turnId,status:'inProgress',items:[{id:'message',type:'agentMessage',text:'Synthetic only'}]}]};
 const follower={snapshot:async()=>state,completeSnapshot:async()=>state,forward:async(method,params)=>{
  assert.equal(method,'thread-follower-submit-user-input');assert.equal(params.requestId,1);
  state.requests=[];state.turns[0].items.push({type:'userInputResponse',requestId:1,turnId,questions,
    completed:true,answers:{choice:params.response.answers.choice.answers}});return {ok:true};
 }};
 const service=new Service({threadId:id,projectPath:dir,title:'Demo',permissionsHash:permissionDigest(state)},follower,new Receipts(dir),{questionProtocol:true});
 const q=(await service.read(id)).pendingQuestions[0];
 const result=await service.answer(id,{questionId:q.id,revision:q.revision,answers:{choice:'A'}});
 assert.equal(result.status,'accepted');
 state.turns[0].items.push({id:'async',type:'agentMessage',text:'',questions:[{title:'异步演示',options:['A','B']}]});
 const asyncCard=(await service.read(id)).pendingQuestions.find(q=>q.kind==='async');
 assert.equal(asyncCard.status,'desktop_required');
 console.log(JSON.stringify({synthetic:true,realDesktopCalls:0,ordinaryQuestion:result.status,asyncQuestion:asyncCard.status}));
} finally { rmSync(dir,{recursive:true,force:true}); }
