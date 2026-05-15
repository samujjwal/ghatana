import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { ArtifactManifestGenerator } from '../domain/ArtifactManifest.js';
import { ArtifactFingerprintCalculator } from '../fingerprint/ArtifactFingerprintCalculator.js';

describe('ArtifactFingerprintCalculator', () => {
  let testDir: string;

  beforeEach(async () => {
    testDir = await fs.mkdtemp(path.join(os.tmpdir(), 'artifact-fingerprint-'));
  });

  afterEach(async () => {
    await fs.rm(testDir, { recursive: true, force: true });
  });

  it('calculates deterministic directory fingerprints and combined size', async () => {
    await fs.mkdir(path.join(testDir, 'dist', 'assets'), { recursive: true });
    await fs.writeFile(path.join(testDir, 'dist', 'index.html'), '<html></html>');
    await fs.writeFile(path.join(testDir, 'dist', 'assets', 'app.js'), 'console.log("app");');

    const calculator = new ArtifactFingerprintCalculator();
    const first = await calculator.calculateForPath(path.join(testDir, 'dist'));
    const second = await calculator.calculateForPath(path.join(testDir, 'dist'));

    expect(first.fingerprint.algorithm).toBe('sha256');
    expect(first.fingerprint.hash).toBe(second.fingerprint.hash);
    expect(first.sizeBytes).toBeGreaterThan(0);
  });

  it('ignores transient files when fingerprinting static web bundles', async () => {
    await fs.mkdir(path.join(testDir, 'dist', 'assets'), { recursive: true });
    await fs.mkdir(path.join(testDir, 'dist', '.vite'), { recursive: true });
    await fs.writeFile(path.join(testDir, 'dist', 'index.html'), '<html></html>');
    await fs.writeFile(path.join(testDir, 'dist', 'assets', 'app.js'), 'console.log("app");');

    const calculator = new ArtifactFingerprintCalculator();
    const baseline = await calculator.calculateForPath(path.join(testDir, 'dist'));

    await fs.writeFile(path.join(testDir, 'dist', '.DS_Store'), 'finder metadata');
    await fs.writeFile(path.join(testDir, 'dist', 'debug.log'), 'debug noise');
    await fs.writeFile(path.join(testDir, 'dist', '.vite', 'manifest.tmp'), 'vite cache');
    const withTransientFiles = await calculator.calculateForPath(path.join(testDir, 'dist'));

    expect(withTransientFiles.fingerprint.hash).toBe(baseline.fingerprint.hash);
    expect(withTransientFiles.sizeBytes).toBe(baseline.sizeBytes);
  });

  it('supports custom ignore patterns for generated directories', async () => {
    await fs.mkdir(path.join(testDir, 'dist', 'generated'), { recursive: true });
    await fs.writeFile(path.join(testDir, 'dist', 'index.html'), '<html></html>');

    const calculator = new ArtifactFingerprintCalculator({ ignorePatterns: ['generated/**'] });
    const baseline = await calculator.calculateForPath(path.join(testDir, 'dist'));

    await fs.writeFile(path.join(testDir, 'dist', 'generated', 'snapshot.json'), '{"generated":true}');
    const withGenerated = await calculator.calculateForPath(path.join(testDir, 'dist'));

    expect(withGenerated.fingerprint.hash).toBe(baseline.fingerprint.hash);
  });

  it('fingerprints individual files and ignores non-file directory entries when available', async () => {
    await fs.mkdir(path.join(testDir, 'dist', 'nested', '.cache'), { recursive: true });
    await fs.writeFile(path.join(testDir, 'dist', 'index.html'), '<html></html>');
    await fs.writeFile(path.join(testDir, 'dist', 'nested', 'chunk.js'), 'console.log("chunk");');
    await fs.writeFile(path.join(testDir, 'dist', 'nested', '.cache', 'ignored.json'), '{}');
    let symlinkCreated = false;
    try {
      await fs.symlink(path.join(testDir, 'dist', 'index.html'), path.join(testDir, 'dist', 'index-link.html'));
      symlinkCreated = true;
    } catch (error) {
      const errorCode = error instanceof Error && 'code' in error ? String(error.code) : '';
      if (errorCode !== 'EPERM' && errorCode !== 'UNKNOWN') {
        throw error;
      }
    }

    const calculator = new ArtifactFingerprintCalculator();
    const fileResult = await calculator.calculateForPath(path.join(testDir, 'dist', 'index.html'));
    const directoryResult = await calculator.calculateForPath(path.join(testDir, 'dist'));

    expect(fileResult.fingerprint.algorithm).toBe('sha256');
    expect(fileResult.sizeBytes).toBe('<html></html>'.length);
    expect(directoryResult.sizeBytes).toBe('<html></html>'.length + 'console.log("chunk");'.length);
    if (symlinkCreated) {
      const linkStats = await fs.lstat(path.join(testDir, 'dist', 'index-link.html'));
      expect(linkStats.isSymbolicLink()).toBe(true);
    }
  });

  it('creates manifests with semantic artifact packaging', () => {
    const generator = new ArtifactManifestGenerator();

    const manifest = generator.createManifest({
      productId: 'digital-marketing',
      phase: 'build',
      surface: 'web',
      artifacts: [
        {
          id: 'web-dist',
          path: 'products/digital-marketing/web/dist',
          metadata: {
            type: 'static-web-bundle',
            packaging: 'static-files',
            version: '1.0.0',
            buildNumber: '42',
            gitCommit: 'abcdef0',
            gitBranch: 'main',
            timestamp: new Date().toISOString(),
            sizeBytes: 1234,
          },
          fingerprint: {
            algorithm: 'sha256',
            hash: 'a'.repeat(64),
          },
          expected: true,
        },
      ],
    });

    expect(manifest.artifacts[0].metadata.packaging).toBe('static-files');
    expect(() => generator.validateManifest(manifest)).not.toThrow();
  });

  it('creates lifecycle-aware artifact manifests with provider and deployment refs', () => {
    const generator = new ArtifactManifestGenerator();

    const manifest = generator.createManifest({
      runId: 'run-123',
      correlationId: 'corr-123',
      productId: 'digital-marketing',
      productUnitId: 'product-unit:digital-marketing',
      providerMode: 'bootstrap',
      phase: 'package',
      sourceRef: 'git:abcdef0',
      generatedBy: {
        providerId: 'file-artifacts',
        adapterId: 'docker-buildx',
        toolchainId: 'docker',
        version: '1.0.0',
      },
      artifacts: [
        {
          id: 'api-image',
          path: 'ghatana/digital-marketing-api@sha256:abc123',
          metadata: {
            type: 'container-image',
            packaging: 'container',
            version: '1.0.0',
            gitCommit: 'abcdef0',
            gitBranch: 'main',
            timestamp: new Date().toISOString(),
            sizeBytes: 0,
            artifactRef: 'artifact:api-image',
            deploymentRefs: [
              {
                deploymentManifestRef: 'deployment-manifest.json',
                environment: 'local',
              },
            ],
          },
          fingerprint: {
            algorithm: 'sha256',
            hash: 'b'.repeat(64),
          },
          expected: true,
        },
      ],
    });

    expect(manifest).toMatchObject({
      runId: 'run-123',
      correlationId: 'corr-123',
      productUnitId: 'product-unit:digital-marketing',
      providerMode: 'bootstrap',
      sourceRef: 'git:abcdef0',
      generatedBy: { adapterId: 'docker-buildx' },
    });
    expect(manifest.surface).toBeUndefined();
    expect(manifest.artifacts[0].metadata.buildNumber).toBe('0');
    expect(manifest.artifacts[0].metadata.artifactRef).toBe('artifact:api-image');
    expect(() => generator.validateManifest(manifest)).not.toThrow();
  });

  it('validates backwards-compatible v1 artifact manifests without lifecycle fields', () => {
    const generator = new ArtifactManifestGenerator();
    const legacyManifest = {
      schemaVersion: '1.0.0',
      productId: 'digital-marketing',
      phase: 'build',
      surface: 'web',
      timestamp: new Date().toISOString(),
      artifacts: [
        {
          id: 'web-dist',
          path: 'products/digital-marketing/web/dist',
          metadata: {
            type: 'static-web-bundle',
            packaging: 'static-files',
            version: '1.0.0',
            buildNumber: '42',
            timestamp: new Date().toISOString(),
            sizeBytes: 1234,
          },
          fingerprint: {
            algorithm: 'sha256',
            hash: 'a'.repeat(64),
          },
          expected: true,
          found: true,
        },
      ],
    };

    expect(generator.validateManifest(legacyManifest)).toMatchObject({
      schemaVersion: '1.0.0',
      productId: 'digital-marketing',
      surface: 'web',
    });
  });

  it('reports missing and unexpected artifacts from a validated manifest', () => {
    const generator = new ArtifactManifestGenerator();
    const timestamp = new Date().toISOString();
    const manifest = generator.validateManifest({
      schemaVersion: '1.0.0',
      productId: 'digital-marketing',
      phase: 'build',
      timestamp,
      artifacts: [
        {
          id: 'missing-required',
          path: 'dist',
          metadata: {
            type: 'static-web-bundle',
            packaging: 'static-files',
            version: '1.0.0',
            buildNumber: '1',
            timestamp,
            sizeBytes: 0,
          },
          fingerprint: { algorithm: 'sha256', hash: 'a'.repeat(64) },
          expected: true,
          found: false,
        },
        {
          id: 'unexpected',
          path: 'debug.log',
          metadata: {
            type: 'documentation',
            packaging: 'json',
            version: '1.0.0',
            buildNumber: '1',
            timestamp,
            sizeBytes: 1,
          },
          fingerprint: { algorithm: 'sha256', hash: 'b'.repeat(64) },
          expected: false,
          found: true,
        },
      ],
    });

    const result = generator.validateArtifacts(manifest);

    expect(result.valid).toBe(false);
    expect(result.missing.map((artifact) => artifact.id)).toEqual(['missing-required']);
    expect(result.unexpected.map((artifact) => artifact.id)).toEqual(['unexpected']);
  });
});
