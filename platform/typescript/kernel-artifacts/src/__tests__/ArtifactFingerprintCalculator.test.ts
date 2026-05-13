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
});