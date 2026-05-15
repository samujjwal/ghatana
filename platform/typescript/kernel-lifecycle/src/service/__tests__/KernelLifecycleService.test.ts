import { promises as fs } from 'node:fs';
import * as os from 'node:os';
import * as path from 'node:path';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  createExecutableProductUnit,
  type KernelLifecycleProviderContext,
  type ProductUnit,
  type RegistryProvider,
} from '@ghatana/kernel-product-contracts';
import type {
  ProductLifecyclePlan,
  ProductLifecycleResult,
} from '../../domain/ProductLifecyclePhase.js';
import type { ProductLifecycleExecutor } from '../../execution/ProductLifecycleExecutor.js';
import type { ProductLifecyclePlanner } from '../../planning/ProductLifecyclePlanner.js';
import { ProviderUnavailableError } from '../KernelLifecycleErrors.js';
import { KernelLifecycleService } from '../KernelLifecycleService.js';

describe('KernelLifecycleService', () => {
  let repoRoot: string;
  let productUnit: ProductUnit;

  beforeEach(async () => {
    repoRoot = await fs.mkdtemp(path.join(os.tmpdir(), 'ghatana-lifecycle-service-'));
    productUnit = createExecutableProductUnit({
      id: 'digital-marketing',
      name: 'Digital Marketing',
      kind: 'business-product',
      scope: {
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        projectId: 'project-1',
      },
      lifecycleProfile: 'standard-web-api-product',
      lifecycleStatus: 'enabled',
      registryProviderRef: { providerId: 'ghatana-file-registry' },
      sourceProviderRef: {
        providerId: 'ghatana-file-registry',
        config: { lifecycleConfigPath: 'products/digital-marketing/kernel-product.yaml' },
      },
      surfaces: [
        {
          id: 'web',
          type: 'web',
          implementationStatus: 'implemented',
          sourceRef: 'products/digital-marketing/frontend',
        },
      ],
    });
  });

  afterEach(async () => {
    await fs.rm(repoRoot, { recursive: true, force: true });
  });

  it('lists ProductUnits through the injected registry provider and scope filter', async () => {
    const service = new KernelLifecycleService({
      repoRoot,
      registryProvider: createRegistryProvider([productUnit]),
      providerContext: createBootstrapProviderContext(),
    });

    await expect(
      service.listProductUnits({
        scope: { tenantId: 'tenant-1', workspaceId: 'workspace-1', projectId: 'project-1' },
      }),
    ).resolves.toEqual([productUnit]);
    await expect(
      service.listProductUnits({
        scope: { tenantId: 'tenant-2', workspaceId: 'workspace-1', projectId: 'project-1' },
      }),
    ).resolves.toEqual([]);
  });

  it('creates a plan, persists it, and records runtime truth and provenance', async () => {
    const providerContext = createBootstrapProviderContext();
    const plan = createPlan(repoRoot);
    const planner = {
      plan: vi.fn().mockResolvedValue(plan),
    } as unknown as ProductLifecyclePlanner;
    const service = new KernelLifecycleService({
      repoRoot,
      planner,
      registryProvider: createRegistryProvider([productUnit]),
      providerContext,
      clock: () => '2026-05-14T00:00:00.000Z',
    });

    await expect(service.createLifecyclePlan('digital-marketing', 'build', {
      correlationId: 'corr-1',
    })).resolves.toBe(plan);
    await expect(
      fs.readFile(path.join(plan.outputDirectory, 'lifecycle-plan.json'), 'utf-8'),
    ).resolves.toContain('"runId": "run-1"');
    expect(providerContext.runtimeTruth?.recordRuntimeTruth).toHaveBeenCalledWith(
      expect.objectContaining({
        productUnitId: 'digital-marketing',
        runId: 'run-1',
        status: 'planned',
      }),
      expect.objectContaining({ required: true, correlationId: 'corr-1' }),
    );
    expect(providerContext.provenance?.recordProvenance).toHaveBeenCalledWith(
      expect.objectContaining({
        provenanceId: 'kernel-lifecycle:run-1',
        evidenceRefs: ['lifecycle-plan:run-1'],
      }),
      expect.objectContaining({ required: true, correlationId: 'corr-1' }),
    );
  });

  it('runs a dry-run build through the injected executor and writes latest pointers', async () => {
    const plan = createPlan(repoRoot);
    const result = createResult(repoRoot);
    const planner = {
      plan: vi.fn().mockResolvedValue(plan),
    } as unknown as ProductLifecyclePlanner;
    const executor = {
      executePlan: vi.fn().mockResolvedValue(result),
    } as unknown as ProductLifecycleExecutor;
    const service = new KernelLifecycleService({
      repoRoot,
      planner,
      executor,
      registryProvider: createRegistryProvider([productUnit]),
      providerContext: createBootstrapProviderContext(),
    });

    await expect(service.runLifecyclePhase('digital-marketing', 'build', {
      dryRun: true,
      correlationId: 'corr-1',
    })).resolves.toBe(result);
    expect(executor.executePlan).toHaveBeenCalledWith(
      plan,
      expect.objectContaining({ dryRun: true, outputDirectory: plan.outputDirectory }),
    );
    await expect(
      fs.readFile(path.join(repoRoot, '.kernel', 'out', 'products', 'digital-marketing', 'build', 'latest', 'run-id.txt'), 'utf-8'),
    ).resolves.toBe('run-1');
  });

  it('loads latest lifecycle-result manifest through pointer refs', async () => {
    const plan = createPlan(repoRoot);
    const result = createResult(repoRoot);
    const service = new KernelLifecycleService({
      repoRoot,
      planner: { plan: vi.fn().mockResolvedValue(plan) } as unknown as ProductLifecyclePlanner,
      executor: { executePlan: vi.fn().mockResolvedValue(result) } as unknown as ProductLifecycleExecutor,
      registryProvider: createRegistryProvider([productUnit]),
      providerContext: createBootstrapProviderContext(),
    });
    await service.runLifecyclePhase('digital-marketing', 'build', { dryRun: true });

    await expect(
      service.getManifest('digital-marketing', 'run-1', 'lifecycle-result', 'build'),
    ).resolves.toMatchObject({
      schemaVersion: '1.0.0',
      runId: 'run-1',
      productId: 'digital-marketing',
      status: 'succeeded',
    });
  });

  it('fails closed when platform mode lacks the memory provider', async () => {
    const service = new KernelLifecycleService({
      repoRoot,
      registryProvider: createRegistryProvider([productUnit]),
      providerContext: {
        ...createBootstrapProviderContext(),
        mode: 'platform',
        memory: undefined,
      },
    });

    await expect(service.listProductUnits()).rejects.toMatchObject<Partial<ProviderUnavailableError>>({
      reasonCode: 'provider-unavailable',
      safeDetails: { missingProviders: ['memory'] },
    });
  });
});

function createRegistryProvider(productUnits: readonly ProductUnit[]): RegistryProvider {
  return {
    providerId: 'test-registry',
    version: '1.0.0',
    capabilities: ['product-units'],
    getProductUnit: async (productUnitId) => productUnits.find((unit) => unit.id === productUnitId) ?? null,
    listProductUnits: async () => productUnits,
    listProductUnitsByKind: async (kind) => productUnits.filter((unit) => unit.kind === kind),
    validateProductUnit: async () => ({ valid: true, errors: [] }),
  };
}

function createBootstrapProviderContext(): KernelLifecycleProviderContext {
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
  };
}

function createPlan(repoRoot: string): ProductLifecyclePlan {
  return {
    schemaVersion: '1.0.0',
    runId: 'run-1',
    correlationId: 'corr-1',
    providerMode: 'bootstrap',
    productId: 'digital-marketing',
    phase: 'build',
    phaseMode: 'sequential',
    lifecycleProfile: 'standard-web-api-product',
    surfaces: [],
    gates: [],
    steps: [],
    expectedArtifacts: [],
    requiredManifests: ['lifecycle-result'],
    requiredPlugins: [],
    approvalRequirements: [],
    outputDirectory: path.join(repoRoot, '.kernel', 'out', 'products', 'digital-marketing', 'build', 'run-1'),
    estimatedDurationMs: 1,
  };
}

function createResult(repoRoot: string): ProductLifecycleResult {
  return {
    schemaVersion: '1.0.0',
    runId: 'run-1',
    correlationId: 'corr-1',
    providerMode: 'bootstrap',
    productId: 'digital-marketing',
    phase: 'build',
    status: 'succeeded',
    startedAt: '2026-05-14T00:00:00.000Z',
    completedAt: '2026-05-14T00:00:01.000Z',
    steps: [],
    gates: [],
    artifacts: [],
    manifestRefs: {
      lifecycleResult: path.join(repoRoot, '.kernel', 'out', 'products', 'digital-marketing', 'build', 'run-1', 'lifecycle-result.json'),
    },
    outputDirectory: path.join(repoRoot, '.kernel', 'out', 'products', 'digital-marketing', 'build', 'run-1'),
  };
}
