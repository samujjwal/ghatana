import { describe, expect, it, vi } from 'vitest';
import type { ProductUnit } from '@ghatana/kernel-product-contracts';
import type {
  ProductLifecyclePlan,
  ProductLifecycleResult,
} from '../../domain/ProductLifecyclePhase.js';
import { ManifestNotFoundError } from '../../service/KernelLifecycleErrors.js';
import type { KernelLifecycleService } from '../../service/KernelLifecycleService.js';
import { KernelLifecycleApiHandlers } from '../KernelLifecycleApiHandlers.js';

describe('KernelLifecycleApiHandlers', () => {
  it('rejects missing tenant header when scope is required', async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireScopeHeaders: true,
      requireAuthentication: false,
    });

    const response = await handlers.listProductUnits({
      headers: { 'X-Correlation-Id': 'corr-1' },
    });

    expect(response.statusCode).toBe(403);
    expect(response.body).toMatchObject({
      reasonCode: 'scope-headers-required',
      correlationId: 'corr-1',
      safeDetails: {
        missingHeaders: [
          'X-Ghatana-Tenant-Id',
          'X-Ghatana-Workspace-Id',
          'X-Ghatana-Project-Id',
        ],
      },
    });
  });

  it('returns ProductUnit list from service with propagated scope and correlation ID', async () => {
    const service = createService();
    const handlers = new KernelLifecycleApiHandlers({ service, requireAuthentication: false });

    const response = await handlers.listProductUnits({
      headers: scopedHeaders('corr-2'),
    });

    expect(response.statusCode).toBe(200);
    expect(response.headers['x-correlation-id']).toBe('corr-2');
    expect(response.body).toEqual([productUnit]);
    expect(service.listProductUnits).toHaveBeenCalledWith({
      correlationId: 'corr-2',
      scope: { tenantId: 'tenant-1', workspaceId: 'workspace-1', projectId: 'project-1' },
    });
  });

  it('creates lifecycle plans and returns Studio-compatible plan response', async () => {
    const service = createService();
    const handlers = new KernelLifecycleApiHandlers({ service, requireAuthentication: false });

    const response = await handlers.createLifecyclePlan({
      params: { productUnitId: 'digital-marketing' },
      headers: scopedHeaders('corr-3'),
      body: {
        phase: 'build',
        environment: 'local',
      },
    });

    expect(response.statusCode).toBe(201);
    expect(response.body).toMatchObject({
      runId: 'run-1',
      productUnitId: 'digital-marketing',
      phase: 'build',
      status: 'planned',
    });
    expect(service.createLifecyclePlan).toHaveBeenCalledWith(
      'digital-marketing',
      'build',
      expect.objectContaining({
        correlationId: 'corr-3',
        environment: 'local',
      }),
    );
  });

  it('executes dry-run lifecycle phase and returns run response', async () => {
    const service = createService();
    const handlers = new KernelLifecycleApiHandlers({ service, requireAuthentication: false });

    const response = await handlers.executeLifecyclePhase({
      params: { productUnitId: 'digital-marketing' },
      headers: scopedHeaders('corr-4'),
      body: {
        phase: 'build',
        dryRun: true,
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.body).toMatchObject({
      runId: 'run-1',
      productUnitId: 'digital-marketing',
      status: 'healthy',
    });
    expect(service.runLifecyclePhase).toHaveBeenCalledWith(
      'digital-marketing',
      'build',
      expect.objectContaining({ dryRun: true, correlationId: 'corr-4' }),
    );
  });

  it('returns manifest-not-found with safe error response', async () => {
    const service = createService({
      getManifest: vi.fn().mockRejectedValue(new ManifestNotFoundError('No manifest', {
        correlationId: 'corr-5',
        productUnitId: 'digital-marketing',
        runId: 'run-1',
      })),
    });
    const handlers = new KernelLifecycleApiHandlers({ service, requireAuthentication: false });

    const response = await handlers.getArtifactManifest({
      params: { productUnitId: 'digital-marketing', runId: 'run-1' },
      headers: scopedHeaders('corr-5'),
    });

    expect(response.statusCode).toBe(404);
    expect(response.body).toMatchObject({
      reasonCode: 'manifest-not-found',
      correlationId: 'corr-5',
      productUnitId: 'digital-marketing',
      runId: 'run-1',
    });
  });

  it('lists pending approvals via the approval queue endpoint', async () => {
    const service = createService({
      listPendingApprovals: vi.fn().mockResolvedValue([
        {
          approvalId: 'approval-1',
          productUnitId: 'digital-marketing',
          runId: 'run-1',
          requestedBy: 'release-manager',
          reason: 'Deploy',
          requiredApprovers: ['alice'],
          expiresAt: '2026-05-16T00:00:00.000Z',
        },
      ]),
    });
    const handlers = new KernelLifecycleApiHandlers({ service, requireAuthentication: false });

    const response = await handlers.listPendingApprovals({
      headers: scopedHeaders('corr-7'),
      query: { productUnitId: 'digital-marketing', runId: 'run-1' },
    });

    expect(response.statusCode).toBe(200);
    expect(response.body).toEqual([
      expect.objectContaining({ approvalId: 'approval-1', productUnitId: 'digital-marketing' }),
    ]);
    expect(service.listPendingApprovals).toHaveBeenCalledWith(
      expect.objectContaining({
        productUnitId: 'digital-marketing',
        runId: 'run-1',
        correlationId: 'corr-7',
      }),
    );
  });

  it('rejects invalid lifecycle phase requests before calling service', async () => {
    const service = createService();
    const handlers = new KernelLifecycleApiHandlers({ service, requireAuthentication: false });

    const response = await handlers.createLifecyclePlan({
      params: { productUnitId: 'digital-marketing' },
      headers: scopedHeaders('corr-6'),
      body: { phase: 'not-a-phase' },
    });

    expect(response.statusCode).toBe(400);
    expect(response.body).toMatchObject({
      reasonCode: 'invalid-request',
      correlationId: 'corr-6',
    });
    expect(service.createLifecyclePlan).not.toHaveBeenCalled();
  });
});

const productUnit: ProductUnit = {
  schemaVersion: '1.0.0',
  id: 'digital-marketing',
  name: 'Digital Marketing',
  kind: 'business-product',
  registryProviderRef: { providerId: 'registry' },
  sourceProviderRef: { providerId: 'source' },
  surfaces: [
    {
      id: 'web',
      type: 'web',
      implementationStatus: 'implemented',
    },
  ],
};

function scopedHeaders(correlationId: string): Record<string, string> {
  return {
    'X-Correlation-Id': correlationId,
    'X-Ghatana-Tenant-Id': 'tenant-1',
    'X-Ghatana-Workspace-Id': 'workspace-1',
    'X-Ghatana-Project-Id': 'project-1',
  };
}

function createService(overrides: Partial<ServiceShape> = {}): ServiceShape {
  return {
    listProductUnits: vi.fn().mockResolvedValue([productUnit]),
    getProductUnit: vi.fn().mockResolvedValue(productUnit),
    createLifecyclePlan: vi.fn().mockResolvedValue(plan),
    runLifecyclePhase: vi.fn().mockResolvedValue(result),
    listLifecycleRuns: vi.fn().mockResolvedValue([]),
    getLifecycleRun: vi.fn().mockResolvedValue({
      runId: 'run-1',
      correlationId: 'corr-1',
      productUnitId: 'digital-marketing',
      phase: 'build',
      status: 'succeeded',
    }),
    getManifest: vi.fn().mockResolvedValue({ manifest: true }),
    listPendingApprovals: vi.fn().mockResolvedValue([]),
    requestApproval: vi.fn().mockResolvedValue({ approvalId: 'approval-1', status: 'pending' }),
    submitApprovalDecision: vi.fn().mockResolvedValue({ approvalId: 'approval-1', status: 'approved' }),
    normalizeError: vi.fn(),
    ...overrides,
  } as unknown as ServiceShape;
}

type ServiceShape = KernelLifecycleService & {
  readonly listProductUnits: ReturnType<typeof vi.fn>;
  readonly getProductUnit: ReturnType<typeof vi.fn>;
  readonly createLifecyclePlan: ReturnType<typeof vi.fn>;
  readonly runLifecyclePhase: ReturnType<typeof vi.fn>;
  readonly listLifecycleRuns: ReturnType<typeof vi.fn>;
  readonly getLifecycleRun: ReturnType<typeof vi.fn>;
  readonly getManifest: ReturnType<typeof vi.fn>;
  readonly listPendingApprovals: ReturnType<typeof vi.fn>;
  readonly requestApproval: ReturnType<typeof vi.fn>;
  readonly submitApprovalDecision: ReturnType<typeof vi.fn>;
  readonly normalizeError: ReturnType<typeof vi.fn>;
};

const plan: ProductLifecyclePlan = {
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
  requiredManifests: [],
  requiredPlugins: [],
  approvalRequirements: [],
  outputDirectory: '/tmp/kernel/run-1',
  estimatedDurationMs: 1,
};

const result: ProductLifecycleResult = {
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
  outputDirectory: '/tmp/kernel/run-1',
};
