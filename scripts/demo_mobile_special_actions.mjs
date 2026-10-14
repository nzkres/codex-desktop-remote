// Offline synthetic demo: no Desktop IPC, credentials, pairing or live service.
import assert from 'node:assert/strict';
import {mkdtempSync, rmSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {randomBytes} from 'node:crypto';
import {Service,httpServer,permissionDigest} from '../src/service.js';
import {Receipts} from '../src/receipts.js';
import {MobileSync} from '../src/vendor/mobile-sync.js';
const directory=mkdtempSync(join(tmpdir(),'cdr-controls-demo-'));
const id='00000000-1111-2222-3333-444444444444';
const state={id,cwd:directory,requests:[],unconfirmedTurnSubmissions:[],
 threadRuntimeStatus:{type:'idle'},currentPermissions:{sandboxPolicy:{type:'readOnly'},approvalPolicy:'on-request'},
 turns:[{turnId:'synthetic-turn',status:'completed',items:[{id:'synthetic-question',type:'agentMessage',text:'Synthetic question',questions:[{title:'Choose a demo',options:['A','B']}]}]}]};
let calls=0;
const follower={snapshot:async()=>state,completeSnapshot:async()=>state,forward:async()=>{calls++;throw Error('must not forward');}};
const service=new Service({threadId:id,projectPath:directory,title:'Synthetic only',permissionsHash:permissionDigest(state)},follower,new Receipts(directory));
const token=randomBytes(32).toString('hex');
const server=httpServer(service,token,randomBytes(16).toString('hex'));
const report=[];
try {
 const source=await service.read(id);
 const wire=new MobileSync().sync(id,source);
 assert.equal(source.pendingQuestions.length,0);
 assert.equal(JSON.stringify(wire).includes('"questions"'),false);
 report.push({case:'async question fields lost in mobile projection',reproduced:true});
 state.requests=[{id:'synthetic-request',method:'item/tool/requestUserInput',params:{questions:[]}}];
 await assert.rejects(service.read(id),{code:'desktop_action_required'});
 await assert.rejects(service.send(id,{text:'A',clientMessageId:'synthetic-answer'}),{code:'thread_busy'});
 report.push({case:'pending request blocks read; plain text cannot answer',reproduced:true});
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const headers={Authorization:'Bearer '+token,'Content-Type':'application/json'};
 for(const route of ['questions/synthetic-request/answer','approvals/synthetic-approval/decision','mode','implement-plan']) {
  const response=await fetch(`http://127.0.0.1:${server.address().port}/api/sessions/${id}/${route}`,{method:'POST',headers,body:'{}'});
  assert.equal(response.status,501);assert.equal((await response.json()).code,'unsupported_route');
  report.push({case:route,status:501,reproduced:true});
 }
 assert.equal(calls,0);
 console.log(JSON.stringify({synthetic:true,realDesktopCalls:0,findings:report},null,2));
} finally {
 server.closeAllConnections();
 if(server.listening)await new Promise(r=>server.close(r));
 // Only the directory created by this synthetic demo is removed.
 rmSync(directory,{recursive:true,force:true});
}
