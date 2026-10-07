// Explicit one-thread acceptance harness. Credentials exist only in this process.
import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { randomBytes } from 'node:crypto';
import { Follower } from '../src/follower.js';
import { Service, httpServer, permissionDigest } from '../src/service.js';
import { Receipts } from '../src/receipts.js';
import { verifyCompatibility } from '../src/compatibility.js';
import { nativeTurns } from '../src/vendor/desktop-state.js';
const filename=resolve(process.argv[2]);
const config=JSON.parse(readFileSync(filename,'utf8'));
const report={schema:1,fixtureOnly:true,threadId:config.threadId,modelTurnsStarted:0};
let follower,server;
try {
 await verifyCompatibility(config);
 follower=new Follower(config);
 const before=await follower.completeSnapshot();
 const turns=nativeTurns(before);
 if(!turns.some(t=>t.items.some(i=>i.type==='agentMessage'&&i.text==='DESKTOP_REMOTE_READY')))
   throw new Error('fixture_marker_missing');
 const hash=permissionDigest(before);
 if(config.permissionsHash && config.permissionsHash!==hash) throw new Error('permissions_changed');
 if(!config.permissionsHash){config.permissionsHash=hash;writeFileSync(filename,JSON.stringify(config,null,2));}
 mkdirSync(config.dataDir,{recursive:true});
 const token=randomBytes(32).toString('hex');
 server=httpServer(new Service(config,follower,new Receipts(join(config.dataDir,'receipts'))),token,randomBytes(16).toString('hex'));
 await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const url=`http://127.0.0.1:${server.address().port}/api/sessions/${config.threadId}`;
 const headers={Authorization:'Bearer '+token,'Content-Type':'application/json'};
 const history=await (await fetch(url+'/mobile-sync',{headers})).json();
 report.httpHistoryVerified=history.protocol===1&&history.session?.id===config.threadId&&history.items?.some(e=>e.item.text==='DESKTOP_REMOTE_READY');
 if(process.argv[3]==='--send-literal'){
   const r=await fetch(url+'/messages',{method:'POST',headers,body:JSON.stringify({text:'请只回复 LOCAL_REMOTE_OK，不调用工具、不读写文件。',clientMessageId:'local-http-acceptance-1'})});
   const receipt=await r.json();
   if(r.status!==200)throw new Error(receipt.code??'send_failed');
   report.modelTurnsStarted=receipt.result.replayed?0:1;
   report.acknowledgedTurnId=receipt.result.turnId;
   const until=Date.now()+45000;
   do{
     await new Promise(r=>setTimeout(r,1000));
     const after=await follower.completeSnapshot();
     report.permissionsPreserved=permissionDigest(after)===config.permissionsHash;
     const turn=nativeTurns(after).find(t=>t.id===receipt.result.turnId);
     report.literalReplyVerified=turn?.status==='completed'&&turn.items.some(i=>i.type==='agentMessage'&&i.text==='LOCAL_REMOTE_OK');
     if(report.literalReplyVerified)break;
   }while(Date.now()<until);
   if(!report.literalReplyVerified||!report.permissionsPreserved)throw new Error('acceptance_incomplete');
 }
 report.result='passed';
}catch(e){report.result='incomplete';report.code=/^[a-z_]{1,60}$/.test(e.code??e.message)?e.code??e.message:'probe_failed';process.exitCode=1;}
finally{server?.closeAllConnections();server?.close();follower?.close();console.log(JSON.stringify(report));}
