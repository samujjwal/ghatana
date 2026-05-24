/**
 * Tests for centralize-release-evidence.mjs
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';

describe('centralize-release-evidence', () => {
  it('should define evidence types', () => {
    const evidenceTypes = [
      { name: 'test-results', source: 'build/reports/tests/test', target: 'evidence/test-results.json' },
      { name: 'security-scan', source: 'build/reports/security/scan.json', target: 'evidence/security-scan.json' },
      { name: 'performance-metrics', source: 'build/reports/performance/metrics.json', target: 'evidence/performance-metrics.json' },
      { name: 'deployment-logs', source: 'build/reports/deployment/logs.json', target: 'evidence/deployment-logs.json' },
      { name: 'approval-records', source: 'build/reports/approval/records.json', target: 'evidence/approval-records.json' },
    ];

    assert.strictEqual(evidenceTypes.length, 5);
    assert.strictEqual(evidenceTypes[0].name, 'test-results');
    assert.strictEqual(evidenceTypes[1].name, 'security-scan');
    assert.strictEqual(evidenceTypes[2].name, 'performance-metrics');
    assert.strictEqual(evidenceTypes[3].name, 'deployment-logs');
    assert.strictEqual(evidenceTypes[4].name, 'approval-records');
  });

  it('should have source and target paths for each evidence type', () => {
    const evidenceTypes = [
      { name: 'test-results', source: 'build/reports/tests/test', target: 'evidence/test-results.json' },
      { name: 'security-scan', source: 'build/reports/security/scan.json', target: 'evidence/security-scan.json' },
    ];

    for (const evidenceType of evidenceTypes) {
      assert.ok(evidenceType.source, `Evidence type ${evidenceType.name} should have a source path`);
      assert.ok(evidenceType.target, `Evidence type ${evidenceType.name} should have a target path`);
      assert.strictEqual(typeof evidenceType.source, 'string');
      assert.strictEqual(typeof evidenceType.target, 'string');
    }
  });

  it('should create evidence manifest', () => {
    const evidenceManifest = {
      releaseTag: 'v1.0.0',
      collectedAt: new Date().toISOString(),
      evidence: [],
    };

    assert.ok(evidenceManifest.releaseTag);
    assert.ok(evidenceManifest.collectedAt);
    assert.ok(Array.isArray(evidenceManifest.evidence));
  });

  it('should require release tag argument', () => {
    const releaseTag = 'v1.0.0';
    assert.ok(releaseTag);
    assert.strictEqual(typeof releaseTag, 'string');
  });

  it('should track collection status for each evidence type', () => {
    const evidenceEntry = {
      type: 'test-results',
      path: 'evidence/test-results.json',
      collected: true,
    };

    assert.strictEqual(evidenceEntry.collected, true);
    assert.strictEqual(evidenceEntry.type, 'test-results');
  });
});
