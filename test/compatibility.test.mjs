import assert from 'node:assert/strict';
import { mkdtemp, mkdir, rm, symlink, writeFile } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { COMPATIBILITY_PIN, COMPATIBILITY_PINS, verifyCompatibility, verifyCompatibilityEvidence } from '../src/compatibility.js';

const rejectsCode = (promise, code) => assert.rejects(promise, (error) =>
  error?.code === code && error.message === code && !String(error.stack).includes('relative-secret'));

test('accepted synthetic evidence is an exact measured Windows pin', () => {
  assert.deepEqual(verifyCompatibilityEvidence({
    platform: COMPATIBILITY_PIN.platform,
    desktopVersion: COMPATIBILITY_PIN.desktopVersion,
    appAsarSha256: COMPATIBILITY_PIN.appAsarSha256,
    codexExeSha256: COMPATIBILITY_PIN.codexExeSha256,
  }), { ok: true, code: 'compatible' });
  for (const [field, value, code] of [
    ['platform', 'linux', 'unsupported_platform'],
    ['desktopVersion', '26.930.7946.0', 'unsupported_version'],
    ['appAsarSha256', '0'.repeat(64), 'hash_mismatch'],
    ['codexExeSha256', '0'.repeat(64), 'hash_mismatch'],
  ]) {
    assert.deepEqual(verifyCompatibilityEvidence({ ...COMPATIBILITY_PIN, [field]: value }),
      { ok: false, code });
  }
  assert.deepEqual(verifyCompatibilityEvidence(null), { ok: false, code: 'invalid_evidence' });
});

test('new Desktop requires both matching fingerprints; versions cannot mix or float', () => {
  const newest = COMPATIBILITY_PINS.find(p => p.desktopVersion === '26.1007.2314.0');
  assert.ok(newest);
  assert.deepEqual(verifyCompatibilityEvidence(newest), { ok: true, code: 'compatible' });
  for (const field of ['appAsarSha256', 'codexExeSha256']) {
    assert.deepEqual(verifyCompatibilityEvidence({ ...newest, [field]: COMPATIBILITY_PIN[field] }),
      { ok: false, code: 'hash_mismatch' });
  }
  assert.deepEqual(verifyCompatibilityEvidence({ ...newest, desktopVersion: '26.1007.2315.0' }),
    { ok: false, code: 'unsupported_version' });
});

test('invalid package input fails without exposing its value', async () => {
  await rejectsCode(verifyCompatibility({ desktopRoot: 'relative-secret' }), 'invalid_config');
  await rejectsCode(verifyCompatibility({}), 'invalid_config');
});

test('a synthetic package is rejected for wrong version, wrong bytes, and missing files',
  { skip: process.platform !== 'win32' }, async () => {
    const root = await mkdtemp(path.join(os.tmpdir(), 'compatibility-test-'));
    try {
      await mkdir(path.join(root, 'app', 'resources'), { recursive: true });
      await writeFile(path.join(root, 'AppxManifest.xml'), '<Package><Identity Version="26.930.7946.0" /></Package>');
      await writeFile(path.join(root, 'app', 'resources', 'app.asar'), 'synthetic asar');
      await writeFile(path.join(root, 'app', 'resources', 'codex.exe'), 'synthetic binary');
      await rejectsCode(verifyCompatibility({ desktopRoot: root }), 'unsupported_version');
      await writeFile(path.join(root, 'AppxManifest.xml'), '<Package><Identity Version="26.930.7945.0" /></Package>');
      await rejectsCode(verifyCompatibility({ desktopRoot: root }), 'hash_mismatch');
      await rm(path.join(root, 'app', 'resources', 'codex.exe'));
      await rejectsCode(verifyCompatibility({ desktopRoot: root }), 'missing_file');
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });

test('a reparse path is rejected before its contents are read',
  { skip: process.platform !== 'win32' }, async () => {
    const root = await mkdtemp(path.join(os.tmpdir(), 'compatibility-link-test-'));
    try {
      const target = path.join(root, 'actual');
      await mkdir(target);
      await symlink(target, path.join(root, 'linked'), 'junction');
      await rejectsCode(verifyCompatibility({ desktopRoot: path.join(root, 'linked') }), 'reparse_path');
      await mkdir(path.join(root, 'app'));
      await writeFile(path.join(root, 'AppxManifest.xml'), '<Package><Identity Version="26.930.7945.0" /></Package>');
      await symlink(target, path.join(root, 'app', 'resources'), 'junction');
      await rejectsCode(verifyCompatibility({ desktopRoot: root }), 'reparse_path');
    } finally {
      await rm(root, { recursive: true, force: true });
    }
  });
