// Creation only. No turn/start, resume, input injection, Desktop tools or TCP listener.
import {spawn} from 'node:child_process';
import {realpathSync} from 'node:fs';
import {join,toNamespacedPath} from 'node:path';
import {homedir} from 'node:os';
import {verifyCompatibility} from './compatibility.js';
import {ControlError} from './vendor/desktop-ipc.js';
import {taskId} from './catalog.js';

export const CREATE_ORIGINATOR='desktop_remote_lite_create';
export async function verifyCreationSupport(config) {
  const pin=await verifyCompatibility(config);
  if(pin.desktopVersion!=='26.1007.9446.0')
    throw new ControlError('create_unsupported','当前 Desktop 版本尚未验证手机新建，请先更新连接器与 Desktop。',501);
}
const samePath=(a,b)=>toNamespacedPath(realpathSync.native(a)).toLowerCase()===toNamespacedPath(realpathSync.native(b)).toLowerCase();
export async function createEmptyConversation(config, projectPath, title, onCreated, {launch=spawn,timeoutMs=45000,verify=verifyCreationSupport}={}) {
  await verify(config);
  const environment={...process.env,CODEX_HOME:config.codexHome??process.env.CODEX_HOME??join(homedir(),'.codex')};
  // Do not borrow executor identities, app tool pipes or an active model turn.
  for(const key of Object.keys(environment)) if(key.startsWith('CODEX_APP_TOOLS_')) delete environment[key];
  for(const key of ['CODEX_INTERNAL_ORIGINATOR_OVERRIDE','CODEX_THREAD_ID','CODEX_SESSION_ID']) delete environment[key];
  const child=launch(join(config.desktopRoot,'app/resources/codex.exe'),['app-server','--listen','stdio://'],
    {cwd:projectPath,env:environment,windowsHide:true,stdio:['pipe','pipe','ignore']});
  let buffer='',serial=0,stopped=false,failed=null;
  const pending=new Map();
  const fail=error=>{
    failed=error;
    for(const {reject} of pending.values()) reject(error);
    pending.clear();
  };
  const error=()=>new ControlError('create_unknown','创建结果尚未确认，原请求编号已保留，请核对后继续；不会重复新建。',409);
  const exit=new Promise(resolve=>child.once('close',code=>{stopped=true;resolve(code);if(pending.size)fail(error());}));
  child.on('error',()=>fail(error())); child.stdin.on('error',()=>fail(error()));
  child.stdout.setEncoding('utf8');
  child.stdout.on('data',data=>{
    buffer+=data;
    if(Buffer.byteLength(buffer)>8*1024*1024){fail(error());child.kill();return;}
    let pos;
    while((pos=buffer.indexOf('\n'))>=0){
      const line=buffer.slice(0,pos);buffer=buffer.slice(pos+1);
      let m;try{m=JSON.parse(line);}catch{fail(error());child.kill();return;}
      if(!m||typeof m!=='object'||Array.isArray(m)){fail(error());child.kill();return;}
      if(typeof m.method==='string'&&/^(turn|item)\//.test(m.method)){fail(error());child.kill();return;}
      // Unsolicited requests must never grant permissions or run tools.
      if(m.method&&Object.hasOwn(m,'id')){fail(error());child.kill();return;}
      const waiter=pending.get(m.id);
      if(waiter){pending.delete(m.id);m.error?waiter.reject(error()):waiter.resolve(m.result);}
    }
  });
  const request=(method,params)=>new Promise((resolve,reject)=>{
    if(failed||stopped){reject(failed??error());return;}
    const id=++serial;pending.set(id,{resolve,reject});
    child.stdin.write(JSON.stringify({id,method,params})+'\n');
  });
  const timer=setTimeout(()=>{fail(error());child.kill();},timeoutMs);
  try {
    await request('initialize',{clientInfo:{name:CREATE_ORIGINATOR,version:'1.0.0'}});
    child.stdin.write('{"method":"initialized"}\n');
    const answer=await request('thread/start',{cwd:projectPath,ephemeral:false});
    const thread=answer?.thread;
    if(!taskId(thread?.id)||thread.ephemeral!==false||!samePath(thread.cwd,projectPath)
       ||thread.originator!==CREATE_ORIGINATOR||!Array.isArray(thread.turns)||thread.turns.length)throw error();
    onCreated(thread.id,false); // save the actual ID before another side effect
    // Naming is necessary: this Desktop build otherwise drops a zero-turn thread on exit.
    await request('thread/name/set',{threadId:thread.id,name:title});
    const read=(await request('thread/read',{threadId:thread.id,includeTurns:false}))?.thread;
    if(read?.id!==thread.id||!samePath(read.cwd,projectPath)||read.name!==title||read.status?.type!=='idle')throw error();
    child.stdin.end();
    if(await exit!==0||failed)throw error();
    onCreated(thread.id,true); // process is gone; execution must belong to Desktop
    return thread.id;
  } finally {
    clearTimeout(timer);
    if(!stopped){child.kill();await Promise.race([exit,new Promise(r=>setTimeout(r,3000))]);}
  }
}
