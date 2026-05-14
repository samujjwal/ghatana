import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { mkdtemp, rm, readFile, writeFile } from 'node:fs/promises';
import * as path from 'node:path';
import * as os from 'node:os';
import { ArtifactWriter, ContainerImageArtifact } from '../ArtifactWriter.js';
import type { ProductArtifact } from '../../domain/ProductLifecyclePhase.js';

describe('ArtifactWriter', () => {
  let tmpDir: string;
  let writer: ArtifactWriter;

  beforeEach(async () => {
    tmpDir = await mkdtemp(path.join(os.tmpdir(), 'artifact-writer-test-'));
    writer = new ArtifactWriter();
  });

  afterEach(async () => {
    await rm(tmpDir, { recursive: true, force: true });
  });

  describe('writeArtifactManifest', () => {
    it('writes a valid artifact manifest to disk', async () => {
      const artifacts: ProductArtifact[] = [
        {
          id: 'jar-artifact',
          surface: 'backend-api',
          type: 'jvm-service',
          path: 'products/my-api/build/libs/my-api.jar',
          fingerprint: 'abc123',
          producedBy: 'gradle-java-service',
          sizeBytes: 1024,
        },
      ];

      const manifestPath = path.join(tmpDir, 'artifact-manifest.json');
      await writer.writeArtifactManifest(artifacts, manifestPath);

      const raw = await readFile(manifestPath, 'utf-8');
      const parsed = JSON.parse(raw) as Record<string, unknown>;

      expect(parsed.schemaVersion).toBe('1.0.0');
      expect(typeof parsed.generatedAt).toBe('string');
      expect(Array.isArray(parsed.artifacts)).toBe(true);
      const arts = parsed.artifacts as ProductArtifact[];
      expect(arts[0].id).toBe('jar-artifact');
      expect(arts[0].type).toBe('jvm-service');
    });

    it('creates nested output directories if they do not exist', async () => {
      const nestedPath = path.join(tmpDir, 'a', 'b', 'c', 'manifest.json');
      await writer.writeArtifactManifest([], nestedPath);

      const content = await readFile(nestedPath, 'utf-8');
      const parsed = JSON.parse(content) as Record<string, unknown>;
      expect(parsed.artifacts).toEqual([]);
    });
  });

  describe('readArtifactManifest', () => {
    it('reads and parses an existing artifact manifest', async () => {
      const manifest = {
        schemaVersion: '1.0.0',
        generatedAt: new Date().toISOString(),
        artifacts: [{ id: 'x', surface: 'web', type: 'static-web-bundle', path: 'dist/', fingerprint: 'ff00', producedBy: 'pnpm-vite-react' }],
      };
      const filePath = path.join(tmpDir, 'manifest.json');
      await writeFile(filePath, JSON.stringify(manifest), 'utf-8');

      const result = await writer.readArtifactManifest(filePath);
      expect(result.schemaVersion).toBe('1.0.0');
      expect(result.artifacts).toHaveLength(1);
      expect(result.artifacts[0].id).toBe('x');
    });
  });

  describe('writeContainerImageArtifact', () => {
    it('writes a container image artifact manifest and returns the ProductArtifact', async () => {
      const containerArtifact: ContainerImageArtifact = {
        id: 'dmos-api-image',
        surface: 'backend-api',
        producedBy: 'docker-buildx',
        image: 'ghatana/digital-marketing-api',
        tag: 'local',
        digest: 'sha256:abc123',
        localImageId: 'sha256:short123',
      };

      const result = await writer.writeContainerImageArtifact(containerArtifact, tmpDir);

      expect(result.type).toBe('container-image');
      expect(result.image).toBe('ghatana/digital-marketing-api');
      expect(result.tag).toBe('local');
      expect(result.digest).toBe('sha256:abc123');
      expect(result.localImageId).toBe('sha256:short123');
      expect(result.path).toBe('ghatana/digital-marketing-api:local');
      expect(result.fingerprint).toBe('sha256:abc123');

      // Verify file was written
      const manifestPath = path.join(tmpDir, 'artifact-manifest.json');
      const raw = await readFile(manifestPath, 'utf-8');
      const parsed = JSON.parse(raw) as { artifacts: ProductArtifact[] };
      expect(parsed.artifacts).toHaveLength(1);
      expect(parsed.artifacts[0].image).toBe('ghatana/digital-marketing-api');
    });

    it('handles missing digest gracefully (empty fingerprint)', async () => {
      const containerArtifact: ContainerImageArtifact = {
        id: 'no-digest',
        surface: 'web',
        producedBy: 'docker-buildx',
        image: 'ghatana/dmos-ui',
        tag: 'latest',
      };

      const result = await writer.writeContainerImageArtifact(containerArtifact, tmpDir);
      expect(result.fingerprint).toBe('');
      expect(result.digest).toBeUndefined();
    });
  });

  describe('calculateFingerprint', () => {
    it('returns a deterministic SHA-256 hex fingerprint', async () => {
      const filePath = path.join(tmpDir, 'testfile.txt');
      await writeFile(filePath, 'hello kernel', 'utf-8');

      const fingerprint1 = await writer.calculateFingerprint(filePath);
      const fingerprint2 = await writer.calculateFingerprint(filePath);

      expect(fingerprint1).toBe(fingerprint2);
      expect(fingerprint1).toMatch(/^[a-f0-9]{64}$/);
    });
  });

  describe('verifyFingerprint', () => {
    it('returns true when fingerprint matches file contents', async () => {
      const filePath = path.join(tmpDir, 'artifact.txt');
      await writeFile(filePath, 'test content', 'utf-8');

      const fingerprint = await writer.calculateFingerprint(filePath);
      const valid = await writer.verifyFingerprint(filePath, fingerprint);
      expect(valid).toBe(true);
    });

    it('returns false when fingerprint does not match', async () => {
      const filePath = path.join(tmpDir, 'artifact.txt');
      await writeFile(filePath, 'test content', 'utf-8');

      const valid = await writer.verifyFingerprint(filePath, 'wrongfingerprint');
      expect(valid).toBe(false);
    });
  });

  describe('copyArtifact', () => {
    it('copies a file to the target path, creating directories', async () => {
      const srcPath = path.join(tmpDir, 'source.jar');
      await writeFile(srcPath, 'jar-contents', 'utf-8');

      const destPath = path.join(tmpDir, 'out', 'nested', 'copy.jar');
      await writer.copyArtifact(srcPath, destPath);

      const content = await readFile(destPath, 'utf-8');
      expect(content).toBe('jar-contents');
    });
  });
});
