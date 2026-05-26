import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { findCommittedEvidenceFreshnessViolations } from '../check-committed-evidence-freshness.mjs';

function tempRepo() {
  return mkdtempSync(path.join(os.tmpdir(), 'ghatana-committed-evidence-freshness-'));
}

function write(root, relativePath, payload) {
  const fullPath = path.join(root, relativePath);
  mkdirSync(path.dirname(fullPath), { recursive: true });
  writeFileSync(fullPath, typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2));
}

test('passes when all committed evidence matches current HEAD', () => {
  const root = tempRepo();
  try {
    const commit = 'a'.repeat(40);
    write(root, '.kernel/evidence/product-release-readiness.json', {
      evidenceRun: { commit },
    });
    write(root, '.kernel/evidence/data-cloud-release-bundle.json', {
      evidenceRun: { commit },
      sourceCommitSha: commit,
      targetCommitSha: commit,
    });
    write(root, 'release-evidence/smoke/smoke-e2e-report.json', {
      evidenceRun: { commit },
    });

    assert.deepEqual(findCommittedEvidenceFreshnessViolations(root, { expectedCommit: commit }), []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects committed evidence with stale commit', () => {
  const root = tempRepo();
  try {
    write(root, '.kernel/evidence/product-release-readiness.json', {
      evidenceRun: { commit: 'b'.repeat(40) },
    });

    const violations = findCommittedEvidenceFreshnessViolations(root, { expectedCommit: 'a'.repeat(40) });

    assert.equal(violations.length, 1);
    assert.match(violations[0], /must match current HEAD/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('validates nested evidence in release bundles', () => {
  const root = tempRepo();
  try {
    const currentCommit = 'a'.repeat(40);
    const staleCommit = 'b'.repeat(40);
    
    write(root, '.kernel/evidence/data-cloud-release-bundle.json', {
      evidenceRun: { commit: currentCommit },
      items: {
        activeModuleEvidence: {
          present: true,
          payload: {
            evidenceRun: { commit: staleCommit },
          },
        },
      },
    });

    const violations = findCommittedEvidenceFreshnessViolations(root, { expectedCommit: currentCommit });

    assert.ok(violations.length > 0);
    assert.ok(violations.some((violation) => 
      violation.includes('activeModuleEvidence') &&
      violation.includes('must match current HEAD')
    ));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('checks both .kernel/evidence and release-evidence directories', () => {
  const root = tempRepo();
  try {
    const currentCommit = 'a'.repeat(40);
    const staleCommit = 'b'.repeat(40);
    
    write(root, '.kernel/evidence/current.json', {
      evidenceRun: { commit: currentCommit },
    });
    write(root, 'release-evidence/stale.json', {
      evidenceRun: { commit: staleCommit },
    });

    const violations = findCommittedEvidenceFreshnessViolations(root, { expectedCommit: currentCommit });

    assert.ok(violations.length > 0);
    assert.ok(violations.some((violation) => violation.includes('release-evidence/stale.json')));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('can skip specific evidence paths', () => {
  const root = tempRepo();
  try {
    const currentCommit = 'a'.repeat(40);
    const staleCommit = 'b'.repeat(40);
    
    write(root, '.kernel/evidence/stale.json', {
      evidenceRun: { commit: staleCommit },
    });
    write(root, '.kernel/evidence/current.json', {
      evidenceRun: { commit: currentCommit },
    });

    const violations = findCommittedEvidenceFreshnessViolations(root, {
      expectedCommit: currentCommit,
      skipEvidencePaths: ['.kernel/evidence/stale.json'],
    });

    assert.deepEqual(violations, []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects malformed commit SHAs', () => {
  const root = tempRepo();
  try {
    write(root, '.kernel/evidence/malformed.json', {
      evidenceRun: { commit: 'not-a-sha' },
    });

    const violations = findCommittedEvidenceFreshnessViolations(root, { expectedCommit: 'a'.repeat(40) });

    assert.equal(violations.length, 1);
    assert.match(violations[0], /40-character git SHA/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('returns error when unable to resolve git HEAD', () => {
  const root = tempRepo();
  try {
    const violations = findCommittedEvidenceFreshnessViolations(root, { expectedCommit: null });

    assert.equal(violations.length, 1);
    assert.match(violations[0], /Unable to resolve current git HEAD/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
