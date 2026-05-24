import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { checkEvidenceRunMetadata } from '../check-evidence-run-metadata.mjs';

function tempRepo() {
  return mkdtempSync(path.join(os.tmpdir(), 'ghatana-evidence-run-metadata-'));
}

function write(root, relativePath, payload) {
  const fullPath = path.join(root, relativePath);
  mkdirSync(path.dirname(fullPath), { recursive: true });
  writeFileSync(fullPath, typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2));
}

const criticalEvidence = [{
  path: '.kernel/evidence/example.json',
  expectedSource: 'scripts/example-check.mjs',
  expectedCommand: 'pnpm check:example',
}];

test('passes generated evidence with command source and commit metadata', () => {
  const root = tempRepo();
  try {
    write(root, 'scripts/example-check.mjs', 'console.log("ok");\n');
    write(root, '.kernel/evidence/example.json', {
      generatedAt: '2026-05-24T00:00:00.000Z',
      evidenceRun: {
        generatedBy: 'scripts/example-check.mjs',
        source: 'scripts/example-check.mjs',
        command: 'pnpm check:example',
        commit: 'a'.repeat(40),
      },
    });

    assert.deepEqual(checkEvidenceRunMetadata(root, criticalEvidence), []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects evidence without matching script-run metadata', () => {
  const root = tempRepo();
  try {
    write(root, 'scripts/example-check.mjs', 'console.log("ok");\n');
    write(root, '.kernel/evidence/example.json', {
      generatedAt: '2026-05-24T00:00:00.000Z',
    });

    const violations = checkEvidenceRunMetadata(root, criticalEvidence);

    assert.equal(violations.length, 1);
    assert.match(violations[0], /missing evidenceRun metadata/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects timestamp-only edits with wrong command metadata', () => {
  const root = tempRepo();
  try {
    write(root, 'scripts/example-check.mjs', 'console.log("ok");\n');
    write(root, '.kernel/evidence/example.json', {
      generatedAt: '2026-05-24T00:00:00.000Z',
      evidenceRun: {
        generatedBy: 'scripts/example-check.mjs',
        source: 'scripts/example-check.mjs',
        command: 'manual edit',
        commit: 'a'.repeat(40),
      },
    });

    const violations = checkEvidenceRunMetadata(root, criticalEvidence);

    assert.ok(violations.some((violation) => violation.includes('evidenceRun.command')));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
