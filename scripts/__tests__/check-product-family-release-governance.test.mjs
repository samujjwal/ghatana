import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, '../..');

function runScript(relativePath) {
  return spawnSync(process.execPath, [path.join(repoRoot, relativePath)], {
    cwd: repoRoot,
    encoding: 'utf8',
  });
}

test('product-family promotion policy check passes and writes evidence', () => {
  const result = runScript('scripts/check-product-family-asset-promotion-policy.mjs');

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /promotion policy check passed/i);
  assert.equal(
    existsSync(path.join(repoRoot, '.kernel/evidence/product-family-asset-promotion-policy.json')),
    true,
  );
});

test('product release evidence-pack check passes and writes summary evidence', () => {
  const result = runScript('scripts/check-product-release-evidence-packs.mjs');

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /evidence-pack check passed/i);
  assert.equal(
    existsSync(path.join(repoRoot, '.kernel/evidence/product-release-evidence-packs.json')),
    true,
  );
});

test('yappc product-family control plane check passes', () => {
  const result = runScript('scripts/check-yappc-product-family-control-plane.mjs');

  assert.equal(result.status, 0, result.stderr || result.stdout);
  assert.match(result.stdout, /control plane check passed/i);
});
