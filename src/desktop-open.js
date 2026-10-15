// 手机点开一个已有对话时，要求 Desktop 自己加载它；不创建执行器或发送消息。
// 列表、轮询、诊断和 OTA 不调用这里。最终历史/权限仍由 Service 的新快照核对。
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {readFile, lstat} from 'node:fs/promises';
import {join} from 'node:path';
import {verifyCompatibility} from './compatibility.js';
import {taskId} from './catalog.js';
import {ControlError} from './vendor/desktop-ipc.js';

// 26.1007 安装清单中 codex:// 的主入口是 ChatGPT.exe，不是同目录 Codex.exe。
const LAUNCHER_SHA256='13d7ee588d7ae20f04f7aa25a65ae82917ada81501304ac8f5e679d0b3306601';
const LAUNCHERS=Object.freeze({'26.1007.2314.0':LAUNCHER_SHA256,
  '26.1007.9446.0':'ee785aec9ff3058afb01069a8b455fb17e46f3c33611a867efd3db37de1f05f3'});
export async function openNativeConversation(config,id,beforeLaunch=()=>{}) {
  const fail=()=>{throw new ControlError('launcher_unverified','当前 Desktop 启动入口尚未通过核对，请在电脑打开这条对话。',503);};
  if(process.platform!=='win32'||!taskId(id)) fail();
  const compatibility=await verifyCompatibility(config);
  const binary=join(config.desktopRoot,'app','ChatGPT.exe');
  for(const p of [config.desktopRoot,join(config.desktopRoot,'app'),binary])
    if((await lstat(p)).isSymbolicLink()) fail();
  if(!(await lstat(binary)).isFile()) fail();
  if(createHash('sha256').update(await readFile(binary)).digest('hex')!==LAUNCHERS[compatibility.desktopVersion]) fail();
  // 最后一次检查断开/更新栅栏，之后同步调用spawn，避免哈希读取期间发生晚到打开。
  beforeLaunch();
  await new Promise((accept,reject)=>{
    const child=spawn(binary,[`codex://threads/${id}`],{windowsHide:true,stdio:'ignore'});
    child.once('spawn',accept);
    child.once('error',()=>reject(new ControlError('desktop_open_failed','无法打开 Desktop 原对话，请在电脑检查后重试。',503)));
    child.unref(); // 不随连接器退出杀掉 Desktop，也不按进程名管理它。
  });
}

export async function ensureDesktopConversation(config,follower,{
  guard,refresh,onOpen=()=>{},verify=verifyCompatibility,open=openNativeConversation,
  delay=ms=>new Promise(r=>setTimeout(r,ms)),attempts=2,
}) {
  guard();
  await follower.ipc.ready();guard();
  const generation=follower.generation;
  const checked=()=>{guard();if(follower.generation!==generation)
    throw new ControlError('desktop_unavailable','Desktop 连接已变化，请重新打开手机对话。',503);};
  const owner=async()=>{
    checked();
    try {
      const response=await follower.ipc.request('thread-owner-discovery',{hostId:'local',conversationId:config.threadId});
      checked();
      if(typeof response.handledByClientId!=='string'||response.result?.supportsUntrustedAppInput!==true)
        throw new ControlError('owner_unverified','无法核对 Desktop 处理端，请在电脑检查。',503);
      return true;
    } catch(error) {
      checked();
      if(error?.remote===true&&error.message==='no-client-found') return false;
      throw error;
    }
  };
  if(await owner()) return {opened:false};
  await verify(config);checked();
  await refresh();checked();
  await open(config,config.threadId,()=>{checked();onOpen();});checked();
  for(let i=0;i<attempts;i++) {
    await delay(1000);checked();
    if(await owner()) return {opened:true};
  }
  throw new ControlError('desktop_open_pending','已请求 Desktop 打开原对话，记录尚未就绪，请稍后点击重试。',503);
}
