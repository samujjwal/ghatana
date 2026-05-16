import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import productUnitIntentRoutes from '../routes/product-unit-intents';
import type { ProductUnitIntent } from '@ghatana/kernel-product-contracts';

describe('product-unit-intent routes', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    delete process.env.KERNEL_LIFECYCLE_BASE_URL;
    delete process.env.KERNEL_LIFECYCLE_AUTH_TOKEN;
    process.env.DATACLOUD_AUTH_TOKEN = 'datacloud-token';
    app = Fastify();
    await app.register(productUnitIntentRoutes, { prefix: '/api/v1' });
  });

  afterEach(async () => {
    vi.restoreAllMocks();
    await app.close();
  });

  it('rejects unauthenticated requests', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      payload: { intent: buildIntent(), evidence: { evidenceRefs: ['evidence://one'] } },
    });

    expect(response.statusCode).toBe(401);
  });

  it('rejects invalid ProductUnitIntent bodies', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      headers: authHeaders(),
      payload: { intent: { schemaVersion: 'bad' }, evidence: { evidenceRefs: ['evidence://one'] } },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({ error: 'Invalid ProductUnitIntent' });
  });

  it('rejects missing evidence on promote-candidate', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      headers: authHeaders(),
      payload: { intent: buildIntent({ intentType: 'promote-candidate' }), evidence: {} },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({ blockedReasons: ['missing-evidence'] });
  });

  it('previews bootstrap intents without applying registry mutations', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      headers: authHeaders(),
      payload: {
        intent: buildIntent({ intentType: 'promote-candidate' }),
        evidence: { evidenceRefs: ['evidence://artifact-intelligence'] },
        providerMode: 'bootstrap',
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      intentId: 'intent-1',
      status: 'accepted',
      evidenceRef: 'evidence://artifact-intelligence',
      blockedReasons: [],
    });
  });

  it('accepts schema-backed artifact intelligence evidence bundles', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      headers: authHeaders(),
      payload: {
        intent: buildIntent({ intentType: 'promote-candidate' }),
        evidence: {
          semanticArtifacts: [buildSemanticArtifactEvidence()],
        },
        providerMode: 'bootstrap',
      },
    });

    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      evidenceRef: 'semantic-artifact:project-1:web',
      blockedReasons: [],
    });
  });

  it('rejects malformed schema-backed artifact intelligence evidence', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      headers: authHeaders(),
      payload: {
        intent: buildIntent({ intentType: 'promote-candidate' }),
        evidence: {
          semanticArtifacts: [{ evidenceId: 'not-enough' }],
        },
      },
    });

    expect(response.statusCode).toBe(400);
    expect(response.json()).toMatchObject({
      error: 'Invalid artifact intelligence evidence',
      blockedReasons: ['invalid-evidence'],
    });
  });

  it('requires explicit permission for apply', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      headers: authHeaders(),
      payload: {
        intent: buildIntent({ intentType: 'promote-candidate' }),
        evidence: { evidenceRefs: ['evidence://artifact-intelligence'] },
        mode: 'apply',
      },
    });

    expect(response.statusCode).toBe(403);
    expect(response.json()).toMatchObject({
      status: 'blocked',
      blockedReasons: ['apply-requires-explicit-permission'],
    });
  });

  it('queues apply when explicit permission is present', async () => {
    process.env.KERNEL_LIFECYCLE_BASE_URL = 'https://kernel.example';
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        schemaVersion: '1.0.0',
        intentId: 'intent-1',
        status: 'applied',
        productUnitId: 'external-demo',
        providerMode: 'bootstrap',
        registryProviderId: 'ghatana-file-registry',
        sourceProviderId: 'github',
        lifecycleEventRefs: ['kernel://event/1'],
        provenanceRefs: ['evidence://artifact-intelligence'],
        runtimeTruthRefs: ['kernel://runtime/1'],
        blockedReasons: [],
        errors: [],
        correlationId: 'corr-apply',
      }),
    } as Response);

    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      headers: { ...authHeaders(), 'x-yappc-intent-apply': 'true' },
      payload: {
        intent: buildIntent({ intentType: 'promote-candidate' }),
        evidence: { evidenceRefs: ['evidence://artifact-intelligence'] },
        mode: 'apply',
      },
    });

    expect(response.statusCode).toBe(202);
    expect(response.json()).toMatchObject({ status: 'queued', blockedReasons: [] });
  });

  it('requires stored Data Cloud evidence refs in platform mode', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      headers: authHeaders(),
      payload: {
        intent: buildIntent({ intentType: 'promote-candidate' }),
        evidence: { evidenceRefs: ['evidence://local-only'] },
        providerMode: 'platform',
      },
    });

    expect(response.statusCode).toBe(409);
    expect(response.json()).toMatchObject({
      status: 'blocked',
      blockedReasons: ['platform-mode-requires-data-cloud-evidence-ref'],
    });
  });

  it('rejects scope mismatches', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      headers: { ...authHeaders(), 'x-tenant-id': 'tenant-2' },
      payload: { intent: buildIntent(), evidence: { evidenceRefs: ['evidence://one'] } },
    });

    expect(response.statusCode).toBe(403);
    expect(response.json()).toMatchObject({ error: 'tenant scope mismatch' });
  });

  it('rejects platform mode when Data Cloud provider client is not configured', async () => {
    delete process.env.DATACLOUD_AUTH_TOKEN;
    
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      headers: authHeaders(),
      payload: {
        intent: buildIntent({ intentType: 'promote-candidate' }),
        evidence: { evidenceRefs: ['datacloud://memory/ref-1'] },
        providerMode: 'platform',
      },
    });

    expect(response.statusCode).toBe(503);
    expect(response.json()).toMatchObject({
      status: 'blocked',
      blockedReasons: ['platform-mode-requires-data-cloud-provider-client'],
    });
  });

  it('accepts bootstrap mode with local evidence refs', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      headers: authHeaders(),
      payload: {
        intent: buildIntent({ intentType: 'promote-candidate' }),
        evidence: { evidenceRefs: ['evidence://local-only'] },
        providerMode: 'bootstrap',
      },
    });

    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.status).toBe('accepted');
    expect(body.providerMode).toBe('bootstrap');
    expect(body.blockedReasons).toEqual([]);
  });

  it('marks result providerMode as bootstrap in bootstrap mode', async () => {
    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      headers: authHeaders(),
      payload: {
        intent: buildIntent({ intentType: 'promote-candidate' }),
        evidence: { evidenceRefs: ['evidence://local-only'] },
        providerMode: 'bootstrap',
      },
    });

    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.providerMode).toBe('bootstrap');
  });

  it('marks result providerMode as platform in platform mode', async () => {
    process.env.KERNEL_LIFECYCLE_BASE_URL = 'https://kernel.example';
    vi.spyOn(globalThis, 'fetch').mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => ({
        schemaVersion: '1.0.0',
        intentId: 'intent-1',
        status: 'previewed',
        productUnitId: 'external-demo',
        providerMode: 'platform',
        registryProviderId: 'ghatana-file-registry',
        sourceProviderId: 'github',
        lifecycleEventRefs: ['kernel://event/1'],
        provenanceRefs: ['datacloud://memory/ref-1'],
        runtimeTruthRefs: ['kernel://runtime/1'],
        blockedReasons: [],
        errors: [],
        correlationId: 'corr-platform',
      }),
    } as Response);

    const response = await app.inject({
      method: 'POST',
      url: '/api/v1/yappc/product-unit-intents',
      headers: authHeaders(),
      payload: {
        intent: buildIntent({ intentType: 'promote-candidate' }),
        evidence: { evidenceRefs: ['datacloud://memory/ref-1'] },
        providerMode: 'platform',
      },
    });

    expect(response.statusCode).toBe(200);
    const body = response.json();
    expect(body.providerMode).toBe('platform');
    expect(body.status).toBe('accepted');
  });

  describe('blocked fallback behavior', () => {
    it('returns blocked status when KERNEL_BASE_URL is not configured', async () => {
      const savedUrl = process.env.KERNEL_LIFECYCLE_BASE_URL;
      process.env.KERNEL_LIFECYCLE_BASE_URL = '';
      try {
        const response = await app.inject({
          method: 'POST',
          url: '/api/v1/yappc/product-unit-intents',
          headers: { ...authHeaders(), 'x-yappc-intent-apply': 'true' },
          payload: {
            intent: buildIntent({ intentType: 'promote-candidate' }),
            evidence: { evidenceRefs: ['evidence://artifact-intelligence'] },
            providerMode: 'bootstrap',
            mode: 'apply',
          },
        });

        const body = response.json();
        expect(response.statusCode).toBe(503);
        expect(body.status).toBe('blocked');
        expect(body.blockedReasons).toContain('kernel-lifecycle-service-unavailable');
      } finally {
        if (savedUrl === undefined) {
          delete process.env.KERNEL_LIFECYCLE_BASE_URL;
        } else {
          process.env.KERNEL_LIFECYCLE_BASE_URL = savedUrl;
        }
      }
    });

    it('responds with status: blocked when Kernel returns a 500 error', async () => {
      const savedUrl = process.env.KERNEL_LIFECYCLE_BASE_URL;
      process.env.KERNEL_LIFECYCLE_BASE_URL = 'http://localhost:19999';
      try {
        const response = await app.inject({
          method: 'POST',
          url: '/api/v1/yappc/product-unit-intents',
          headers: { ...authHeaders(), 'x-yappc-intent-apply': 'true' },
          payload: {
            intent: buildIntent({ intentType: 'promote-candidate' }),
            evidence: { evidenceRefs: ['evidence://artifact-intelligence'] },
            providerMode: 'bootstrap',
            mode: 'apply',
          },
        });

        const body = response.json();
        expect(response.statusCode).toBe(503);
        expect(body.status).toBe('blocked');
        expect(
          body.blockedReasons?.includes('kernel-service-unreachable') ||
            body.blockedReasons?.some((r: string) => r.startsWith('kernel-service-http-')),
        ).toBeTruthy();
      } finally {
        if (savedUrl === undefined) {
          delete process.env.KERNEL_LIFECYCLE_BASE_URL;
        } else {
          process.env.KERNEL_LIFECYCLE_BASE_URL = savedUrl;
        }
      }
    });

    it('never returns status: applied in blocked scenario', async () => {
      const savedUrl = process.env.KERNEL_LIFECYCLE_BASE_URL;
      process.env.KERNEL_LIFECYCLE_BASE_URL = '';
      try {
        const response = await app.inject({
          method: 'POST',
          url: '/api/v1/yappc/product-unit-intents',
          headers: { ...authHeaders(), 'x-yappc-intent-apply': 'true' },
          payload: {
            intent: buildIntent({ intentType: 'promote-candidate' }),
            evidence: { evidenceRefs: ['evidence://artifact-intelligence'] },
            mode: 'apply',
          },
        });

        const body = response.json();
        expect(body.status).not.toBe('applied');
      } finally {
        if (savedUrl === undefined) {
          delete process.env.KERNEL_LIFECYCLE_BASE_URL;
        } else {
          process.env.KERNEL_LIFECYCLE_BASE_URL = savedUrl;
        }
      }
    });
  });
});

function authHeaders(): Record<string, string> {
  return {
    'x-user-id': 'user-1',
    'x-user-role': 'BUILDER',
    'x-tenant-id': 'tenant-1',
    'x-workspace-id': 'workspace-1',
    'x-project-id': 'project-1',
  };
}

function buildSemanticArtifactEvidence(): Record<string, unknown> {
  return {
    schemaVersion: '1.0.0',
    evidenceId: 'semantic-artifact:project-1:web',
    evidenceType: 'semantic-artifact-reference',
    tenantId: 'tenant-1',
    workspaceId: 'workspace-1',
    projectId: 'project-1',
    productUnitId: 'project-1',
    confidence: 0.9,
    provenanceRefs: ['yappc:artifact:artifact-1'],
    createdAt: '2026-05-14T00:00:00.000Z',
    createdBy: 'user:builder',
    correlationId: 'corr-1',
    privacyClassification: 'internal',
    retention: {
      expiresAt: '2027-05-14T00:00:00.000Z',
    },
    artifactId: 'project-1-web',
    artifactKind: 'ui-route',
    displayName: 'Project 1 Web',
    semanticTags: ['route', 'web'],
    riskLevel: 'low',
  };
}

function buildIntent(overrides: Partial<ProductUnitIntent> = {}): ProductUnitIntent {
  return {
    schemaVersion: '1.0.0',
    intentId: 'intent-1',
    intentType: 'create',
    scope: {
      tenantId: 'tenant-1',
      workspaceId: 'workspace-1',
      projectId: 'project-1',
    },
    producer: {
      id: 'yappc-control-plane',
      type: 'yappc',
      correlationId: 'corr-1',
    },
    target: {
      registryProvider: 'ghatana-file-registry',
      sourceProvider: 'github',
    },
    productUnit: {
      id: 'external-demo',
      name: 'External Demo',
      kind: 'external-application',
      surfaces: [
        {
          id: 'external-demo-web',
          type: 'web',
          implementationStatus: 'planned',
          sourceRef: 'https://github.com/example/external-demo.git',
        },
      ],
    },
    requestedLifecycle: {
      profile: 'standard-web-product',
      enableExecution: false,
      phases: ['validate', 'build'],
    },
    governanceHints: {
      evidenceRequired: true,
      requiresHumanApproval: true,
    },
    provenance: {
      sourceSystem: 'yappc',
      sourceArtifactRefs: ['artifact:yappc:blueprint-1'],
      createdBy: 'user:builder',
      createdAt: '2026-05-14T00:00:00.000Z',
      evidenceRefs: ['evidence:canvas:1'],
    },
    ...overrides,
  };
}
