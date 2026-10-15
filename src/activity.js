// 仅记录手机实际接入/操作中的任务 ID 与目录；标题目录本身不创建这些记录。
// 更新探针只读这些记录。只有实时快照确认空闲，且没有在途写入，运行服务才移除记录。
import {existsSync,lstatSync,readFileSync,renameSync} from 'node:fs';
import {dirname,join,isAbsolute} from 'node:path';
import {randomUUID} from 'node:crypto';
import {ControlError,object} from './vendor/desktop-ipc.js';
import {taskId} from './catalog.js';
export class Activity {
 constructor(receipts) {this.receipts=receipts;this.file=join(dirname(receipts.directory),'active-tasks.json');}
 entries() {
  if(!existsSync(this.file)) return [];
  try {
   const stat=lstatSync(this.file);if(!stat.isFile()||stat.isSymbolicLink()||stat.size>1024*1024)throw new Error();
   const value=JSON.parse(readFileSync(this.file,'utf8'));
   if(value.schema!==1||!Array.isArray(value.tasks)||value.tasks.length>1000)throw new Error();
   const seen=new Set();
   for(const t of value.tasks) {
    if(!object(t)||!taskId(t.threadId)||seen.has(t.threadId)||typeof t.projectPath!=='string'||!isAbsolute(t.projectPath)||typeof t.title!=='string'||(t.uncertain!==undefined&&typeof t.uncertain!=='boolean'))throw new Error();
    seen.add(t.threadId);
   }
   return value.tasks;
  } catch {throw new ControlError('activity_unverified','手机任务交接记录无法核对，请在电脑端诊断。',503);}
 }
 change(task,keep,uncertain=false) {
  const rows=this.entries(),existing=rows.find(t=>t.threadId===task.threadId);
  if(existing?.uncertain&&!keep)return;
  if((!keep&&!existing)||(keep&&existing&&existing.projectPath===task.projectPath&&(!uncertain||existing.uncertain)))return;
  if(existing&&existing.projectPath!==task.projectPath)throw new ControlError('task_directory_changed','手机任务目录已变化，请在电脑端核对。');
  const tasks=rows.filter(t=>t.threadId!==task.threadId);
  if(keep)tasks.push({threadId:task.threadId,projectPath:task.projectPath,title:task.title,uncertain});
  if(tasks.length>1000)throw new ControlError('activity_unverified','手机任务交接记录过多，请在电脑端核对。');
  this.save(tasks);
 }
 save(tasks) {
  const temp=this.file+'.'+randomUUID()+'.tmp';
  this.receipts.write(temp,{schema:1,tasks});renameSync(temp,this.file);
 }
 assertCertain() {
  if(this.entries().some(t=>t.uncertain))throw new ControlError('update_uncertain','上次停止结果尚未确认，请在电脑端核对。');
 }
 beginStop(task,turnId) {
  this.assertCertain();this.change(task,true);
  const rows=this.entries(),entry=rows.find(t=>t.threadId===task.threadId),operation=randomUUID();
  entry.uncertain=true;entry.stopOperation=operation;entry.expectedTurnId=turnId;
  this.save(rows);return operation;
 }
 confirmStop(task,operation) {
  const rows=this.entries(),entry=rows.find(t=>t.threadId===task.threadId);
  if(!entry||!entry.uncertain||entry.stopOperation!==operation||entry.projectPath!==task.projectPath)
   throw new ControlError('activity_unverified','停止确认记录发生变化。');
  entry.uncertain=false;delete entry.stopOperation;delete entry.expectedTurnId;this.save(rows);
 }

}
