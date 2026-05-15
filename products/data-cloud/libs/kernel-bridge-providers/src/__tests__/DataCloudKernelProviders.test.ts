import { describe, expect, it } from 'vitest';
import { validateKernelLifecycleProviderContext } from '@ghatana/kernel-product-contracts';
import {
  DataCloudArtifactProvider,
  DataCloudKernelProviderClient,
  DataCloudMemoryProvider,
  DataCloudRuntimeTruthProvider,
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
    await expect(context.events?.listEvents({ productUnitId: 'digital-marketing', correlationId: 'corr-1' })).resolves.toEqual([event()]);

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

function event() {
  return {
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
  };
}
