import { describe, expect, it, vi } from 'vitest';
import type {
  GateEvaluationRequest,
  GateProvider,
  KernelLifecycleProviderContext,
} from '@ghatana/kernel-product-contracts';
import type { ProductGatePlan } from '../../domain/ProductLifecyclePhase.js';
import { GateExecutor } from '../GateExecutor.js';

function gatePlan(overrides: Partial<ProductGatePlan> = {}): ProductGatePlan {
  return {
    gateId: 'security',
    gateName: 'Security',
    required: true,
    phase: 'build',
    source: 'kernel-product.yaml',
    status: 'pending',
    ...overrides,
  };
}

function gateProvider(overrides: Partial<GateProvider> = {}): GateProvider {
  return {
    providerId: 'policy-gates',
    version: '1.0.0',
    capabilities: ['gates'],
    evaluateGate: vi.fn().mockResolvedValue({
      gateId: 'security',
      passed: true,
      reason: 'policy passed',
      evidence: ['policy:security'],
      evaluatedAt: '2026-05-14T00:00:00.000Z',
      duration: 12,
    }),
    getGateConfig: vi.fn().mockResolvedValue(null),
    listGates: vi.fn().mockResolvedValue(['security']),
    ...overrides,
  };
}

function context(provider: GateProvider): KernelLifecycleProviderContext {
  return {
    mode: 'bootstrap',
    gates: {
      security: provider,
      'policy-gates': provider,
    },
  };
}

describe('GateExecutor', () => {
  it('returns typed passed gate results with evidence and context', async () => {
    const provider = gateProvider();
    const executor = new GateExecutor({ providerContext: context(provider) });

    const result = await executor.execute({
      productId: 'digital-marketing',
      runId: 'run-1',
      correlationId: 'corr-1',
      phase: 'build',
      gates: [gatePlan({ providerId: 'policy-gates' })],
      artifacts: [
        {
          id: 'web-dist',
          surface: 'web',
          type: 'static-web-bundle',
          path: 'dist',
          fingerprint: 'sha256:abc',
          producedBy: 'vite',
        },
      ],
      environment: 'staging',
      productUnit: { id: 'digital-marketing' },
      deployment: { id: 'deployment-1' },
      providerMode: 'bootstrap',
      scope: {
        tenantId: 'tenant-1',
        workspaceId: 'workspace-1',
        projectId: 'project-1',
      },
      policyPacks: ['kernel://policy-packs/web-api-security-baseline'],
      privacyClassification: 'internal',
      evidenceRefs: ['artifact:source'],
    });

    expect(result.failedRequiredGate).toBeUndefined();
    expect(result.summary).toEqual({
      passedCount: 1,
      failedRequiredCount: 0,
      skippedOptionalCount: 0,
    });
    expect(result.gates).toEqual([
      {
        gateId: 'security',
        gateName: 'Security',
        status: 'passed',
        checkedAt: '2026-05-14T00:00:00.000Z',
        details: 'policy passed',
        evidenceRefs: ['policy:security'],
        durationMs: 12,
        providerId: 'policy-gates',
        privacyClassification: 'internal',
      },
    ]);
    expect(vi.mocked(provider.evaluateGate).mock.calls[0]?.[0]).toMatchObject({
      gateId: 'security',
      productUnitId: 'digital-marketing',
      phase: 'build',
      context: {
        runId: 'run-1',
        correlationId: 'corr-1',
        environment: 'staging',
        providerMode: 'bootstrap',
        policyPacks: ['kernel://policy-packs/web-api-security-baseline'],
        privacyClassification: 'internal',
        evidenceRefs: ['artifact:source'],
      },
    } satisfies Partial<GateEvaluationRequest>);
  });

  it('fails closed when a required gate provider is missing', async () => {
    const executor = new GateExecutor({ providerContext: { mode: 'platform', gates: {} } });

    const result = await executor.execute({
      productId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build',
      gates: [gatePlan()],
      artifacts: [],
    });

    expect(result.failedRequiredGate).toMatchObject({
      gateId: 'security',
      status: 'failed',
      details: 'Required gate provider missing: security',
      evidenceRefs: [],
      reasonCode: 'required-gate-provider-missing',
    });
    expect(result.summary.failedRequiredCount).toBe(1);
  });

  it('skips optional gates when providers are missing', async () => {
    const executor = new GateExecutor();

    const result = await executor.execute({
      productId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build',
      gates: [gatePlan({ required: false })],
      artifacts: [],
    });

    expect(result.failedRequiredGate).toBeUndefined();
    expect(result.gates[0]).toMatchObject({
      status: 'skipped',
      details: 'Optional gate provider missing: security',
      reasonCode: 'optional-gate-provider-missing',
    });
    expect(result.summary.skippedOptionalCount).toBe(1);
  });

  it('fails required gates when providers reject and skips optional provider errors', async () => {
    const provider = gateProvider({
      providerId: 'policy-gates',
      evaluateGate: vi.fn().mockRejectedValue(new Error('policy engine offline')),
    });
    const executor = new GateExecutor({ providerContext: context(provider) });

    const required = await executor.execute({
      productId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build',
      gates: [gatePlan()],
      artifacts: [],
    });
    const optional = await executor.execute({
      productId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build',
      gates: [gatePlan({ required: false })],
      artifacts: [],
    });

    expect(required.failedRequiredGate).toMatchObject({
      status: 'failed',
      details: 'Gate provider policy-gates failed: policy engine offline',
      providerId: 'policy-gates',
      reasonCode: 'gate-provider-failed',
    });
    expect(optional.gates[0]).toMatchObject({
      status: 'skipped',
      details: 'Gate provider policy-gates failed: policy engine offline',
      reasonCode: 'gate-provider-failed',
    });
  });

  it('surfaces non-error provider rejections', async () => {
    const provider = gateProvider({
      evaluateGate: vi.fn().mockRejectedValue('policy-timeout'),
    });
    const executor = new GateExecutor({ providerContext: context(provider) });

    const result = await executor.execute({
      productId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build',
      gates: [gatePlan()],
      artifacts: [],
    });

    expect(result.failedRequiredGate?.details).toBe(
      'Gate provider policy-gates failed: policy-timeout',
    );
  });

  it('stops after the first failed required gate', async () => {
    const provider = gateProvider({
      evaluateGate: vi.fn().mockResolvedValue({
        gateId: 'security',
        passed: false,
        reason: 'policy denied',
        evidence: ['policy:deny'],
        evaluatedAt: '2026-05-14T00:00:00.000Z',
        duration: 5,
      }),
    });
    const executor = new GateExecutor({ providerContext: context(provider) });

    const result = await executor.execute({
      productId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build',
      gates: [gatePlan(), gatePlan({ gateId: 'privacy', gateName: 'Privacy' })],
      artifacts: [],
    });

    expect(result.gates).toHaveLength(1);
    expect(result.failedRequiredGate).toMatchObject({
      gateId: 'security',
      status: 'failed',
      evidenceRefs: ['policy:deny'],
      reasonCode: 'gate-evaluation-failed',
    });
  });

  it('fails a passed required gate without evidence refs', async () => {
    const provider = gateProvider({
      evaluateGate: vi.fn().mockResolvedValue({
        gateId: 'security',
        passed: true,
        reason: 'policy passed',
        evidence: [],
        evaluatedAt: '2026-05-14T00:00:00.000Z',
        duration: 5,
      }),
    });
    const executor = new GateExecutor({ providerContext: context(provider) });

    const result = await executor.execute({
      productId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build',
      gates: [gatePlan()],
      artifacts: [],
    });

    expect(result.failedRequiredGate).toMatchObject({
      gateId: 'security',
      status: 'failed',
      details: 'Required gate passed without evidence refs',
      reasonCode: 'required-gate-missing-evidence',
      evidenceRefs: [],
    });
    expect(result.summary).toEqual({
      passedCount: 0,
      failedRequiredCount: 1,
      skippedOptionalCount: 0,
    });
  });

  it('summarizes passed, failed required, and skipped optional gates', async () => {
    const provider = gateProvider();
    const executor = new GateExecutor({ providerContext: context(provider) });

    const result = await executor.execute({
      productId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build',
      gates: [
        gatePlan({ gateId: 'security' }),
        gatePlan({ gateId: 'missing-optional', gateName: 'Missing optional', required: false }),
      ],
      artifacts: [],
    });

    expect(result.summary).toEqual({
      passedCount: 1,
      failedRequiredCount: 0,
      skippedOptionalCount: 1,
    });
  });
});
