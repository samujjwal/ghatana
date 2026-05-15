import { describe, expect, it } from 'vitest';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import type { ArtifactManifest } from '../domain/ArtifactManifest.js';
import { ProductArtifactValidator } from '../validator/ProductArtifactValidator.js';

describe('ProductArtifactValidator', () => {
  const validator = new ProductArtifactValidator();

  it('validates expected artifacts against manifest entries', () => {
    const manifest = createManifest([
      createArtifact({
        id: 'web-dist',
        type: 'static-web-bundle',
        packaging: 'static-files',
        artifactRef: 'artifact:web-dist',
      }),
    ]);

    const result = validator.validateExpectedArtifacts({
      manifest,
      expectedArtifacts: [
        {
          id: 'web-dist',
          type: 'static-web-bundle',
          packaging: 'static-files',
          required: true,
          artifactRef: 'artifact:web-dist',
        },
      ],
    });

    expect(result).toEqual({ valid: true, errors: [], missing: [] });
  });

  it('returns a missing required artifact failure result', () => {
    const result = validator.validateExpectedArtifacts({
      manifest: createManifest([]),
      expectedArtifacts: [
        {
          id: 'web-dist',
          type: 'static-web-bundle',
          packaging: 'static-files',
          required: true,
        },
      ],
    });

    expect(result.valid).toBe(false);
    expect(result.missing).toEqual([
      {
        reasonCode: 'artifact-missing',
        artifactId: 'web-dist',
        required: true,
        message: 'Expected artifact web-dist is missing',
      },
    ]);
    expect(result.errors[0]).toMatchObject({ path: 'artifacts.web-dist' });
  });

  it('tracks optional missing artifacts without failing validation', () => {
    const result = validator.validateExpectedArtifacts({
      manifest: createManifest([]),
      expectedArtifacts: [
        {
          id: 'coverage',
          type: 'coverage-report',
          packaging: 'json',
          required: false,
        },
      ],
    });

    expect(result.valid).toBe(true);
    expect(result.missing).toHaveLength(1);
    expect(result.errors).toHaveLength(0);
  });

  it('validates artifact type and packaging against product config', () => {
    const manifest = createManifest([
      createArtifact({
        id: 'api-jar',
        type: 'jvm-service',
        packaging: 'jar',
      }),
    ]);

    const result = validator.validateExpectedArtifacts({
      manifest,
      expectedArtifacts: [
        {
          id: 'api-jar',
          type: 'container-image',
          packaging: 'container',
          required: true,
        },
      ],
    });

    expect(result.valid).toBe(false);
    expect(result.errors.map((error) => error.path)).toEqual([
      'artifacts.api-jar.metadata.type',
      'artifacts.api-jar.metadata.packaging',
    ]);
  });

  it('requires digest refs for container images when configured', () => {
    const manifest = createManifest([
      createArtifact({
        id: 'api-image',
        path: 'ghatana/api:local',
        type: 'container-image',
        packaging: 'container',
      }),
    ]);

    const result = validator.validateExpectedArtifacts({
      manifest,
      expectedArtifacts: [
        {
          id: 'api-image',
          type: 'container-image',
          packaging: 'container',
          required: true,
          requireDigest: true,
        },
      ],
    });

    expect(result.valid).toBe(false);
    expect(result.errors[0].message).toContain('sha256 digest');
  });

  it('accepts container image digest refs from artifactRef', () => {
    const manifest = createManifest([
      createArtifact({
        id: 'api-image',
        path: 'ghatana/api:local',
        type: 'container-image',
        packaging: 'container',
        artifactRef: 'ghatana/api@sha256:abc123',
      }),
    ]);

    const result = validator.validateExpectedArtifacts({
      manifest,
      expectedArtifacts: [
        {
          id: 'api-image',
          type: 'container-image',
          packaging: 'container',
          required: true,
          requireDigest: true,
        },
      ],
    });

    expect(result.valid).toBe(true);
  });

  it('matches expected artifacts by artifactRef when ids differ', () => {
    const manifest = createManifest([
      createArtifact({
        id: 'generated-id',
        type: 'static-web-bundle',
        packaging: 'static-files',
        artifactRef: 'artifact:web',
      }),
    ]);

    const result = validator.validateExpectedArtifacts({
      manifest,
      expectedArtifacts: [
        {
          id: 'web',
          type: 'static-web-bundle',
          packaging: 'static-files',
          required: true,
          artifactRef: 'artifact:web',
        },
      ],
    });

    expect(result.valid).toBe(true);
  });

  it('validates individual artifact fields and fingerprint shape', () => {
    const artifact = createArtifact({
      id: '',
      path: '',
      type: 'static-web-bundle',
      packaging: 'static-files',
    });
    artifact.metadata.version = '';
    artifact.metadata.sizeBytes = -1;
    artifact.fingerprint.hash = 'not-hex';

    const errors = validator.validateArtifact(artifact);

    expect(errors.map((error) => error.path)).toEqual(
      expect.arrayContaining([
        'id',
        'path',
        'metadata.version',
        'fingerprint.hash',
        'metadata.sizeBytes',
      ]),
    );
  });

  it('reports missing metadata type and empty fingerprint hash', () => {
    const artifact = createArtifact({
      id: 'bad-artifact',
      type: 'static-web-bundle',
      packaging: 'static-files',
    });
    artifact.metadata.type = '' as typeof artifact.metadata.type;
    artifact.fingerprint = {
      algorithm: 'md5' as typeof artifact.fingerprint.algorithm,
      hash: '',
    };

    const errors = validator.validateArtifact(artifact);

    expect(errors.map((error) => error.path)).toEqual(
      expect.arrayContaining(['metadata.type', 'fingerprint.algorithm', 'fingerprint.hash']),
    );
  });

  it('validates artifact type and packaging enums', () => {
    expect(validator.validateArtifactType('static-web-bundle')).toHaveLength(0);
    expect(validator.validateArtifactType('bad-type')).toEqual([
      expect.objectContaining({ path: 'type' }),
    ]);
    expect(validator.validatePackaging('container')).toHaveLength(0);
    expect(validator.validatePackaging('bad-packaging')).toEqual([
      expect.objectContaining({ path: 'packaging' }),
    ]);
  });

  it('validates artifact path existence and size', async () => {
    const tempDir = await fs.mkdtemp(path.join(os.tmpdir(), 'artifact-validator-'));
    try {
      const filePath = path.join(tempDir, 'artifact.txt');
      await fs.writeFile(filePath, 'artifact');

      await expect(validator.validateArtifactPathExists(filePath)).resolves.toHaveLength(0);
      await expect(validator.validateArtifactSize(filePath, 'artifact'.length)).resolves.toHaveLength(0);
      await expect(validator.validateArtifactPathExists(path.join(tempDir, 'missing.txt'))).resolves.toEqual([
        expect.objectContaining({ path: 'path' }),
      ]);
      await expect(validator.validateArtifactSize(filePath, 999)).resolves.toEqual([
        expect.objectContaining({ path: 'size' }),
      ]);
      await expect(validator.validateArtifactSize(path.join(tempDir, 'missing.txt'), 1)).resolves.toEqual([
        expect.objectContaining({ path: 'path' }),
      ]);
    } finally {
      await fs.rm(tempDir, { recursive: true, force: true });
    }
  });
});

function createManifest(artifacts: ArtifactManifest['artifacts']): ArtifactManifest {
  return {
    schemaVersion: '1.0.0',
    productId: 'digital-marketing',
    phase: 'package',
    timestamp: new Date().toISOString(),
    artifacts,
  };
}

function createArtifact(params: {
  id: string;
  path?: string;
  type: ArtifactManifest['artifacts'][number]['metadata']['type'];
  packaging: ArtifactManifest['artifacts'][number]['metadata']['packaging'];
  artifactRef?: string;
}): ArtifactManifest['artifacts'][number] {
  return {
    id: params.id,
    path: params.path ?? params.id,
    metadata: {
      type: params.type,
      packaging: params.packaging,
      version: '1.0.0',
      buildNumber: '1',
      gitCommit: 'abcdef0',
      gitBranch: 'main',
      timestamp: new Date().toISOString(),
      sizeBytes: 1,
      ...(params.artifactRef !== undefined ? { artifactRef: params.artifactRef } : {}),
    },
    fingerprint: {
      algorithm: 'sha256',
      hash: 'a'.repeat(64),
    },
    expected: true,
    found: true,
  };
}
