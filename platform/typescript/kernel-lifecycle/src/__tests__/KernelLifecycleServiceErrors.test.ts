import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import {
  type KernelLifecycleProviderContext,
  type RegistryProvider,
} from '@ghatana/kernel-product-contracts';
import {
  KernelLifecycleService,
  type KernelLifecycleLogger,
} from '../service/KernelLifecycleService.js';
import {
  LifecycleManifestCorruptError,
  LifecycleRunIndexUnavailableError,
} from '../service/KernelLifecycleErrors.js';

describe('KernelLifecycleService — hardened error paths', () => {
  let repoRoot: string;

  beforeEach(async () => {
    repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'kernel-lifecycle-errors-'));
  });

  afterEach(async () => {
    await fs.rm(repoRoot, { recursive: true, force: true });
    vi.restoreAllMocks();
  });

  function createService(extra?: { logger?: KernelLifecycleLogger }): KernelLifecycleService {
    return new KernelLifecycleService({
      repoRoot,
      registryProvider: createRegistryProvider(),
      providerContext: createProviderContext(),
      ...(extra?.logger !== undefined ? { logger: extra.logger } : {}),
    });
  }

  function outputRoot(): string {
    return path.join(repoRoot, '.kernel', 'out');
  }

  describe('listLifecycleRuns', () => {
    it('returns empty array when product unit directory does not exist', async () => {
      const service = createService();

      const runs = await service.listLifecycleRuns('nonexistent-product');

      expect(runs).toEqual([]);
    });

    it('throws LifecycleRunIndexUnavailableError when readdir fails for non-ENOENT reason', async () => {
      const productRoot = path.join(outputRoot(), 'products', 'digital-marketing');
      await fs.mkdir(productRoot, { recursive: true });

      vi.spyOn(fs, 'readdir').mockRejectedValueOnce(
        Object.assign(new Error('permission denied'), { code: 'EACCES' }),
      );

      const service = createService();

      await expect(service.listLifecycleRuns('digital-marketing')).rejects.toThrow(
        LifecycleRunIndexUnavailableError,
      );
    });

    it('logs an error before throwing LifecycleRunIndexUnavailableError', async () => {
      const productRoot = path.join(outputRoot(), 'products', 'digital-marketing');
      await fs.mkdir(productRoot, { recursive: true });

      vi.spyOn(fs, 'readdir').mockRejectedValueOnce(
        Object.assign(new Error('EPERM'), { code: 'EPERM' }),
      );

      const errorSpy = vi.fn();
      const service = createService({ logger: { info: vi.fn(), warn: vi.fn(), error: errorSpy } });

      await expect(service.listLifecycleRuns('digital-marketing')).rejects.toThrow(
        LifecycleRunIndexUnavailableError,
      );
      expect(errorSpy).toHaveBeenCalledWith(
        'Lifecycle truth read failed',
        expect.objectContaining({ reasonCode: 'lifecycle-run-index-unavailable' }),
      );
    });
  });

  describe('getLifecycleRun — corrupt JSON', () => {
    it('throws LifecycleManifestCorruptError when lifecycle-result.json contains invalid JSON', async () => {
      const runDir = path.join(outputRoot(), 'products', 'digital-marketing', 'build', 'run-1');
      await fs.mkdir(runDir, { recursive: true });
      await fs.writeFile(path.join(runDir, 'lifecycle-result.json'), '{ invalid json :::');

      const service = createService();

      await expect(service.getLifecycleRun('digital-marketing', 'run-1')).rejects.toThrow(
        LifecycleManifestCorruptError,
      );
    });

    it('throws LifecycleManifestCorruptError when lifecycle-plan.json contains invalid JSON', async () => {
      const runDir = path.join(outputRoot(), 'products', 'digital-marketing', 'build', 'run-1');
      await fs.mkdir(runDir, { recursive: true });
      await fs.writeFile(path.join(runDir, 'lifecycle-plan.json'), '<<<not json>>>');

      const service = createService();

      await expect(service.getLifecycleRun('digital-marketing', 'run-1')).rejects.toThrow(
        LifecycleManifestCorruptError,
      );
    });

    it('logs an error before throwing LifecycleManifestCorruptError for corrupt result JSON', async () => {
      const runDir = path.join(outputRoot(), 'products', 'digital-marketing', 'build', 'run-1');
      await fs.mkdir(runDir, { recursive: true });
      await fs.writeFile(path.join(runDir, 'lifecycle-result.json'), 'not-valid-json!');

      const errorSpy = vi.fn();
      const service = createService({ logger: { info: vi.fn(), warn: vi.fn(), error: errorSpy } });

      await expect(service.getLifecycleRun('digital-marketing', 'run-1')).rejects.toThrow(
        LifecycleManifestCorruptError,
      );
      expect(errorSpy).toHaveBeenCalledWith(
        'Lifecycle truth read failed',
        expect.objectContaining({ reasonCode: 'lifecycle-manifest-corrupt' }),
      );
    });
  });

  describe('LifecycleTruthReadError reasonCodes', () => {
    it('LifecycleRunIndexUnavailableError carries lifecycle-run-index-unavailable reasonCode', async () => {
      const productRoot = path.join(outputRoot(), 'products', 'dm');
      await fs.mkdir(productRoot, { recursive: true });
      vi.spyOn(fs, 'readdir').mockRejectedValueOnce(
        Object.assign(new Error('EBUSY'), { code: 'EBUSY' }),
      );

      const service = createService();

      const error = await service.listLifecycleRuns('dm').catch((e: unknown) => e);
      expect(error).toBeInstanceOf(LifecycleRunIndexUnavailableError);
      expect((error as LifecycleRunIndexUnavailableError).reasonCode).toBe('lifecycle-run-index-unavailable');
      expect((error as LifecycleRunIndexUnavailableError).safeDetails).toMatchObject({
        reasonCode: 'lifecycle-run-index-unavailable',
      });
    });

    it('LifecycleManifestCorruptError carries lifecycle-manifest-corrupt reasonCode and safeDetails', async () => {
      const runDir = path.join(outputRoot(), 'products', 'dm', 'build', 'run-x');
      await fs.mkdir(runDir, { recursive: true });
      await fs.writeFile(path.join(runDir, 'lifecycle-result.json'), '{corrupt}');

      const service = createService();

      const error = await service.getLifecycleRun('dm', 'run-x').catch((e: unknown) => e);
      expect(error).toBeInstanceOf(LifecycleManifestCorruptError);
      expect((error as LifecycleManifestCorruptError).reasonCode).toBe('lifecycle-manifest-corrupt');
      expect((error as LifecycleManifestCorruptError).safeDetails).toMatchObject({
        operation: 'readJsonIfExists',
      });
    });
  });
});

function createRegistryProvider(): RegistryProvider {
  return {
    providerId: 'test-registry',
    version: '1.0.0',
    capabilities: ['product-units'],
    getProductUnit: async () => null,
    listProductUnits: async () => [],
    listProductUnitsByKind: async () => [],
    validateProductUnit: async () => ({ valid: true, errors: [] }),
  } as unknown as RegistryProvider;
}

function createProviderContext(): KernelLifecycleProviderContext {
  return {
    mode: 'bootstrap',
    events: {
      providerId: 'events',
      version: '1.0.0',
      capabilities: ['events'],
      appendEvent: vi.fn().mockResolvedValue({ success: true, ref: 'events.jsonl' }),
      listEvents: vi.fn().mockResolvedValue([]),
    },
    artifacts: {
      providerId: 'artifacts',
      version: '1.0.0',
      capabilities: ['artifact-manifests'],
      recordArtifactManifest: vi.fn().mockResolvedValue({ success: true, ref: 'artifact.json' }),
      listArtifactManifests: vi.fn().mockResolvedValue([]),
    },
    health: {
      providerId: 'health',
      version: '1.0.0',
      capabilities: ['health'],
      recordHealthSnapshot: vi.fn().mockResolvedValue({ success: true, ref: 'health.json' }),
      getLatestHealthSnapshot: vi.fn().mockResolvedValue(null),
    },
    approvals: {
      providerId: 'approvals',
      version: '1.0.0',
      capabilities: ['approvals'],
      requestLifecycleApproval: vi.fn().mockResolvedValue({ success: true, ref: 'approval.json' }),
      decideLifecycleApproval: vi.fn().mockResolvedValue({ success: true, ref: 'approval.json' }),
    },
    provenance: {
      providerId: 'provenance',
      version: '1.0.0',
      capabilities: ['provenance'],
      recordProvenance: vi.fn().mockResolvedValue({ success: true, ref: 'provenance.json' }),
      listProvenance: vi.fn().mockResolvedValue([]),
    },
    memory: {
      providerId: 'memory',
      version: '1.0.0',
      capabilities: ['memory'],
      recordMemory: vi.fn().mockResolvedValue({ success: true, ref: 'memory.json' }),
      listMemory: vi.fn().mockResolvedValue([]),
    },
    runtimeTruth: {
      providerId: 'runtime-truth',
      version: '1.0.0',
      capabilities: ['runtime-truth'],
      recordRuntimeTruth: vi.fn().mockResolvedValue({ success: true, ref: 'runtime-truth.json' }),
      getRuntimeTruth: vi.fn().mockResolvedValue(null),
    },
  } as unknown as KernelLifecycleProviderContext;
}
