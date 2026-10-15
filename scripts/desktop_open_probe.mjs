// Operator-only feasibility probe. Not wired into HTTP, polling, OTA or startup.
// It opens an EXISTING native Desktop conversation, never starts a model turn.
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {verifyCompatibility} from '../src/compatibility.js';
import {readCatalog, taskId, localProjectPath, taskScope} from '../src/catalog.js';
import {Follower} from '../src/follower.js';
import {configuredPermissionsMatch} from '../src/service.js';
import {nativeTurns} from '../src/vendor/desktop-state.js';
import {openNativeConversation} from '../src/desktop-open.js';

const refuse = code => { throw Object.assign(new Error(code), {code}); };
const codes = new Set(['invalid_task_scope','outside_scope','unsupported_version','hash_mismatch',
  'desktop_unavailable','owner_unavailable','owner_unverified','owner_changed','snapshot_unavailable',
  'permissions_changed','incomplete_history','runtime_status_unknown','history_unavailable','launcher_unverified','desktop_open_failed',
  'desktop_open_pending','catalog_unavailable','task_directory_changed']);

export async function probeDesktopOpen(config,id,{
  allowOpen=false, verify=verifyCompatibility, discover=readCatalog,
  createFollower=cfg=>new Follower(cfg), open=openNativeConversation,
  delay=ms=>new Promise(r=>setTimeout(r,ms)), attempts=6,
}={}) {
  const report={schema:1,modelInputs:0,opened:false,sameTask:false,historyReadable:false};
  let follower;
  try {
    if (!taskId(id)) refuse('outside_scope');
    if (taskScope(config)!=='all-local') refuse('invalid_task_scope');
    await verify(config);
    const rows=await discover(config), matches=rows.filter(row=>row.threadId===id);
    if(matches.length!==1 || !localProjectPath(matches[0].projectPath)) refuse('outside_scope');
    const cfg={...config,...matches[0]};
    follower=createFollower(cfg);
    await follower.ipc.ready();
    async function discoverOwner() {
      try {
        const response=await follower.ipc.request('thread-owner-discovery',{hostId:'local',conversationId:id});
        if(typeof response.handledByClientId!=='string' || response.result?.supportsUntrustedAppInput!==true)
          refuse('owner_unverified');
        return true;
      } catch(error) {
        // A transport timeout is NOT evidence that no Desktop owner exists.
        if(error?.remote===true && error.message==='no-client-found') return false;
        throw error;
      }
    }
    report.ownerBefore=await discoverOwner();
    if(!report.ownerBefore) {
      if(!allowOpen) {report.code='owner_unavailable';return report;}
      // Refresh the catalog before the visible navigation, so stale IDs/paths fail.
      const fresh=(await discover(config)).filter(row=>row.threadId===id);
      if(fresh.length!==1 || fresh[0].projectPath!==cfg.projectPath) refuse('task_directory_changed');
      await open(cfg,id);report.opened=true;
      let ready=false;
      for(let i=0;i<attempts;i++) {await delay(1000);if(await discoverOwner()){ready=true;break;}}
      if(!ready) refuse('desktop_open_pending');
    }
    // Observe a fresh native snapshot; do not invoke load-complete-history here.
    const state=await follower.snapshot();
    if(state.id!==id || !follower.authorized(state)) refuse('outside_scope');
    if(!configuredPermissionsMatch(state,cfg)) refuse('permissions_changed');
    report.sameTask=true;
    report.runtime=state.threadRuntimeStatus?.type??'unknown';
    if(!['idle','active'].includes(report.runtime)) refuse('runtime_status_unknown');
    if(state.turnHistory?.history?.isComplete===false) refuse('incomplete_history');
    if(!Array.isArray(state.turns) && !(state.turnHistory?.kind==='canonical' &&
       Array.isArray(state.turnHistory.history?.islands) && state.turnHistory.history?.entitiesByKey))
      refuse('history_unavailable');
    report.turnCount=nativeTurns(state).length;
    report.historyReadable=true;
    report.code='verified';
  } catch(error) {report.code=codes.has(error?.code)?error.code:'desktop_open_probe_failed';}
  finally {follower?.close();}
  return report;
}

if(process.argv[1] && import.meta.url===pathToFileURL(resolve(process.argv[1])).href) {
  try {
    const [file,id,option,...extra]=process.argv.slice(2);
    if(!file || !id || extra.length || (option!==undefined && option!=='--open')) throw Error();
    const config=JSON.parse(await readFile(file,'utf8'));
    const report=await probeDesktopOpen(config,id,{allowOpen:option==='--open'});
    console.log(JSON.stringify(report));
    if(report.code!=='verified') process.exitCode=1;
  } catch {console.log(JSON.stringify({schema:1,code:'invalid_arguments',modelInputs:0}));process.exitCode=1;}
}
