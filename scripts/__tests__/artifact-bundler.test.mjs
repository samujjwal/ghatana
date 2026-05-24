#!/usr/bin/env node

/**
 * Tests for Artifact Bundler
 *
 * @doc.type test
 * @doc.phase Phase 8
 */

import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';
import { describe, it, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert';

import { createBundle, collectArtifacts, calculateChecksum } from '../artifact-bundler.mjs';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const ROOT_DIR = path.resolve(__dirname, '..', '..');
const TEST_EVIDENCE_DIR = path.join(ROOT_DIR, '.kernel', 'evidence-test');

describe('Artifact Bundler Tests (Phase 8)', () => {
  beforeEach(() => {
    // Create test evidence directory
    fs.mkdirSync(TEST_EVIDENCE_DIR, { recursive: true });
    fs.writeFileSync(path.join(TEST_EVIDENCE_DIR, 'test-evidence.json'), '{"test": true}');
  });

  afterEach(() => {
    // Clean up test directory
    if (fs.existsSync(TEST_EVIDENCE_DIR)) {
      fs.rmSync(TEST_EVIDENCE_DIR, { recursive: true, force: true });
    }
  });

  describe('calculateChecksum', () => {
    it('calculates SHA-256 checksum for a file', () => {
      const testFile = path.join(TEST_EVIDENCE_DIR, 'test-checksum.txt');
      fs.writeFileSync(testFile, 'test content');

      const checksum = calculateChecksum(testFile);

      assert.strictEqual(typeof checksum, 'string');
      assert.strictEqual(checksum.length, 64); // SHA-256 produces 64 hex characters
    });

    it('produces consistent checksums for same content', () => {
      const testFile = path.join(TEST_EVIDENCE_DIR, 'test-consistent.txt');
      fs.writeFileSync(testFile, 'consistent content');

      const checksum1 = calculateChecksum(testFile);
      const checksum2 = calculateChecksum(testFile);

      assert.strictEqual(checksum1, checksum2);
    });
  });

  describe('collectArtifacts', () => {
    it('collects evidence files from evidence directory', () => {
      const artifacts = collectArtifacts();

      assert.ok(Array.isArray(artifacts));
      assert.ok(artifacts.length > 0);

      const evidenceArtifacts = artifacts.filter(a => a.type === 'evidence');
      assert.ok(evidenceArtifacts.length > 0);
    });

    it('includes relative path for each artifact', () => {
      const artifacts = collectArtifacts();

      for (const artifact of artifacts) {
        assert.ok(artifact.relativePath);
        assert.ok(typeof artifact.relativePath === 'string');
      }
    });

    it('includes artifact type for each artifact', () => {
      const artifacts = collectArtifacts();

      for (const artifact of artifacts) {
        assert.ok(artifact.type);
        assert.ok(['evidence', 'test-results'].includes(artifact.type));
      }
    });
  });

  describe('createBundle', () => {
    it('creates bundle manifest for given version', () => {
      const result = createBundle('1.0.0-test');

      assert.ok(result.manifestPath);
      assert.ok(fs.existsSync(result.manifestPath));
      assert.ok(typeof result.artifactCount === 'number');
      assert.ok(result.checksum);
    });

    it('creates manifest with version and timestamp', () => {
      const result = createBundle('1.0.0-test');

      const manifestContent = fs.readFileSync(result.manifestPath, 'utf-8');
      const manifest = JSON.parse(manifestContent);

      assert.strictEqual(manifest.version, '1.0.0-test');
      assert.ok(manifest.timestamp);
      assert.ok(Array.isArray(manifest.artifacts));
    });

    it('includes checksums in manifest', () => {
      const result = createBundle('1.0.0-test');

      const manifestContent = fs.readFileSync(result.manifestPath, 'utf-8');
      const manifest = JSON.parse(manifestContent);

      assert.ok(manifest.checksums);
      assert.ok(typeof manifest.checksums === 'object');
    });
  });
});
