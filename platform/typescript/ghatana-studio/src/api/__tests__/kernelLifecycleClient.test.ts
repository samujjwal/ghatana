import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ValidationError } from '@ghatana/api';
import {
  createKernelLifecycleClient,
  type LifecyclePlan,
  type LifecycleRun,
} from '../kernelLifecycleClient';

const productUnit = {
  schemaVersion: '1.0.0',
  id: 'digital-marketing',
  name: 'Digital Marketing',
  kind: 'business-product',
  registryProviderRef: { providerId: 'registry' },
  sourceProviderRef: { providerId: 'source' },
  surfaces: [
    {
      id: 'digital-marketing-web',
      type: 'web',
      implementationStatus: 'implemented',
    },
  ],
};

const lifecyclePlan: LifecyclePlan = {
  runId: 'run-1',
  correlationId: 'corr-1',
  productUnitId: 'digital-marketing',
  phase: 'build',
  status: 'running',
};

const lifecycleRun: LifecycleRun = {
  runId: 'run-1',
  correlationId: 'corr-1',
  productUnitId: 'digital-marketing',
  phase: 'build',
  status: 'healthy',
  failureReasonCode: 'adapter-failed',
  manifestRefs: { artifact: 'artifact-manifest.json' },
  approvalRefs: ['approval-1'],
  eventsRef: 'lifecycle-events.json',
  healthSnapshotRef: 'health.json',
};

const artifactManifest = {
  schemaVersion: '1.0.0',
  productId: 'digital-marketing',
  phase: 'build',
  surface: 'web',
  timestamp: '2026-05-14T00:00:00.000Z',
  artifacts: [
    {
      id: 'web-dist',
      path: 'dist',
      metadata: {
        type: 'static-web-bundle',
        packaging: 'static-files',
        version: '1.0.0',
        buildNumber: '1',
        gitCommit: 'abcdef0',
        gitBranch: 'main',
        timestamp: '2026-05-14T00:00:00.000Z',
        sizeBytes: 12,
      },
      fingerprint: { algorithm: 'sha256', hash: 'a'.repeat(64) },
      expected: true,
      found: true,
    },
  ],
};

const deploymentManifest = {
  schemaVersion: '1.0.0',
  productId: 'digital-marketing',
  version: '1.0.0',
  environment: 'local',
  deploymentId: 'deploy-1',
  surfaces: [
    {
      surface: 'web',
      status: 'deployed',
      artifactId: 'web-dist',
      deploymentTarget: 'compose-local',
      deployedAt: '2026-05-14T00:00:00.000Z',
      healthCheckPassed: true,
    },
  ],
  deployedAt: '2026-05-14T00:00:00.000Z',
  rollbackPlan: {
    strategy: 'previous-artifact',
    targetVersion: '0.9.0',
    reason: 'Rollback',
    steps: ['restore previous artifact'],
  },
};

const approvalGate = {
  approvalId: 'approval-1',
  productId: 'digital-marketing',
  runId: 'run-1',
  correlationId: 'approval-1',
  environment: 'bootstrap',
  gateName: 'approval-1',
  action: 'deploy',
  riskLevel: 'medium',
  requestedBy: 'sam',
  requestedAt: '2026-05-14T00:00:00.000Z',
  evidenceRefs: ['lifecycle-result:run-1'],
  required: true,
  approvers: ['alice'],
  approvals: [],
  status: 'pending',
  requiredApprovals: 1,
};

interface FetchCall {
  readonly url: string;
  readonly init: RequestInit;
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function getFetchCalls(mockFetch: ReturnType<typeof vi.fn>): readonly FetchCall[] {
  return mockFetch.mock.calls.map((call: unknown[]) => {
    const url = call[0];
    const init = call[1];
    if (!(url instanceof URL) || !isRequestInit(init)) {
      throw new Error('Unexpected fetch call shape');
    }
    return {
      url: url.toString(),
      init,
    };
  });
}

function isRequestInit(value: unknown): value is RequestInit {
  return typeof value === 'object' && value !== null;
}

describe('kernelLifecycleClient', () => {
  let mockFetch: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockFetch = vi.fn();
    vi.stubGlobal('fetch', mockFetch);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('lists and reads ProductUnits with contract validation', async () => {
    mockFetch
      .mockResolvedValueOnce(jsonResponse([productUnit]))
      .mockResolvedValueOnce(jsonResponse(productUnit));
    const client = createKernelLifecycleClient({
      baseUrl: 'https://studio.test',
      correlationIdFactory: () => 'corr-default',
    });

    await expect(client.listProductUnits()).resolves.toEqual([productUnit]);
    await expect(client.getProductUnit('digital-marketing')).resolves.toEqual(productUnit);

    const calls = getFetchCalls(mockFetch);
    expect(calls[0].url).toBe('https://studio.test/api/kernel/product-units');
    expect(calls[1].url).toBe(
      'https://studio.test/api/kernel/product-units/digital-marketing',
    );
    expect(new Headers(calls[0].init.headers).get('X-Correlation-Id')).toBe('corr-default');
  });

  it('creates lifecycle plans with scoped headers and explicit correlation IDs', async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse(lifecyclePlan));
    const client = createKernelLifecycleClient({
      baseUrl: 'https://studio.test',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      correlationIdFactory: () => 'corr-default',
    });

    await expect(
      client.createLifecyclePlan('digital marketing', 'build', {
        correlationId: 'corr-plan',
        environment: 'local',
        dryRun: true,
      }),
    ).resolves.toEqual(lifecyclePlan);

    const [call] = getFetchCalls(mockFetch);
    const headers = new Headers(call.init.headers);
    expect(call.url).toBe(
      'https://studio.test/api/kernel/product-units/digital%20marketing/lifecycle/plans',
    );
    expect(headers.get('X-Correlation-Id')).toBe('corr-plan');
    expect(headers.get('X-Ghatana-Tenant-Id')).toBe('tenant-1');
    expect(headers.get('X-Ghatana-Workspace-Id')).toBe('workspace-1');
    expect(headers.get('X-Ghatana-Project-Id')).toBe('project-1');
    expect(JSON.parse(String(call.init.body))).toEqual({
      phase: 'build',
      correlationId: 'corr-plan',
      environment: 'local',
      dryRun: true,
    });
  });

  it('creates lifecycle plans with generated correlation id and no optional body fields', async () => {
    mockFetch.mockResolvedValueOnce(
      jsonResponse({ ...lifecyclePlan, correlationId: 'corr-generated' }),
    );
    const client = createKernelLifecycleClient({
      baseUrl: 'https://studio.test',
      correlationIdFactory: () => 'corr-generated',
    });

    await expect(client.createLifecyclePlan('digital-marketing', 'build')).resolves.toMatchObject({
      correlationId: 'corr-generated',
    });

    const [call] = getFetchCalls(mockFetch);
    expect(JSON.parse(String(call.init.body))).toEqual({
      phase: 'build',
      correlationId: 'corr-generated',
    });
  });

  it('executes lifecycle phases without retrying mutation semantics in the client layer', async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse(lifecycleRun));
    const client = createKernelLifecycleClient({
      baseUrl: 'https://studio.test',
      correlationIdFactory: () => 'corr-execute',
    });

    await expect(
      client.executeLifecyclePhase('digital-marketing', 'build', {
        dryRun: true,
        environment: 'local',
      }),
    ).resolves.toEqual(lifecycleRun);

    const [call] = getFetchCalls(mockFetch);
    expect(call.url).toBe(
      'https://studio.test/api/kernel/product-units/digital-marketing/lifecycle/execute',
    );
    expect(JSON.parse(String(call.init.body))).toEqual({
      phase: 'build',
      correlationId: 'corr-execute',
      dryRun: true,
      environment: 'local',
    });
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  it('loads lifecycle run, run list, and manifests with output validation', async () => {
    mockFetch
      .mockResolvedValueOnce(jsonResponse(lifecycleRun))
      .mockResolvedValueOnce(jsonResponse([lifecycleRun]))
      .mockResolvedValueOnce(
        jsonResponse({
          schemaVersion: '1.0.0',
          productUnitId: 'digital-marketing',
          runId: 'run-1',
          gates: [{ gateId: 'security', status: 'passed', required: true }],
        }),
      )
      .mockResolvedValueOnce(jsonResponse(artifactManifest))
      .mockResolvedValueOnce(jsonResponse(deploymentManifest))
      .mockResolvedValueOnce(
        jsonResponse({
          schemaVersion: '1.0.0',
          productUnitId: 'digital-marketing',
          runId: 'run-1',
          status: 'healthy',
          checkedAt: '2026-05-14T00:00:00.000Z',
        }),
      );
    const client = createKernelLifecycleClient({ baseUrl: 'https://studio.test' });

    await expect(client.getLifecycleRun('digital-marketing', 'run-1')).resolves.toEqual(
      lifecycleRun,
    );
    await expect(client.listLifecycleRuns('digital-marketing')).resolves.toEqual([
      lifecycleRun,
    ]);
    await expect(
      client.getGateResultManifest('digital-marketing', 'run-1'),
    ).resolves.toMatchObject({ gates: [{ gateId: 'security' }] });
    await expect(client.getArtifactManifest('digital-marketing', 'run-1')).resolves.toEqual(
      artifactManifest,
    );
    await expect(
      client.getDeploymentManifest('digital-marketing', 'run-1', { environment: 'local' }),
    ).resolves.toEqual(deploymentManifest);
    await expect(
      client.getVerifyHealthReport('digital-marketing', 'run-1'),
    ).resolves.toMatchObject({ status: 'healthy' });

    expect(getFetchCalls(mockFetch)[4].url).toContain('environment=local');
  });

  it('loads deployment manifests without optional query parameters', async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse(deploymentManifest));
    const client = createKernelLifecycleClient({ baseUrl: 'https://studio.test' });

    await expect(
      client.getDeploymentManifest('digital-marketing', 'run-1'),
    ).resolves.toEqual(deploymentManifest);

    expect(getFetchCalls(mockFetch)[0].url).toBe(
      'https://studio.test/api/kernel/product-units/digital-marketing/lifecycle/runs/run-1/deployment-manifest',
    );
  });

  it('requests approvals and submits approval decisions', async () => {
    mockFetch
      .mockResolvedValueOnce(jsonResponse(approvalGate))
      .mockResolvedValueOnce(
        jsonResponse({
          ...approvalGate,
          approvals: [
            {
              approvalId: 'approval-1',
              approver: 'alice',
              approved: true,
              timestamp: '2026-05-14T00:00:00.000Z',
              decidedAt: '2026-05-14T00:00:00.000Z',
              comment: 'Approved',
              evidenceRefs: [],
            },
          ],
          status: 'approved',
          decidedAt: '2026-05-14T00:00:00.000Z',
        }),
      );
    const client = createKernelLifecycleClient({ baseUrl: 'https://studio.test' });

    await expect(
      client.requestApproval({
        approvalId: 'approval-1',
        productUnitId: 'digital-marketing',
        requestedBy: 'release-manager',
        reason: 'Deploy',
        requiredApprovers: ['alice'],
        expiresAt: '2026-05-14T00:00:00.000Z',
      }),
    ).resolves.toEqual(approvalGate);
    await expect(
      client.submitApprovalDecision('approval-1', {
        approvalId: 'approval-1',
        approved: true,
        approvedBy: 'alice',
        reason: 'Approved',
        decidedAt: '2026-05-14T00:00:00.000Z',
      }),
    ).resolves.toMatchObject({ status: 'approved' });

    const calls = getFetchCalls(mockFetch);
    expect(calls[0].url).toBe('https://studio.test/api/kernel/approvals');
    expect(calls[1].url).toBe(
      'https://studio.test/api/kernel/approvals/approval-1/decisions',
    );
  });

  it('rejects invalid inputs before making network calls', async () => {
    const client = createKernelLifecycleClient();

    await expect(client.getProductUnit(' ')).rejects.toThrow(
      'productUnitId must be a non-empty string',
    );
    await expect(client.createLifecyclePlan('digital-marketing', 'bad' as 'build')).rejects.toThrow(
      'Unsupported lifecycle phase: bad',
    );
    await expect(
      client.submitApprovalDecision('approval-1', {
        approvalId: 'approval-2',
        approved: true,
        approvedBy: 'alice',
        reason: 'Approved',
        decidedAt: '2026-05-14T00:00:00.000Z',
      }),
    ).rejects.toThrow('approvalId must match decision.approvalId');
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('surfaces output validation failures from shared schemas', async () => {
    mockFetch.mockResolvedValueOnce(jsonResponse({ id: 'missing-contract-fields' }));
    const client = createKernelLifecycleClient({ baseUrl: 'https://studio.test' });

    await expect(client.getProductUnit('digital-marketing')).rejects.toBeInstanceOf(
      ValidationError,
    );
  });
});
