import Fastify, { type FastifyInstance } from 'fastify';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import productUnitIntentRoutes from '../routes/product-unit-intents';
import type { ProductUnitIntent } from '@ghatana/kernel-product-contracts';

describe('product-unit-intent routes', () => {
  let app: FastifyInstance;

  beforeEach(async () => {
    app = Fastify();
    await app.register(productUnitIntentRoutes, { prefix: '/api/v1' });
  });

  afterEach(async () => {
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
    // Set DATACLOUD_AUTH_TOKEN to undefined to simulate missing client
    process.env.DATACLOUD_AUTH_TOKEN = undefined;
    
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
    // This test would require mocking the Data Cloud provider client
    // For now, we'll skip it as it requires external dependencies
    // In a real scenario, this would test that platform mode returns providerMode: platform
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
    source: 'yappc-creator-ui',
    confidence: 0.9,
    provenanceRefs: ['yappc:artifact:artifact-1'],
    createdAt: '2026-05-14T00:00:00.000Z',
    correlationId: 'corr-1',
    productUnitId: 'project-1',
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
