// Read-only recovery proof. Same task/permission/receipt gates as an update;
// no identity writes, Desktop mutations, model input or directory creation.
import {readFileSync,lstatSync,readdirSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {pathToFileURL} from 'node:url';
import {verifyCompatibility} from '../src/compatibility.js';
import {Activity} from '../src/activity.js';
import {Receipts} from '../src/receipts.js';
import {taskConfigs} from '../src/tasks.js';
import {readCatalog,taskScope} from '../src/catalog.js';
import {Follower} from '../src/follower.js';
import {ensureDesktopConversation} from '../src/desktop-open.js';
import {configuredPermissionsMatch} from '../src/service.js';
import {activeTurn,nativeTurns} from '../src/vendor/desktop-state.js';

export async function inspectRecoveryTasks(config,createFollower=cfg=>new Follower(cfg)) {
  const receipts=Object.create(Receipts.prototype);receipts.directory=join(config.dataDir,'receipts');
  const tasks=new Map(taskConfigs({...config,taskScope:'selected'}).map(t=>[t.threadId,t]));
  for(const task of new Activity(receipts).entries()) {
    const anchor=tasks.get(task.threadId);
    if(anchor&&resolve(anchor.projectPath).toLowerCase()!==resolve(task.projectPath).toLowerCase())throw new Error('task_directory_changed');
    tasks.set(task.threadId,anchor??{...config,...task});
  }
  let runningTaskCount=0;
  for(const task of tasks.values()) {
    const cfg={...config,...task,taskScope:config.taskScope};delete cfg.additionalTasks;
    const follower=createFollower(cfg);
    try {
      const state=await follower.snapshot();
      if(state.id!==cfg.threadId||!configuredPermissionsMatch(state,cfg))throw new Error('permissions_unverified');
      nativeTurns(state);
      if(!['idle','active'].includes(state.threadRuntimeStatus?.type)||!Array.isArray(state.requests)||
          (Object.hasOwn(state,'unconfirmedTurnSubmissions')&&(!Array.isArray(state.unconfirmedTurnSubmissions)||state.unconfirmedTurnSubmissions.length)))
        throw new Error('recovery_task_unverified');
      if(activeTurn(state)||state.threadRuntimeStatus.type==='active')runningTaskCount++;
    } finally {follower.close();}
  }
  return {tasksVerified:true,permissionsVerified:true,receiptsCertain:true,runningTaskCount};
}

async function openRecoveryTasks(config, ids) {
  const rows=taskScope(config)==='all-local'?await readCatalog(config):null;
  const receipts=Object.create(Receipts.prototype);receipts.directory=join(config.dataDir,'receipts');
  const tasks=new Map(taskConfigs({...config,taskScope:'selected'}).map(t=>[t.threadId,t]));
  for(const t of new Activity(receipts).entries()) tasks.set(t.threadId,{...config,...t});
  if(!Array.isArray(ids)||ids.length>8||ids.some(id=>!tasks.has(id))) throw new Error('outside_scope');
  for(const id of new Set(ids)) {
    const task=tasks.get(id);
    const cfg={...config,...task};delete cfg.additionalTasks;
    const follower=new Follower(cfg);
    try {
      await ensureDesktopConversation(cfg,follower,{guard:()=>{},refresh:async()=>{
        if(rows&&!rows.some(r=>r.threadId===task.threadId)) throw new Error('outside_scope');
      }});
    } finally {follower.close();}
  }
}

export async function recoveryProof(config, {verify=verifyCompatibility, check=inspectRecoveryTasks,openTasks=[]}={}) {
  await verify(config);
  const directory=join(config.dataDir,'receipts');
  const info=lstatSync(directory);
  if(!info.isDirectory()||info.isSymbolicLink()||readdirSync(directory,{withFileTypes:true}).some(e=>!e.isFile()||!/^[a-f0-9]{64}\.json$/.test(e.name)))
    throw new Error('receipt_invalid');
  const receipts=Object.create(Receipts.prototype);receipts.directory=directory;
  receipts.assertCertain();new Activity(receipts).assertCertain();
  if(openTasks.length) await openRecoveryTasks(config,openTasks);
  const proof=await check(config);
  if(proof.tasksVerified!==true||proof.permissionsVerified!==true||proof.receiptsCertain!==true||!Number.isSafeInteger(proof.runningTaskCount)||proof.runningTaskCount<0) throw new Error('recovery_task_unverified');
  return {schema:1,compatible:true,...proof,modelInputs:0};
}
if(process.argv[1]&&import.meta.url===pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const ids=process.argv.slice(4).map(arg=>{
      if(!/^--open-task=[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(arg))throw new Error('invalid_config');
      return arg.slice('--open-task='.length);
    });
    if(process.argv.length<4) throw new Error('invalid_config');
    const cfg=JSON.parse(readFileSync(resolve(process.argv[2]),'utf8'));
    cfg.desktopRoot=resolve(process.argv[3]);
    console.log(JSON.stringify(await recoveryProof(cfg,{openTasks:ids})));
  } catch(error) {
    const raw=error?.code??error?.message;
    const code=typeof raw==='string'&&/^[a-z_]{1,60}$/.test(raw)?raw:'recovery_task_unverified';
    console.log(JSON.stringify({schema:1,verified:false,code,modelInputs:0}));process.exitCode=1;
  }
}
