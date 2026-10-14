import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';

// Exercise the exact inline workflow program; no network, credentials or real artifact deletion.
const workflow = readFileSync(new URL('../.github/workflows/artifact-retention.yml', import.meta.url), 'utf8');
const script = workflow.split('          script: |\n')[1].split('\n').map(line => line.slice(12)).join('\n');
const execute = new (Object.getPrototypeOf(async function() {}).constructor)('github', 'context', 'core', script);
const name = 'desktop-remote-source-Windows';
const artifact = (id, created_at, extra = {}) => ({id, name, created_at, size_in_bytes: 100, expired: false, workflow_run: {id}, ...extra});
async function run(items, {active = [], unavailable = false, failDelete = false} = {}) {
  const deleted = [], log = [];
  const actions = {listArtifactsForRepo() {},
    getWorkflowRun: async ({run_id}) => ({data: {status: active.includes(run_id) ? 'in_progress' : 'completed'}}),
    getArtifact: async ({artifact_id}) => ({data: {...items.find(a => a.id === artifact_id), expired: unavailable}}),
    deleteArtifact: async ({artifact_id, owner, repo}) => {
      assert.equal(owner, 'fixture'); assert.equal(repo, 'only-this-repo');
      if (failDelete) throw Error('denied');
      deleted.push(artifact_id);
    }};
  const github = {rest: {actions}, paginate: async () => items};
  const core = {info: text => log.push(text), summary: {addRaw: () => ({write: async () => {}})}};
  try { await execute(github, {repo: {owner: 'fixture', repo: 'only-this-repo'}}, core); }
  catch (error) { return {deleted, error}; }
  return {deleted, log};
}
test('retains newest by timestamp per name, skips unknown/active/expired records', async () => {
  const items = [artifact(999, '2026-01-01'), artifact(1, '2026-01-03'), artifact(3, '2026-01-02'),
    artifact(4, '2026-01-04', {name: 'unrelated'}), artifact(5, '2026-01-05'),
    artifact(6, '2026-01-06', {expired: true}), artifact(7, '2026-01-02', {workflow_run: null}),
    artifact(8, '2026-01-01', {name: 'desktop-remote-android-ota-test'})];
  assert.deepEqual((await run(items, {active: [5]})).deleted, [3, 999]);
});
test('missing retained artifact or denied delete stops cleanup', async () => {
  const items = [artifact(1, '2026-01-01'), artifact(2, '2026-01-02')];
  for (const options of [{unavailable: true}, {failDelete: true}]) {
    const result = await run(items, options);
    assert.ok(result.error); assert.deepEqual(result.deleted, []);
  }
});
test('empty repo and one artifact per name delete nothing', async () => {
  assert.deepEqual((await run([])).deleted, []);
  assert.deepEqual((await run([artifact(1, '2026-01-01')])).deleted, []);
  assert.match(workflow, /cancel-in-progress: false/);
  assert.doesNotMatch(workflow, /actions\/checkout|pull_request_target|deleteRelease/);
});
