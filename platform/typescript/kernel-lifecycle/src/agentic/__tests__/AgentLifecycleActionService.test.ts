import { describe, expect, it, vi } from 'vitest';
import { AgentLifecycleActionService } from '../AgentLifecycleActionService';
import type {
  AgentLifecycleActionRequest,
  KernelLifecycleProviderContext,
} from '@ghatana/kernel-product-contracts';
import type {
  ProductLifecyclePlan,
  ProductLifecycleResult,
} from '../../domain/ProductLifecyclePhase';

const request: AgentLifecycleActionRequest = {
  schemaVersion: '1.0.0',
  requestId: 'agent-request-1',
  correlationId: 'corr-agent-1',
  productUnitId: 'digital-marketing',
  scope: {
    tenantId: 'tenant-1',
    workspaceId: 'workspace-1',
    projectId: 'digital-marketing',
  },
  requestedByAgent: 'agent:release-reviewer',
  requestedAction: 'execute-lifecycle-phase',
  lifecyclePhase: 'deploy',
  proposedPlanRef: 'lifecycle-plan:run-1',
  riskLevel: 'medium',
  requiredApprovals: [],
  requiredVerification: [{ verificationId: 'verify-health', kind: 'health', required: true }],
  evidenceRefs: ['evidence:policy:1'],
  rollbackPlanRef: 'rollback-plan:run-1',
};

function plan(): ProductLifecyclePlan {
  return {
    productId: 'digital-marketing',
    phase: 'deploy',
    runId: 'run-1',
    correlationId: 'corr-agent-1',
    providerMode: 'bootstrap',
    phaseMode: 'sequential',
    steps: [],
    gates: [],
    expectedArtifacts: [],
    requiredManifests: [],
    requiredPlugins: [],
    approvalRequirements: [],
    semanticArtifactRefs: [],
  } as unknown as ProductLifecyclePlan;
}

function lifecycleResult(status: ProductLifecycleResult['status'] = 'succeeded'): ProductLifecycleResult {
  return {
    productId: 'digital-marketing',
    phase: 'deploy',
    runId: 'run-1',
    status,
    startedAt: '2026-05-14T00:00:00.000Z',
    completedAt: '2026-05-14T00:00:01.000Z',
    stepResults: [],
    gateResults: [],
    outputDirectory: '.kernel/out/run-1',
    manifestRefs: {
      lifecycleResult: '.kernel/out/run-1/lifecycle-result.json',
    },
  } as unknown as ProductLifecycleResult;
}

function createService(overrides: Partial<ConstructorParameters<typeof AgentLifecycleActionService>[0]> = {}) {
  const planner = {
    plan: vi.fn(async () => plan()),
  };
  const executor = {
    executePlan: vi.fn(async () => lifecycleResult()),
  };
  const service = new AgentLifecycleActionService({
    planner,
    executor,
    outputDirectory: '.kernel/out',
    now: () => '2026-05-14T00:00:00.000Z',
    ...overrides,
    checks: {
      policy: () => 'allowed',
      mastery: () => 'allowed',
      ...overrides.checks,
    },
  });
  return { service, planner, executor };
}

describe('AgentLifecycleActionService', () => {
  it('fails closed by default before planning when no policy or mastery checks are configured', async () => {
    const planner = { plan: vi.fn(async () => plan()) };
    const executor = { executePlan: vi.fn(async () => lifecycleResult()) };
    const service = new AgentLifecycleActionService({
      planner,
      executor,
      outputDirectory: '.kernel/out',
      now: () => '2026-05-14T00:00:00.000Z',
    });

    const result = await service.handle(request);

    expect(result.policyDecision).toBe('denied');
    expect(result.failure?.reasonCode).toBe('policy-denied');
    expect(planner.plan).not.toHaveBeenCalled();
    expect(executor.executePlan).not.toHaveBeenCalled();
  });

  it('allows low-risk bootstrap plan creation only when bootstrap dev defaults are explicit', async () => {
    const planner = { plan: vi.fn(async () => plan()) };
    const executor = { executePlan: vi.fn(async () => lifecycleResult()) };
    const service = new AgentLifecycleActionService({
      planner,
      executor,
      outputDirectory: '.kernel/out',
      allowBootstrapDevDefaults: true,
      now: () => '2026-05-14T00:00:00.000Z',
      checks: { mastery: () => 'allowed' },
    });

    const result = await service.handle({
      ...request,
      requestedAction: 'create-lifecycle-plan',
      riskLevel: 'low',
    });

    expect(result.lifecycleRunRef).toBe('lifecycle-plan:run-1');
    expect(executor.executePlan).not.toHaveBeenCalled();
  });

  it('returns denied result without planning when policy denies', async () => {
    const { service, planner, executor } = createService({
      checks: { policy: () => 'denied' },
    });

    const result = await service.handle(request);

    expect(result.policyDecision).toBe('denied');
    expect(result.masteryDecision).toBe('denied');
    expect(result.lifecycleRunRef).toBe(request.proposedPlanRef);
    expect(planner.plan).not.toHaveBeenCalled();
    expect(executor.executePlan).not.toHaveBeenCalled();
  });

  it('returns denied result without planning when mastery denies', async () => {
    const { service, planner, executor } = createService({
      checks: { mastery: () => 'denied' },
    });

    const result = await service.handle(request);

    expect(result.policyDecision).toBe('allowed');
    expect(result.masteryDecision).toBe('denied');
    expect(result.lifecycleRunRef).toBe(request.proposedPlanRef);
    expect(planner.plan).not.toHaveBeenCalled();
    expect(executor.executePlan).not.toHaveBeenCalled();
  });

  it('plans and returns pending approval without executing when approvals are required', async () => {
    const { service, planner, executor } = createService();

    const result = await service.handle({
      ...request,
      requiredApprovals: [{ approvalId: 'deploy-prod', approverRole: 'release-manager', required: true }],
    });

    expect(result.policyDecision).toBe('allowed');
    expect(result.approvalDecision).toBe('pending');
    expect(result.rollbackReadiness).toBe('ready');
    expect(planner.plan).toHaveBeenCalledWith('digital-marketing', 'deploy', {
      correlationId: 'corr-agent-1',
      providerMode: 'bootstrap',
    });
    expect(executor.executePlan).not.toHaveBeenCalled();
  });

  it('executes after approval and writes provenance through provider context', async () => {
    const recordProvenance = vi.fn(async () => ({ success: true, ref: 'provenance/agent.json' }));
    const recordRuntimeTruth = vi.fn(async () => ({ success: true, ref: 'runtime-truth/agent.json' }));
    const recordMemory = vi.fn(async () => ({ success: true, ref: 'memory/agent.json' }));
    const providerContext: KernelLifecycleProviderContext = {
      mode: 'platform',
      provenance: {
        providerId: 'datacloud-provenance',
        version: '1.0.0',
        capabilities: ['provenance'],
        backingStore: 'data-cloud',
        recordProvenance,
        listProvenance: vi.fn(async () => []),
      },
      runtimeTruth: {
        providerId: 'datacloud-runtime-truth',
        version: '1.0.0',
        capabilities: ['runtime-truth'],
        backingStore: 'data-cloud',
        recordRuntimeTruth,
        getRuntimeTruth: vi.fn(async () => null),
      },
      memory: {
        providerId: 'datacloud-memory',
        version: '1.0.0',
        capabilities: ['memory'],
        backingStore: 'data-cloud',
        recordMemory,
        listMemory: vi.fn(async () => []),
      },
    };
    const { service, executor } = createService({
      providerContext,
      checks: { approval: () => 'approved' },
    });

    const result = await service.handle(request);

    expect(result.approvalDecision).toBe('approved');
    expect(result.healthStatus).toBe('healthy');
    expect(result.evidenceRefs).toContain('.kernel/out/run-1/lifecycle-result.json');
    expect(executor.executePlan).toHaveBeenCalledWith(
      expect.objectContaining({ runId: 'run-1' }),
      expect.objectContaining({ providerContext, dryRun: false })
    );
    expect(recordProvenance).toHaveBeenCalledWith(
      expect.objectContaining({ provenanceId: 'agent-lifecycle:agent-request-1' }),
      { required: true, correlationId: 'corr-agent-1' }
    );
    expect(recordRuntimeTruth).toHaveBeenCalledWith(
      expect.objectContaining({
        productUnitId: 'digital-marketing',
        status: 'lifecycle-executed',
      }),
      { required: true, correlationId: 'corr-agent-1' }
    );
    expect(recordMemory).toHaveBeenCalledWith(
      expect.objectContaining({
        memoryId: 'agent-lifecycle:agent-request-1:run-1',
        productUnitId: 'digital-marketing',
      }),
      { required: true, correlationId: 'corr-agent-1' }
    );
  });

  it('returns approval rejection without executing', async () => {
    const { service, executor } = createService({
      checks: { approval: () => 'rejected' },
    });

    const result = await service.handle(request);

    expect(result.approvalDecision).toBe('rejected');
    expect(result.lifecycleRunRef).toBe('lifecycle-plan:run-1');
    expect(executor.executePlan).not.toHaveBeenCalled();
  });

  it('treats non-required approval entries as not required', async () => {
    const { service } = createService();

    const result = await service.handle({
      ...request,
      requiredApprovals: [{ approvalId: 'informational', approverRole: 'observer', required: false }],
    });

    expect(result.approvalDecision).toBe('not-required');
    expect(result.healthStatus).toBe('healthy');
  });

  it('reports unhealthy execution without manifest evidence when execution fails verification-successfully', async () => {
    const executor = {
      executePlan: vi.fn(async () => ({
        ...lifecycleResult('failed'),
        manifestRefs: undefined,
      }) as unknown as ProductLifecycleResult),
    };
    const service = new AgentLifecycleActionService({
      planner: { plan: vi.fn(async () => plan()) },
      executor,
      outputDirectory: '.kernel/out',
      checks: {
        policy: () => 'allowed',
        mastery: () => 'allowed',
        approval: () => 'not-required',
        verification: () => true,
      },
    });

    const result = await service.handle(request);

    expect(result.healthStatus).toBe('unhealthy');
    expect(result.evidenceRefs).toEqual(request.evidenceRefs);
    expect(result.evaluatedAt).toMatch(/T/);
  });

  it('returns degraded health and not-ready rollback when verification fails', async () => {
    const { service } = createService({
      checks: {
        approval: () => 'not-required',
        verification: () => false,
      },
    });

    const result = await service.handle(request);

    expect(result.healthStatus).toBe('degraded');
    expect(result.rollbackReadiness).toBe('not-ready');
  });

  it('fails contract validation for raw command requests', async () => {
    const { service, planner } = createService();

    await expect(service.handle({ ...request, proposedPlanRef: 'pnpm deploy' })).rejects.toThrow();
    expect(planner.plan).not.toHaveBeenCalled();
  });

  it('fails contract validation when evidence refs are missing for required verification', async () => {
    const { service, planner, executor } = createService({
      checks: {
        approval: () => 'not-required',
      },
    });

    await expect(service.handle({
      ...request,
      evidenceRefs: [],
      requiredVerification: [{ verificationId: 'verify-health', kind: 'health', required: true }],
    })).rejects.toThrow('Invalid AgentLifecycleActionRequest');

    expect(planner.plan).not.toHaveBeenCalled();
    expect(executor.executePlan).not.toHaveBeenCalled();
  });
});
