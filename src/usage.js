import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {homedir} from 'node:os';
import {isAbsolute,join} from 'node:path';
import {fileURLToPath} from 'node:url';
const execute=promisify(execFile);
const cache=new Map();
// Only called after Service.read has checked task ownership and native permissions.
// Cache quota-only output for 20 seconds; never cache transcript/credentials.
export async function readUsage(config) {
  const python=process.env.CDR_DESKTOP_PYTHON;
  const home=config.codexHome??process.env.CODEX_HOME??join(homedir(),'.codex');
  if(!python || !isAbsolute(python)) return {status:'unavailable',limits:[]};
  const key=JSON.stringify([home,config.threadId]);const saved=cache.get(key);
  if(saved && Date.now()-saved.at<20000) return saved.result;
  const at=Date.now();
  const result=execute(python,['-I','-B',fileURLToPath(new URL('../scripts/desktop_usage.py',import.meta.url)),home,config.threadId],
    {windowsHide:true,timeout:5000,maxBuffer:16384,encoding:'utf8'})
    .then(({stdout})=>JSON.parse(stdout)).catch(()=>({status:'unavailable',limits:[]}));
  cache.delete(key);cache.set(key,{at,result});
  while(cache.size>32) cache.delete(cache.keys().next().value);
  return result;
}
