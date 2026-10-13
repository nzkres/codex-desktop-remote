import { readFileSync } from 'node:fs';
// 连接器对外报告的版本来自 Windows 发布目录的首条记录；这里只读取版本，不执行更新。
export const VERSION = JSON.parse(readFileSync(new URL('../windows/releases.json', import.meta.url), 'utf8')).releases[0].version;
