// 持久发送回执：先写 pending，再发请求，得到明确 turnId 后才写 accepted。
// 收到超时不能证明请求未执行，所以 pending 不能被新消息编号绕过。

import { createHash, randomUUID } from 'node:crypto';
import { existsSync, mkdirSync, openSync, writeFileSync, fsyncSync, closeSync, readFileSync, renameSync, lstatSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { ControlError } from './vendor/desktop-ipc.js';

// Only IDs/digests/acknowledgements persist. A pending record is never resent.
export class Receipts {
  // 一个实例对应一个连接器的数据目录；不同任务的编号共同参与 key；持久化内容只有标识、摘要和确认回执。
  constructor(directory) {
    this.directory = directory;
    mkdirSync(directory, { recursive: true });
    if (lstatSync(directory).isSymbolicLink()) throw new Error('unsafe_receipt_directory');
  }
  key(threadId, messageId) { return createHash('sha256').update(JSON.stringify([threadId, messageId])).digest('hex'); }
  // key 固定同一任务及客户端消息编号；digest 固定消息正文，供重复请求比对。
  digest(text) { return createHash('sha256').update(text).digest('hex'); }
  file(key) { return join(this.directory, key + '.json'); }
  assertCertain() {
    // 发送前或断开/更新前扫描目录：存在 pending、损坏记录或链接时，拒绝继续操作。
    // The directory belongs to all explicitly configured tasks. Changing a message
    // ID must never escape an earlier uncertain send, including after restart.
    const files = readdirSync(this.directory).filter(name => name.endsWith('.json'));
    if (files.length > 10000) throw new ControlError('receipt_limit','发送记录需维护者核对，消息未发送。');
    for (const name of files) {
      if (!/^[a-f0-9]{64}\.json$/.test(name) || lstatSync(join(this.directory,name)).isSymbolicLink())
        throw new ControlError('receipt_invalid','发送记录无法核对，消息未发送。');
      let record;
      try { record=JSON.parse(readFileSync(join(this.directory,name),'utf8')); } catch { record=null; }
      if (record?.schema !== 1 || record.state !== 'accepted' || typeof record.turnId !== 'string' || !record.turnId)
        throw new ControlError('send_unknown','已开放任务中有一条发送结果未确认，请在 Desktop 核对；更换消息编号也不会重发。');
    }
  }
  get(key, digest) {
    // 相同编号和正文已确认时返回旧 turnId；编号冲突或未知回执直接报错，绝不重发。
    if (!existsSync(this.file(key))) return null;
    if (lstatSync(this.file(key)).isSymbolicLink()) throw new Error('unsafe_receipt');
    const r = JSON.parse(readFileSync(this.file(key), 'utf8'));
    if (r.schema !== 1 || r.digest !== digest) throw new ControlError('message_conflict', '发送编号与消息不符，已阻止发送。');
    if (r.state !== 'accepted' || typeof r.turnId !== 'string' || !r.turnId)
      throw new ControlError('send_unknown', '上次发送结果尚未确认，请在 Desktop 查看；不会重复发送。');
    return { turnId: r.turnId, replayed: true };
  }
  write(path, value, flag = 'wx') {
    // 独占创建并 fsync；默认不覆盖已有记录，写入文件中也不保存消息正文。
    const fd = openSync(path, flag, 0o600);
    try { writeFileSync(fd, JSON.stringify(value)); fsyncSync(fd); } finally { closeSync(fd); }
  }
  begin(key, digest) { this.write(this.file(key), { schema: 1, digest, state: 'pending' }); }
  // Desktop 确认后先写临时文件，再原子替换 pending；异常会留下待人工核对的记录。
  accept(key, digest, turnId) {
    const tmp = this.file(key) + '.' + randomUUID() + '.tmp';
    this.write(tmp, { schema: 1, digest, state: 'accepted', turnId });
    renameSync(tmp, this.file(key));
  }
}
