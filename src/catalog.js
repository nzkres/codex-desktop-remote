// 对话目录只用于发现；不把数据库里的状态/权限当作 Desktop 执行授权。
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {homedir} from 'node:os';
import {join, isAbsolute} from 'node:path';
import {fileURLToPath} from 'node:url';
import {ControlError} from './vendor/desktop-ipc.js';
const execute = promisify(execFile);
export const taskId = value => typeof value === 'string' && /^[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}$/.test(value);
// Windows 盘符路径才属于本机目录；网络共享/设备路径不作为本机任务执行目录。
export function localProjectPath(value) {
  if (typeof value !== 'string' || value.includes('\0')) return false;
  if (process.platform !== 'win32') return isAbsolute(value);
  return /^[A-Za-z]:[\\/]/.test(value) || /^\\\\\?\\[A-Za-z]:\\/.test(value);
}
export function taskScope(config) {
  const scope = config.taskScope ?? 'selected'; // 旧安装不静默扩权；新安装默认 all-local。
  if (!['selected', 'all-local'].includes(scope))
    throw new ControlError('invalid_task_scope', '对话共享设置无效。', 400);
  return scope;
}
// Local configuration only. There is deliberately no phone admission endpoint.
// Native permissions and owner checks still authorize every actual operation.
export function linkedConversations(config) {
  const rows=config.linkedConversations??[];
  const fields=['threadId','projectPath','title','originator','threadSource','allowEmptyHistory'];
  const ids=new Set();
  if(!Array.isArray(rows)||rows.length>100)throw new ControlError('invalid_task_scope','已有对话接入记录无效。',400);
  for(const row of rows) {
    if(!row||typeof row!=='object'||Object.keys(row).length!==fields.length||fields.some(k=>!Object.hasOwn(row,k))
      ||!taskId(row.threadId)||ids.has(row.threadId)||!localProjectPath(row.projectPath)
      ||['title','originator','threadSource'].some(k=>typeof row[k]!=='string'||!row[k].trim()||row[k].length>100)
      ||['guardian_review','subagent'].includes(row.threadSource)||typeof row.allowEmptyHistory!=='boolean')
      throw new ControlError('invalid_task_scope','已有对话接入记录无效。',400);
    ids.add(row.threadId);
  }
  return rows;
}
export async function readCatalog(config) {
  const linked=linkedConversations(config);
  const python = process.env.CDR_DESKTOP_PYTHON;
  if (!python || !isAbsolute(python)) throw new ControlError('catalog_unavailable', '本机对话目录不可用，请在连接器中重新连接。', 503);
  try {
    const {stdout} = await execute(python, ['-I', '-B', fileURLToPath(new URL('../scripts/desktop_catalog.py', import.meta.url)),
      config.codexHome ?? process.env.CODEX_HOME ?? join(homedir(), '.codex'),JSON.stringify(config.createdThreadIds??[]),JSON.stringify(linked)],
      {windowsHide:true, timeout:5000, maxBuffer:4*1024*1024, encoding:'utf8'});
    const value = JSON.parse(stdout);
    if (value.schema !== 1 || !Array.isArray(value.tasks)) throw new Error();
    return value.tasks;
  } catch { throw new ControlError('catalog_unavailable', '无法读取本机 Desktop 对话目录，请保持 Desktop 打开后重试。', 503); }
}
