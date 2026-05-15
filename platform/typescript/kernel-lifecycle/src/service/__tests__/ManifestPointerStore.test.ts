import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { KernelLifecycleError, ManifestNotFoundError } from '../KernelLifecycleErrors.js';
import { ManifestPointerStore } from '../ManifestPointerStore.js';

describe('ManifestPointerStore', () => {
  let repoRoot: string;

  beforeEach(async () => {
    repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'ghatana-pointer-store-'));
  });

  afterEach(async () => {
    await fs.rm(repoRoot, { recursive: true, force: true });
  });

  it('writes latest manifest-pointers.json and run-id.txt atomically', async () => {
    const store = new ManifestPointerStore({ repoRoot });

    await store.writeLatestPointers('digital-marketing', 'build', {
      runId: 'run-1',
      correlationId: 'corr-1',
      providerMode: 'bootstrap',
      lifecycleResult: path.join(repoRoot, '.kernel', 'out', 'products', 'digital-marketing', 'build', 'run-1', 'lifecycle-result.json'),
    });

    await expect(store.readLatestRunId('digital-marketing', 'build')).resolves.toBe('run-1');
    await expect(store.readLatestPointers('digital-marketing', 'build')).resolves.toMatchObject({
      runId: 'run-1',
      correlationId: 'corr-1',
      providerMode: 'bootstrap',
    });
  });

  it('resolves relative refs written by lifecycle manifests under the output root', async () => {
    const store = new ManifestPointerStore({ repoRoot });
    const manifestRef = path.join('products', 'digital-marketing', 'build', 'run-1', 'artifact-manifest.json');
    const manifestPath = path.join(repoRoot, '.kernel', 'out', manifestRef);
    await fs.mkdir(path.dirname(manifestPath), { recursive: true });
    await fs.writeFile(manifestPath, '{"ok":true}\n', 'utf-8');
    await store.writeLatestPointers('digital-marketing', 'build', {
      runId: 'run-1',
      correlationId: 'corr-1',
      providerMode: 'bootstrap',
      artifactManifest: manifestRef,
    });

    await expect(
      store.resolveManifestByType('digital-marketing', 'run-1', 'build', 'artifact-manifest'),
    ).resolves.toBe(manifestPath);
  });

  it('returns manifest-not-found for missing manifests', async () => {
    const store = new ManifestPointerStore({ repoRoot });

    await expect(
      store.resolveManifestByType('digital-marketing', 'run-missing', 'build', 'lifecycle-result'),
    ).rejects.toMatchObject<Partial<ManifestNotFoundError>>({
      reasonCode: 'manifest-not-found',
      productUnitId: 'digital-marketing',
      runId: 'run-missing',
      phase: 'build',
    });
  });

  it('rejects output roots outside .kernel/out by default', () => {
    expect(
      () => new ManifestPointerStore({ repoRoot, outputRoot: 'outside' }),
    ).toThrow(KernelLifecycleError);
    expect(
      () => new ManifestPointerStore({ repoRoot, outputRoot: 'outside' }),
    ).toThrow('Kernel lifecycle output root must be inside');
  });

  it('lists run directories without including latest', async () => {
    const store = new ManifestPointerStore({ repoRoot });
    await fs.mkdir(path.join(repoRoot, '.kernel', 'out', 'products', 'digital-marketing', 'build', 'run-1'), { recursive: true });
    await fs.mkdir(path.join(repoRoot, '.kernel', 'out', 'products', 'digital-marketing', 'build', 'latest'), { recursive: true });

    await expect(store.listRunDirectories('digital-marketing', 'build')).resolves.toEqual([
      path.join(repoRoot, '.kernel', 'out', 'products', 'digital-marketing', 'build', 'run-1'),
    ]);
  });
});
