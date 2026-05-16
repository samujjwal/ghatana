import { describe, expect, it, vi } from 'vitest';
import type { ProductUnit } from '@ghatana/kernel-product-contracts';
import {
  KernelLifecycleApiHandlers,
  type KernelLifecycleActor,
  type KernelLifecycleApiRequest,
  type KernelLifecycleAuthorizer,
} from '../KernelLifecycleApiHandlers.js';
import type { KernelLifecycleService } from '../../service/KernelLifecycleService.js';

describe('KernelLifecycleApiHandlers — authorization enforcement', () => {
  it('returns 401 when authorizer rejects authentication', async () => {
    const authorizer = createAuthorizer({ authenticate: vi.fn().mockResolvedValue(null) });
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      authorizer,
      requireScopeHeaders: false,
    });

    const response = await handlers.listProductUnits(request('corr-1'));

    expect(response.statusCode).toBe(401);
    expect(response.body).toMatchObject({
      reasonCode: 'authentication-required',
      correlationId: 'corr-1',
    });
  });

  it('returns 403 when actor is authenticated but not authorized for product unit read', async () => {
    const authorizer = createAuthorizer({
      authenticate: vi.fn().mockResolvedValue(createActor()),
      authorizeProductUnitRead: vi.fn().mockResolvedValue(false),
    });
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      authorizer,
      requireScopeHeaders: false,
    });

    const response = await handlers.listProductUnits(request('corr-2'));

    expect(response.statusCode).toBe(403);
    expect(response.body).toMatchObject({
      reasonCode: 'authorization-failed',
      correlationId: 'corr-2',
    });
  });

  it('allows the request when actor is authenticated and authorized', async () => {
    const authorizer = createAuthorizer({
      authenticate: vi.fn().mockResolvedValue(createActor()),
      authorizeProductUnitRead: vi.fn().mockResolvedValue(true),
    });
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      authorizer,
      requireScopeHeaders: false,
    });

    const response = await handlers.listProductUnits(request('corr-3'));

    expect(response.statusCode).toBe(200);
  });

  it('enforces authorizeLifecyclePlan for createLifecyclePlan', async () => {
    const authorizer = createAuthorizer({
      authenticate: vi.fn().mockResolvedValue(createActor()),
      authorizeLifecyclePlan: vi.fn().mockResolvedValue(false),
    });
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      authorizer,
      requireScopeHeaders: false,
    });

    const response = await handlers.createLifecyclePlan({
      params: { productUnitId: 'digital-marketing' },
      headers: { 'X-Correlation-Id': 'corr-4' },
      body: { phase: 'build' },
    });

    expect(response.statusCode).toBe(403);
    expect(authorizer.authorizeLifecyclePlan).toHaveBeenCalledWith(
      expect.objectContaining({ actorId: 'actor-1' }),
      expect.objectContaining({ productUnitId: 'digital-marketing' }),
    );
  });

  it('enforces authorizeLifecycleExecute for executeLifecyclePhase', async () => {
    const authorizer = createAuthorizer({
      authenticate: vi.fn().mockResolvedValue(createActor()),
      authorizeLifecycleExecute: vi.fn().mockResolvedValue(false),
    });
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      authorizer,
      requireScopeHeaders: false,
    });

    const response = await handlers.executeLifecyclePhase({
      params: { productUnitId: 'digital-marketing' },
      headers: { 'X-Correlation-Id': 'corr-5' },
      body: { phase: 'build', dryRun: true },
    });

    expect(response.statusCode).toBe(403);
    expect(authorizer.authorizeLifecycleExecute).toHaveBeenCalled();
  });

  it('enforces authorizeManifestRead for getArtifactManifest', async () => {
    const authorizer = createAuthorizer({
      authenticate: vi.fn().mockResolvedValue(createActor()),
      authorizeManifestRead: vi.fn().mockResolvedValue(false),
    });
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      authorizer,
      requireScopeHeaders: false,
    });

    const response = await handlers.getArtifactManifest({
      params: { productUnitId: 'digital-marketing', runId: 'run-1' },
      headers: { 'X-Correlation-Id': 'corr-6' },
    });

    expect(response.statusCode).toBe(403);
    expect(authorizer.authorizeManifestRead).toHaveBeenCalledWith(
      expect.objectContaining({ actorId: 'actor-1' }),
      expect.objectContaining({ productUnitId: 'digital-marketing', runId: 'run-1' }),
    );
  });

  it('enforces authorizeApprovalRequest for requestApproval', async () => {
    const authorizer = createAuthorizer({
      authenticate: vi.fn().mockResolvedValue(createActor()),
      authorizeApprovalRequest: vi.fn().mockResolvedValue(false),
    });
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      authorizer,
      requireScopeHeaders: false,
    });

    const response = await handlers.requestApproval({
      params: {},
      headers: { 'X-Correlation-Id': 'corr-7' },
      body: {
        approvalId: 'approval-1',
        productUnitId: 'digital-marketing',
        runId: 'run-1',
        requestedBy: 'actor-1',
        requestedAt: '2026-05-14T00:00:00.000Z',
        reason: 'deployment gate',
        requiredApprovers: ['actor-2'],
        expiresAt: '2026-06-14T00:00:00.000Z',
      },
    });

    expect(response.statusCode).toBe(403);
    expect(authorizer.authorizeApprovalRequest).toHaveBeenCalledWith(
      expect.objectContaining({ actorId: 'actor-1' }),
      expect.objectContaining({ productUnitId: 'digital-marketing' }),
    );
  });

  it('enforces authorizeApprovalDecision for submitApprovalDecision', async () => {
    const authorizer = createAuthorizer({
      authenticate: vi.fn().mockResolvedValue(createActor()),
      authorizeApprovalDecision: vi.fn().mockResolvedValue(false),
    });
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      authorizer,
      requireScopeHeaders: false,
    });

    const response = await handlers.submitApprovalDecision({
      params: { approvalId: 'approval-1' },
      headers: { 'X-Correlation-Id': 'corr-8' },
      body: {
        approvalId: 'approval-1',
        decision: 'approved',
        decidedBy: 'actor-1',
        decidedAt: '2026-05-14T00:00:00.000Z',
      },
    });

    expect(response.statusCode).toBe(403);
    expect(authorizer.authorizeApprovalDecision).toHaveBeenCalled();
  });

  it('returns 401 when no authorizer configured and requireAuthentication is true', async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireScopeHeaders: false,
      requireAuthentication: true,
    });

    const response = await handlers.listProductUnits(request('corr-9'));

    expect(response.statusCode).toBe(401);
    expect(response.body).toMatchObject({ reasonCode: 'authentication-required' });
  });

  it('allows the request when no authorizer and allowUnscopedLocalDevelopment is true', async () => {
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      requireScopeHeaders: false,
      requireAuthentication: true,
      allowUnscopedLocalDevelopment: true,
    });

    const response = await handlers.listProductUnits(request('corr-10'));

    expect(response.statusCode).toBe(200);
  });

  it('passes correlationId from context to auth context when not provided in authContext', async () => {
    const authorizeProductUnitRead = vi.fn().mockResolvedValue(true);
    const authorizer = createAuthorizer({
      authenticate: vi.fn().mockResolvedValue(createActor()),
      authorizeProductUnitRead,
    });
    const handlers = new KernelLifecycleApiHandlers({
      service: createService(),
      authorizer,
      requireScopeHeaders: false,
    });

    await handlers.listProductUnits(request('corr-ctx'));

    expect(authorizeProductUnitRead).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ correlationId: 'corr-ctx' }),
    );
  });
});

function request(correlationId: string): KernelLifecycleApiRequest {
  return { headers: { 'X-Correlation-Id': correlationId } };
}

function createActor(): KernelLifecycleActor {
  return {
    actorId: 'actor-1',
    roles: ['builder'],
    capabilities: ['lifecycle:read'],
  };
}

function createAuthorizer(overrides: Partial<KernelLifecycleAuthorizer> = {}): KernelLifecycleAuthorizer {
  return {
    authenticate: vi.fn().mockResolvedValue(createActor()),
    authorizeProductUnitRead: vi.fn().mockResolvedValue(true),
    authorizeLifecyclePlan: vi.fn().mockResolvedValue(true),
    authorizeLifecycleExecute: vi.fn().mockResolvedValue(true),
    authorizeManifestRead: vi.fn().mockResolvedValue(true),
    authorizeApprovalRequest: vi.fn().mockResolvedValue(true),
    authorizeApprovalDecision: vi.fn().mockResolvedValue(true),
    ...overrides,
  };
}

const productUnit: ProductUnit = {
  schemaVersion: '1.0.0',
  id: 'digital-marketing',
  name: 'Digital Marketing',
  kind: 'business-product',
  registryProviderRef: { providerId: 'registry' },
  sourceProviderRef: { providerId: 'source' },
  surfaces: [{ id: 'web', type: 'web', implementationStatus: 'implemented' }],
};

function createService(): KernelLifecycleService {
  return {
    listProductUnits: vi.fn().mockResolvedValue([productUnit]),
    getProductUnit: vi.fn().mockResolvedValue(productUnit),
    createLifecyclePlan: vi.fn().mockResolvedValue({
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
    }),
    runLifecyclePhase: vi.fn().mockResolvedValue({
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
    }),
    listLifecycleRuns: vi.fn().mockResolvedValue([]),
    getLifecycleRun: vi.fn().mockResolvedValue({ runId: 'run-1', correlationId: 'corr-1', productUnitId: 'digital-marketing', phase: 'build', status: 'succeeded' }),
    getManifest: vi.fn().mockResolvedValue({ manifest: true }),
    requestApproval: vi.fn().mockResolvedValue({ approvalId: 'approval-1', status: 'pending' }),
    submitApprovalDecision: vi.fn().mockResolvedValue({ approvalId: 'approval-1', status: 'approved' }),
    normalizeError: vi.fn(),
  } as unknown as KernelLifecycleService;
}
