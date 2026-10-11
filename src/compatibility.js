import { createHash } from 'node:crypto';
import { createReadStream } from 'node:fs';
import { lstat, readFile } from 'node:fs/promises';
import path from 'node:path';

export const COMPATIBILITY_PIN = Object.freeze({
  platform: 'win32',
  desktopVersion: '26.930.7945.0',
  appAsarSha256: '611d6da979d8bbabfec97dd90dcce27a9522e7016e6ccf135d59cab693ab08da',
  codexExeSha256: '3b8f6e33caa75f232558a3cf76ff9b87bb5ef6dbcf4996372f24e55c78b1b916',
});
export const COMPATIBILITY_PINS = Object.freeze([
  COMPATIBILITY_PIN,
  Object.freeze({
    platform: 'win32',
    desktopVersion: '26.1007.2314.0',
    appAsarSha256: 'c5fdef328cfa2ad9095786f9c3a7aaa447e68834d31299d6ef2592da02029038',
    codexExeSha256: 'd13914ced6c7af174d8d638db938284231227312104b06a25e9608c632a7daec',
  }),
]);

const fail = (code) => ({ ok: false, code });

// Pure comparison for synthetic evidence. This does not establish where the bytes came from.
export function verifyCompatibilityEvidence(evidence) {
  try {
    if (!evidence || typeof evidence !== 'object') return fail('invalid_evidence');
    if (evidence.platform !== COMPATIBILITY_PIN.platform) return fail('unsupported_platform');
    const pin = COMPATIBILITY_PINS.find(p => p.desktopVersion === evidence.desktopVersion);
    if (!pin) return fail('unsupported_version');
    if (evidence.appAsarSha256 !== pin.appAsarSha256 ||
        evidence.codexExeSha256 !== pin.codexExeSha256) return fail('hash_mismatch');
    return { ok: true, code: 'compatible' };
  } catch {
    return fail('invalid_evidence');
  }
}

function manifestVersion(xml) {
  const identities = [...xml.matchAll(/<Identity\b([^>]*)>/g)];
  if (identities.length !== 1) return null;
  const versions = [...identities[0][1].matchAll(/\bVersion\s*=\s*["']([^"']+)["']/g)];
  return versions.length === 1 && /^\d+\.\d+\.\d+\.\d+$/.test(versions[0][1])
    ? versions[0][1] : null;
}

async function noReparse(root, relative = '') {
  // WindowsApps' parent can deny lstat to ordinary users even when this package
  // and its files are readable. The selected package is the trust boundary.
  const rootEntry = await lstat(root);
  if (rootEntry.isSymbolicLink()) return false;
  let current = root;
  for (const part of relative.split(path.sep).filter(Boolean)) {
    current = path.join(current, part);
    const entry = await lstat(current);
    if (entry.isSymbolicLink()) return false; // Includes Windows junctions.
  }
  return true;
}

async function checkedFile(root, relative) {
  if (!await noReparse(root, relative)) return 'reparse_path';
  return (await lstat(path.join(root, relative))).isFile() ? null : 'invalid_file';
}

async function sha256(filename) {
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(filename)) hash.update(chunk);
  return hash.digest('hex');
}

// Read-only pin check for a user-selected local package. Never executes codex.exe.
async function inspectCompatibility(config) {
  try {
    const root = config?.desktopRoot;
    if (typeof root !== 'string' || !path.isAbsolute(root) || root.trim() !== root ||
        root.startsWith('\\\\') || root.includes('\0')) return fail('invalid_config');
    if (process.platform !== 'win32') return fail('unsupported_platform');

    const manifest = path.join(root, 'AppxManifest.xml');
    const appAsar = path.join(root, 'app', 'resources', 'app.asar');
    const codexExe = path.join(root, 'app', 'resources', 'codex.exe');
    if (!await noReparse(root)) return fail('reparse_path');
    if (!(await lstat(root)).isDirectory()) return fail('invalid_config');
    for (const relative of ['AppxManifest.xml', path.join('app', 'resources', 'app.asar'),
      path.join('app', 'resources', 'codex.exe')]) {
      const code = await checkedFile(root, relative);
      if (code) return fail(code);
    }
    if ((await lstat(manifest)).size > 1024 * 1024) return fail('invalid_manifest');
    const version = manifestVersion(await readFile(manifest, 'utf8'));
    if (!version) return fail('invalid_manifest');
    if (!COMPATIBILITY_PINS.some(p => p.desktopVersion === version)) return fail('unsupported_version');
    return verifyCompatibilityEvidence({
      platform: process.platform,
      desktopVersion: version,
      appAsarSha256: await sha256(appAsar),
      codexExeSha256: await sha256(codexExe),
    });
  } catch (error) {
    return fail(error?.code === 'ENOENT' ? 'missing_file' : 'io_error');
  }
}

// A failed pin check must stop callers that simply await this function.
export async function verifyCompatibility(config) {
  const result = await inspectCompatibility(config);
  if (!result.ok) {
    const error = new Error(result.code);
    error.code = result.code;
    throw error;
  }
  return result;
}
