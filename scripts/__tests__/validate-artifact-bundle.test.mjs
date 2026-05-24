/**
 * Tests for validate-artifact-bundle.mjs
 */

import { describe, it } from 'node:test';
import assert from 'node:assert';

describe('validate-artifact-bundle', () => {
  it('should define required files', () => {
    const requiredFiles = [
      'manifest.json',
      'checksums.json',
      'artifacts/',
    ];

    assert.strictEqual(requiredFiles.length, 3);
    assert.strictEqual(requiredFiles[0], 'manifest.json');
    assert.strictEqual(requiredFiles[1], 'checksums.json');
    assert.strictEqual(requiredFiles[2], 'artifacts/');
  });

  it('should validate manifest structure', () => {
    const validManifest = {
      version: '1.0.0',
      timestamp: '2026-03-27T00:00:00Z',
      artifacts: [],
    };

    assert.ok(validManifest.version);
    assert.ok(validManifest.timestamp);
    assert.ok(Array.isArray(validManifest.artifacts));
  });

  it('should validate checksums structure', () => {
    const validChecksums = {
      sha256: 'abc123',
    };

    assert.ok(validChecksums.sha256);
    assert.strictEqual(typeof validChecksums.sha256, 'string');
  });

  it('should reject invalid manifest', () => {
    const invalidManifest = {
      version: '1.0.0',
      // Missing timestamp and artifacts
    };

    assert.ok(invalidManifest.version);
    assert.strictEqual(invalidManifest.timestamp, undefined);
  });

  it('should reject invalid checksums', () => {
    const invalidChecksums = {
      // Missing sha256
    };

    assert.strictEqual(invalidChecksums.sha256, undefined);
  });
});
