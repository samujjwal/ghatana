import { describe, expect, it } from 'vitest';
import { validateKernelLifecycleProviderContext } from '@ghatana/kernel-product-contracts';
import type { KernelLifecycleEvent } from '@ghatana/kernel-product-contracts';
import {
  DataCloudApprovalProvider,
  DataCloudArtifactProvider,
  DataCloudHealthProvider,
  DataCloudKernelProviderClient,
  DataCloudMemoryProvider,
  DataCloudProvenanceProvider,
  DataCloudRuntimeTruthProvider,
  createDataCloudPolicyEvidenceProvider,
  createDataCloudTelemetryProvider,
  createDataCloudKernelProviderContext,
} from '../index.js';

describe('Data Cloud Kernel provider bridge', () => {
  it('creates platform provider context with all required providers', () => {
    const context = createDataCloudKernelProviderContext({
      baseUrl: 'https://data-cloud.local',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      fetchImpl: async () => jsonResponse({ success: true, ref: 'ok' }),
    });

    expect(validateKernelLifecycleProviderContext(context)).toEqual({
      valid: true,
      missingProviders: [],
      mode: 'platform',
      reasonCodes: [],
      invalidBackingStores: [],
    });
  });

  it('appends and lists events through Data Cloud APIs with scoped headers', async () => {
    const calls: CapturedRequest[] = [];
    const context = createDataCloudKernelProviderContext({
      baseUrl: 'https://data-cloud.local',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      fetchImpl: async (input, init) => {
        calls.push(capture(input, init));
        return init?.method === 'GET'
          ? jsonResponse({ items: [event()] })
          : jsonResponse({ success: true, ref: 'events/ref' });
      },
    });

    await expect(context.events?.appendEvent(event(), {
      required: true,
      correlationId: 'corr-1',
    })).resolves.toEqual({ success: true, ref: 'events/ref' });
    await expect(context.events?.listEvents({ productUnitId: 'digital-marketing', correlationId: 'corr-1' })).resolves.toEqual([
      { metadata: { eventId: 'event-1' } },
    ]);

    expect(calls[0]?.url).toBe('https://data-cloud.local/api/v1/kernel/providers/events');
    expect(calls[0]?.headers['x-ghatana-tenant-id']).toBe('tenant-1');
    expect(calls[0]?.headers['x-correlation-id']).toBe('corr-1');
    expect(calls[1]?.url).toContain('productUnitId=digital-marketing');
  });

  it('records artifact, runtime truth, and memory writes', async () => {
    const calls: CapturedRequest[] = [];
    const client = new DataCloudKernelProviderClient({
      baseUrl: 'https://data-cloud.local',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      fetchImpl: async (input, init) => {
        calls.push(capture(input, init));
        return jsonResponse({ success: true, ref: 'write/ref' });
      },
    });

    const artifacts = new DataCloudArtifactProvider(client);
    const runtimeTruth = new DataCloudRuntimeTruthProvider(client);
    const memory = new DataCloudMemoryProvider(client);

    await artifacts.recordArtifactManifest({
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      manifestPath: '.kernel/out/artifact-manifest.json',
      artifactCount: 1,
      digestStatus: 'complete',
    }, { required: true, correlationId: 'corr-1' });
    await runtimeTruth.recordRuntimeTruth({
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build',
      status: 'healthy',
      observedAt: '2026-05-14T00:00:00.000Z',
      evidenceRefs: ['artifact:manifest'],
      providerMode: 'platform',
    }, { required: true, correlationId: 'corr-1' });
    await memory.recordMemory({
      memoryId: 'memory-1',
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      kind: 'lifecycle-run-summary',
      contentRef: 'memory://run-1',
      recordedAt: '2026-05-14T00:00:00.000Z',
    }, { required: true, correlationId: 'corr-1' });

    expect(calls.map((call) => call.url)).toEqual([
      'https://data-cloud.local/api/v1/kernel/providers/artifacts',
      'https://data-cloud.local/api/v1/kernel/providers/runtime-truth',
      'https://data-cloud.local/api/v1/kernel/providers/memory',
    ]);
  });

  it('records health and approval endpoints through Data Cloud APIs', async () => {
    const calls: CapturedRequest[] = [];
    const client = new DataCloudKernelProviderClient({
      baseUrl: 'https://data-cloud.local',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      fetchImpl: async (input, init) => {
        calls.push(capture(input, init));
        return jsonResponse({ success: true, ref: 'write/ref' });
      },
    });

    const health = new DataCloudHealthProvider(client);
    const approvals = new DataCloudApprovalProvider(client);

    await health.recordHealthSnapshot({
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      status: 'healthy',
      snapshotPath: '.kernel/out/health.json',
    }, { required: true, correlationId: 'corr-1' });

    await approvals.requestLifecycleApproval({
      approvalId: 'approval-request-1',
      productUnitId: 'digital-marketing',
      reason: 'needs approval',
      requestedBy: 'owner-1',
      requestedAt: '2026-05-14T00:00:00.000Z',
      requiredApprovers: ['reviewer-1'],
      expiresAt: '2026-05-15T00:00:00.000Z',
    }, { required: true, correlationId: 'corr-2' });

    await approvals.decideLifecycleApproval({
      approvalId: 'approval-request-1',
      approved: true,
      approvedBy: 'reviewer-1',
      reason: 'ship it',
      decidedAt: '2026-05-14T00:01:00.000Z',
    }, { required: true, correlationId: 'corr-3' });

    expect(calls.map((call) => call.url)).toEqual([
      'https://data-cloud.local/api/v1/kernel/providers/health',
      'https://data-cloud.local/api/v1/kernel/providers/approvals/requests',
      'https://data-cloud.local/api/v1/kernel/providers/approvals/decisions',
    ]);
  });

  it('records and lists provenance and memory with scoped queries', async () => {
    const calls: CapturedRequest[] = [];
    const client = new DataCloudKernelProviderClient({
      baseUrl: 'https://data-cloud.local',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      fetchImpl: async (input, init) => {
        calls.push(capture(input, init));
        const url = String(input);
        if (init?.method === 'GET' && url.includes('/provenance')) {
          return jsonResponse({
            items: [{
              provenanceId: 'prov-1',
              evidenceRefs: ['artifact:manifest'],
            }],
          });
        }
        if (init?.method === 'GET' && url.includes('/memory')) {
          return jsonResponse({
            items: [{
              memoryId: 'memory-1',
              contentRef: 'memory://run-1',
            }],
          });
        }
        return jsonResponse({ success: true, ref: 'write/ref' });
      },
    });

    const provenance = new DataCloudProvenanceProvider(client);
    const memory = new DataCloudMemoryProvider(client);

    await provenance.recordProvenance({
      provenanceId: 'prov-1',
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      source: 'kernel-lifecycle',
      evidenceRefs: ['artifact:manifest'],
      recordedAt: '2026-05-14T00:00:00.000Z',
    }, { required: true, correlationId: 'corr-1' });

    await memory.recordMemory({
      memoryId: 'memory-1',
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      kind: 'lifecycle-run-summary',
      contentRef: 'memory://run-1',
      recordedAt: '2026-05-14T00:00:00.000Z',
    }, { required: true, correlationId: 'corr-2' });

    await expect(provenance.listProvenance({ productUnitId: 'digital-marketing' })).resolves.toEqual([
      {
        provenanceId: 'prov-1',
        evidenceRefs: ['artifact:manifest'],
      },
    ]);
    await expect(memory.listMemory({ productUnitId: 'digital-marketing' })).resolves.toEqual([
      {
        memoryId: 'memory-1',
        contentRef: 'memory://run-1',
      },
    ]);

    expect(calls.map((call) => call.url)).toEqual([
      'https://data-cloud.local/api/v1/kernel/providers/provenance',
      'https://data-cloud.local/api/v1/kernel/providers/memory',
      'https://data-cloud.local/api/v1/kernel/providers/provenance?productUnitId=digital-marketing',
      'https://data-cloud.local/api/v1/kernel/providers/memory?productUnitId=digital-marketing',
    ]);
  });

  it('records and lists policy evidence through dedicated endpoints', async () => {
    const calls: CapturedRequest[] = [];
    const policy = createDataCloudPolicyEvidenceProvider({
      baseUrl: 'https://data-cloud.local',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      fetchImpl: async (input, init) => {
        calls.push(capture(input, init));
        return init?.method === 'GET'
          ? jsonResponse({ items: [event()] })
          : jsonResponse({ success: true, ref: 'policy/ref' });
      },
    });

    await expect(policy.appendEvent(event(), { required: true, correlationId: 'corr-1' })).resolves.toEqual({
      success: true,
      ref: 'policy/ref',
    });
    await expect(policy.listEvents({ productUnitId: 'digital-marketing' })).resolves.toEqual([
      { metadata: { eventId: 'event-1' } },
    ]);

    expect(calls.map((call) => call.url)).toEqual([
      'https://data-cloud.local/api/v1/kernel/providers/policy-evidence',
      'https://data-cloud.local/api/v1/kernel/providers/policy-evidence?productUnitId=digital-marketing',
    ]);
  });

  it('records and retrieves telemetry metrics and events', async () => {
    const calls: CapturedRequest[] = [];
    const telemetry = createDataCloudTelemetryProvider({
      baseUrl: 'https://data-cloud.local',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      fetchImpl: async (input, init) => {
        calls.push(capture(input, init));
        const url = String(input);
        if (init?.method === 'GET' && url.includes('/telemetry/metrics/')) {
          return jsonResponse([
            {
              name: 'lifecycle.duration.ms',
              value: 42,
              timestamp: '2026-05-14T00:00:00.000Z',
              labels: { phase: 'build' },
            },
          ]);
        }
        if (init?.method === 'GET' && url.includes('/telemetry/events/')) {
          return jsonResponse([
            {
              eventId: 'evt-1',
              eventType: 'lifecycle.completed',
              timestamp: '2026-05-14T00:00:00.000Z',
              productUnitId: 'digital-marketing',
              payload: { status: 'healthy' },
            },
          ]);
        }
        return jsonResponse({ success: true, ref: 'telemetry/ref' });
      },
    });

    await telemetry.recordMetric({
      name: 'lifecycle.duration.ms',
      value: 42,
      timestamp: '2026-05-14T00:00:00.000Z',
      labels: { phase: 'build' },
    });
    await telemetry.emitEvent({
      eventId: 'evt-1',
      eventType: 'lifecycle.completed',
      timestamp: '2026-05-14T00:00:00.000Z',
      productUnitId: 'digital-marketing',
      payload: { status: 'healthy' },
    });

    await expect(telemetry.getMetrics('digital-marketing')).resolves.toEqual([
      {
        name: 'lifecycle.duration.ms',
        value: 42,
        timestamp: '2026-05-14T00:00:00.000Z',
        labels: { phase: 'build' },
      },
    ]);
    await expect(telemetry.getEvents('digital-marketing')).resolves.toEqual([
      {
        eventId: 'evt-1',
        eventType: 'lifecycle.completed',
        timestamp: '2026-05-14T00:00:00.000Z',
        productUnitId: 'digital-marketing',
        payload: { status: 'healthy' },
      },
    ]);

    expect(calls.map((call) => call.url)).toEqual([
      'https://data-cloud.local/api/v1/kernel/providers/telemetry/metrics',
      'https://data-cloud.local/api/v1/kernel/providers/telemetry/events',
      'https://data-cloud.local/api/v1/kernel/providers/telemetry/metrics/digital-marketing',
      'https://data-cloud.local/api/v1/kernel/providers/telemetry/events/digital-marketing',
    ]);
  });

  it('propagates required write failures and wraps optional failures', async () => {
    const client = new DataCloudKernelProviderClient({
      baseUrl: 'https://data-cloud.local',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      fetchImpl: async () => jsonResponse({ error: 'provider unavailable' }, 503),
    });
    const provider = new DataCloudRuntimeTruthProvider(client);
    const snapshot = {
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build' as const,
      status: 'failed',
      observedAt: '2026-05-14T00:00:00.000Z',
      evidenceRefs: ['runtime://truth'],
    };

    await expect(provider.recordRuntimeTruth(snapshot, {
      required: true,
      correlationId: 'corr-1',
    })).resolves.toEqual({ success: false, error: 'provider unavailable' });
    await expect(provider.recordRuntimeTruth(snapshot, {
      required: false,
      correlationId: 'corr-1',
    })).resolves.toEqual({
      success: false,
      error: 'optional Data Cloud provider write skipped: provider unavailable',
    });
  });

  it('queries runtime truth with tenant-scoped isolation', async () => {
    const calls: CapturedRequest[] = [];
    const client = new DataCloudKernelProviderClient({
      baseUrl: 'https://data-cloud.local',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      fetchImpl: async (input, init) => {
        calls.push(capture(input, init));
        return jsonResponse({
          productUnitId: 'digital-marketing',
          observedAt: '2026-05-14T00:00:00.000Z',
        });
      },
    });
    const provider = new DataCloudRuntimeTruthProvider(client);

    const result = await provider.getRuntimeTruth('digital-marketing');

    expect(result).toEqual({
      productUnitId: 'digital-marketing',
      observedAt: '2026-05-14T00:00:00.000Z',
    });
    expect(calls[0]?.url).toBe('https://data-cloud.local/api/v1/kernel/providers/runtime-truth/digital-marketing/latest');
    expect(calls[0]?.headers['x-ghatana-tenant-id']).toBe('tenant-1');
  });

  it('surfaces runtime truth lookup errors from provider', async () => {
    const client = new DataCloudKernelProviderClient({
      baseUrl: 'https://data-cloud.local',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      fetchImpl: async () => jsonResponse({ error: 'not found' }, 404),
    });
    const provider = new DataCloudRuntimeTruthProvider(client);

    await expect(provider.getRuntimeTruth('digital-marketing')).rejects.toThrow('not found');
  });

  it('provides query proof by recording and retrieving runtime truth', async () => {
    const calls: CapturedRequest[] = [];
    const client = new DataCloudKernelProviderClient({
      baseUrl: 'https://data-cloud.local',
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
      fetchImpl: async (input, init) => {
        calls.push(capture(input, init));
        const url = String(input);
        if (url.includes('runtime-truth/digital-marketing/latest')) {
          return jsonResponse({
            productUnitId: 'digital-marketing',
            runId: 'run-1',
            phase: 'build',
            status: 'healthy',
            observedAt: '2026-05-14T00:00:00.000Z',
            evidenceRefs: ['artifact:manifest'],
            providerMode: 'platform',
          });
        }
        return jsonResponse({ success: true, ref: 'truth/ref' });
      },
    });
    const provider = new DataCloudRuntimeTruthProvider(client);
    const snapshot = {
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build' as const,
      status: 'healthy',
      observedAt: '2026-05-14T00:00:00.000Z',
      evidenceRefs: ['artifact:manifest'],
      providerMode: 'platform' as const,
    };

    await provider.recordRuntimeTruth(snapshot, {
      required: true,
      correlationId: 'corr-1',
    });

    const retrieved = await provider.getRuntimeTruth('digital-marketing');

    expect(retrieved).toEqual({
      productUnitId: 'digital-marketing',
      observedAt: '2026-05-14T00:00:00.000Z',
    });
    expect(calls[0]?.url).toBe('https://data-cloud.local/api/v1/kernel/providers/runtime-truth');
    expect(calls[1]?.url).toBe('https://data-cloud.local/api/v1/kernel/providers/runtime-truth/digital-marketing/latest');
  });
});

interface CapturedRequest {
  readonly url: string;
  readonly headers: Record<string, string>;
}

function capture(input: string | URL | Request, init: RequestInit | undefined): CapturedRequest {
  const headers = init?.headers as Record<string, string>;
  return { url: String(input), headers };
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

function event(): KernelLifecycleEvent {
  const fixture = {
    metadata: {
      schemaVersion: '1.0.0',
      eventId: 'event-1',
      eventType: 'lifecycle.manifest.written',
      productUnitId: 'digital-marketing',
      runId: 'run-1',
      phase: 'build',
      timestamp: '2026-05-14T00:00:00.000Z',
      source: 'test',
      correlationId: 'corr-1',
    },
    payload: {
      manifestType: 'lifecycle-result',
      path: '.kernel/out/result.json',
      required: true,
      status: 'written',
    },
  } as const satisfies KernelLifecycleEvent;

  return fixture;
}
