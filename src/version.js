import { readFileSync } from 'node:fs';
export const VERSION = JSON.parse(readFileSync(new URL('../windows/releases.json', import.meta.url), 'utf8')).releases[0].version;
