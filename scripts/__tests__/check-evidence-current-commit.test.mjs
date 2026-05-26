import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';

import { findEvidenceCurrentCommitViolations } from '../check-evidence-current-commit.mjs';

function tempRepo() {
  return mkdtempSync(path.join(os.tmpdir(), 'ghatana-evidence-current-commit-'));
}

function write(root, relativePath, payload) {
  const fullPath = path.join(root, relativePath);
  mkdirSync(path.dirname(fullPath), { recursive: true });
  writeFileSync(fullPath, typeof payload === 'string' ? payload : JSON.stringify(payload, null, 2));
}

test('passes JSON evidence with evidenceRun.commit matching the current commit', () => {
  const root = tempRepo();
  try {
    const commit = 'a'.repeat(40);
    write(root, '.kernel/evidence/product-release-readiness.json', {
      evidenceRun: { commit },
    });
    write(root, '.kernel/evidence/no-run-metadata.json', {
      generatedAt: '2026-05-24T00:00:00.000Z',
    });

    assert.deepEqual(findEvidenceCurrentCommitViolations(root, { expectedCommit: commit }), []);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects JSON evidence with stale evidenceRun.commit', () => {
  const root = tempRepo();
  try {
    write(root, '.kernel/evidence/product-release-readiness.json', {
      evidenceRun: { commit: 'b'.repeat(40) },
    });

    const violations = findEvidenceCurrentCommitViolations(root, { expectedCommit: 'a'.repeat(40) });

    assert.equal(violations.length, 1);
    assert.match(violations[0], /must match current HEAD/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('can skip product release readiness files during product readiness bootstrap', () => {
  const root = tempRepo();
  try {
    write(root, '.kernel/evidence/product-release-readiness.json', {
      evidenceRun: { commit: 'b'.repeat(40) },
    });
    write(root, '.kernel/evidence/product-release-readiness.phr.json', {
      evidenceRun: { commit: 'b'.repeat(40) },
    });
    write(root, '.kernel/evidence/data-cloud-release-runtime-profile.json', {
      evidenceRun: { commit: 'a'.repeat(40) },
    });

    assert.deepEqual(
      findEvidenceCurrentCommitViolations(root, {
        expectedCommit: 'a'.repeat(40),
        skipProductReleaseReadiness: true,
      }),
      [],
    );
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects malformed evidenceRun.commit values', () => {
  const root = tempRepo();
  try {
    write(root, '.kernel/evidence/ai-governance-behavioral-proof/latest.json', {
      evidenceRun: { commit: 'unknown' },
    });

    const violations = findEvidenceCurrentCommitViolations(root, { expectedCommit: 'a'.repeat(40) });

    assert.equal(violations.length, 1);
    assert.match(violations[0], /40-character git SHA/);
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects stale source and target commit metadata', () => {
  const root = tempRepo();
  try {
    write(root, '.kernel/evidence/data-cloud-release-bundle.json', {
      evidenceRun: {
        commit: 'a'.repeat(40),
        sourceCommitSha: 'a'.repeat(40),
        targetCommitSha: 'b'.repeat(40),
      },
      sourceCommitSha: 'a'.repeat(40),
      targetCommitSha: 'b'.repeat(40),
    });

    const violations = findEvidenceCurrentCommitViolations(root, { expectedCommit: 'a'.repeat(40) });

    assert.ok(violations.some((violation) => violation.includes('targetCommitSha')));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('rejects stale source and target commit metadata without evidenceRun', () => {
  const root = tempRepo();
  try {
    write(root, '.kernel/evidence/digital-marketing/google-ads-connector.json', {
      productId: 'digital-marketing',
      sourceCommitSha: 'b'.repeat(40),
      targetCommitSha: 'b'.repeat(40),
    });

    const violations = findEvidenceCurrentCommitViolations(root, { expectedCommit: 'a'.repeat(40) });

    assert.equal(violations.length, 2);
    assert.ok(violations.every((violation) => violation.includes('must match current HEAD')));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
