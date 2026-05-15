import { describe, expect, it, vi } from 'vitest';
import {
  buildProductUnitIntentFromYappcArtifacts,
  buildYappcArtifactIntelligenceEvidence,
  exportProductUnitIntentFromYappcArtifacts,
  type ProductUnitIntentExportRequest,
} from '../ProductUnitIntentExportService';
import type { PageArtifactDocument } from '../../../../components/canvas/page/pageArtifactDocument';

function pageArtifact(overrides: Partial<PageArtifactDocument> = {}): PageArtifactDocument {
  return {
    artifactId: 'artifact-1',
    documentId: 'document-1',
    source: 'decompiled',
    residualIslandIds: ['legacy-chart'],
    artifactGraph: {
      graphId: 'project-1:graph',
      projectId: 'project-1',
      sourceType: 'route',
      source: 'src/routes/Home.tsx',
      importedAt: '2026-05-14T00:00:00.000Z',
      nodes: [
        {
          id: 'project-1',
          kind: 'product',
          label: 'Project 1',
        },
        {
          id: 'route-home',
          kind: 'page',
          label: 'Home route',
          sourceLocation: {
            filePath: 'src/routes/Home.tsx',
            startLine: 1,
            startColumn: 1,
            endLine: 20,
            endColumn: 1,
          },
        },
        {
          id: 'component-hero',
          kind: 'component',
          label: 'Hero component',
        },
        {
          id: 'source-home',
          kind: 'source',
          label: 'Home source',
        },
        {
          id: 'legacy-chart',
          kind: 'residual',
          label: 'Legacy chart',
        },
      ],
      edges: [
        {
          id: 'edge-1',
          from: 'project-1',
          to: 'route-home',
          kind: 'contains',
        },
      ],
      provenance: {
        createdBy: 'yappc-artifact-compiler',
        compiler: 'yappc-artifact-compiler',
        confidence: 0.7,
        residualIslandIds: ['legacy-chart'],
      },
    },
    syncStatus: 'synced',
    trustLevel: 'IMPORTED_REVIEW_REQUIRED',
    dataClassification: 'INTERNAL',
    createdBy: 'builder',
    updatedBy: 'builder',
    createdAt: '2026-05-14T00:00:00.000Z',
    updatedAt: '2026-05-14T00:00:00.000Z',
    serializedBuilderDocument: {} as PageArtifactDocument['serializedBuilderDocument'],
    ...overrides,
  };
}

const baseRequest: ProductUnitIntentExportRequest = {
  artifacts: [pageArtifact()],
  scope: {
    tenantId: 'tenant-1',
    workspaceId: 'workspace-1',
    projectId: 'project-1',
  },
  createdBy: 'user:builder',
  productUnitName: 'Project 1',
  correlationId: 'corr-1',
  registryProvider: 'kernel-product-registry',
  sourceProvider: 'yappc-creator',
  providerMode: 'bootstrap',
};

function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status: init.status ?? 200,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('ProductUnitIntentExportService', () => {
  it('builds ProductUnitIntent from YAPPC artifacts without mutating registry files', () => {
    const intent = buildProductUnitIntentFromYappcArtifacts(baseRequest);

    expect(intent.intentType).toBe('promote-candidate');
    expect(intent.scope).toEqual(baseRequest.scope);
    expect(intent.target.registryProvider).toBe('kernel-product-registry');
    expect(intent.productUnit.id).toBe('project-1');
    expect(intent.productUnit.surfaces[0]?.sourceRef).toBe('yappc:artifact:artifact-1');
  });

  it('builds schema-backed artifact intelligence evidence for imported graphs and residuals', () => {
    const evidence = buildYappcArtifactIntelligenceEvidence(baseRequest, {
      now: () => '2026-05-14T00:00:00.000Z',
    });

    expect(evidence.semanticArtifacts).toHaveLength(5);
    expect(evidence.graphSummaries[0]?.nodeCount).toBe(5);
    expect(evidence.residualIslandReports[0]?.islandCount).toBe(1);
    expect(evidence.riskHotspotReports[0]?.highestRiskLevel).toBe('high');
    expect(evidence.generatedChangeSets[0]?.changeCount).toBe(1);
  });

  it('falls back to a semantic artifact reference when a page has no graph snapshot', () => {
    const evidence = buildYappcArtifactIntelligenceEvidence(
      {
        ...baseRequest,
        artifacts: [
          pageArtifact({
            artifactGraph: undefined,
            residualIslandIds: [],
          }),
        ],
      },
      { now: () => '2026-05-14T00:00:00.000Z' },
    );

    expect(evidence.semanticArtifacts).toHaveLength(1);
    expect(evidence.graphSummaries).toHaveLength(0);
    expect(evidence.riskHotspotReports[0]?.highestRiskLevel).toBe('low');
  });

  it('marks graphless residual artifacts as medium-risk semantic evidence', () => {
    const evidence = buildYappcArtifactIntelligenceEvidence(
      {
        ...baseRequest,
        artifacts: [
          pageArtifact({
            artifactGraph: undefined,
            residualIslandIds: ['legacy-chart'],
          }),
        ],
      },
      { now: () => '2026-05-14T00:00:00.000Z' },
    );

    expect(evidence.semanticArtifacts[0]?.riskLevel).toBe('medium');
  });

  it('uses ProductUnit fallback for low-confidence graph hotspots without residual ids', () => {
    const evidence = buildYappcArtifactIntelligenceEvidence(
      {
        ...baseRequest,
        artifacts: [
          pageArtifact({
            residualIslandIds: undefined,
            artifactGraph: {
              ...pageArtifact().artifactGraph!,
              provenance: {
                createdBy: 'yappc-artifact-compiler',
                compiler: 'yappc-artifact-compiler',
                confidence: 0.7,
                residualIslandIds: [],
              },
            },
          }),
        ],
      },
      { now: () => '2026-05-14T00:00:00.000Z' },
    );

    expect(evidence.riskHotspotReports[0]?.hotspots[0]?.artifactId).toBe('project-1');
  });

  it('builds empty evidence bundles with explicit unknown provenance when no artifacts are available', () => {
    const evidence = buildYappcArtifactIntelligenceEvidence(
      {
        ...baseRequest,
        artifacts: [],
      },
      { now: () => '2026-05-14T00:00:00.000Z' },
    );

    expect(evidence.semanticArtifacts).toHaveLength(0);
    expect(evidence.residualIslandReports[0]?.provenanceRefs).toEqual([
      'yappc:artifact:unknown',
    ]);
  });

  it('uses ProductUnit id as the default name and marks trusted workspace artifacts implemented', () => {
    const intent = buildProductUnitIntentFromYappcArtifacts({
      ...baseRequest,
      productUnitName: '',
      artifacts: [pageArtifact({ trustLevel: 'TRUSTED_WORKSPACE' })],
    });

    expect(intent.productUnit.name).toBe('project-1');
    expect(intent.productUnit.surfaces[0]?.implementationStatus).toBe('implemented');
  });

  it('posts bootstrap intent and evidence to the backend handoff endpoint', async () => {
    const fetchImpl = vi.fn(async () =>
      jsonResponse({ intentId: 'intent:yappc:project-1:corr-1', status: 'queued', evidenceRef: 'evidence:bundle-1' }),
    );

    const response = await exportProductUnitIntentFromYappcArtifacts(baseRequest, {
      endpoint: '/handoff',
      fetchImpl: fetchImpl as unknown as typeof fetch,
      now: () => '2026-05-14T00:00:00.000Z',
    });

    expect(response.status).toBe('queued');
    expect(response.evidenceRef).toBe('evidence:bundle-1');
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(fetchImpl.mock.calls[0]?.[0]).toBe('/handoff');
    const init = fetchImpl.mock.calls[0]?.[1] as RequestInit;
    expect((init.headers as Record<string, string>)['X-Correlation-Id']).toBe('corr-1');
    expect((init.headers as Record<string, string>)['X-Ghatana-Tenant-Id']).toBe('tenant-1');
    expect(JSON.parse(String(init.body))).toMatchObject({
      mode: 'preview',
      providerMode: 'bootstrap',
      evidenceMetadata: {
        residualIslandReports: 'confidential',
        riskHotspotReports: 'confidential',
      },
      blockedReasons: ['artifact-confidence-below-threshold', 'residual-island-review-required'],
    });
  });

  it('stores evidence through Data Cloud before platform-mode handoff', async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ evidenceRef: 'datacloud:evidence:1' }))
      .mockResolvedValueOnce(jsonResponse({ intentId: 'intent:yappc:project-1:corr-1', status: 'accepted' }));

    const response = await exportProductUnitIntentFromYappcArtifacts(
      { ...baseRequest, providerMode: 'platform' },
      {
        endpoint: '/handoff',
        dataCloudEvidenceEndpoint: '/datacloud/evidence',
        fetchImpl: fetchImpl as unknown as typeof fetch,
        now: () => '2026-05-14T00:00:00.000Z',
      },
    );

    expect(response.status).toBe('accepted');
    expect(fetchImpl.mock.calls.map((call) => call[0])).toEqual([
      '/datacloud/evidence',
      '/handoff',
    ]);
    expect(JSON.parse(String(fetchImpl.mock.calls[0]?.[1]?.body))).toMatchObject({
      correlationId: 'corr-1',
      scope: baseRequest.scope,
      evidenceMetadata: {
        semanticArtifacts: 'internal',
      },
    });
    expect(JSON.parse(String(fetchImpl.mock.calls[1]?.[1]?.body))).toMatchObject({
      providerMode: 'platform',
      evidence: {
        evidenceRefs: ['datacloud:evidence:1'],
      },
    });
  });

  it('accepts blocked and failed handoff statuses from the backend', async () => {
    await expect(
      exportProductUnitIntentFromYappcArtifacts(baseRequest, {
        fetchImpl: vi.fn(async () =>
          jsonResponse({
            intentId: 'intent:yappc:project-1:corr-1',
            status: 'blocked',
            blockedReasons: ['approval-required'],
            previewRef: 'preview:1',
          }),
        ) as unknown as typeof fetch,
      }),
    ).resolves.toMatchObject({
      status: 'blocked',
      blockedReasons: ['approval-required'],
      previewRef: 'preview:1',
    });

    await expect(
      exportProductUnitIntentFromYappcArtifacts(baseRequest, {
        fetchImpl: vi.fn(async () =>
          jsonResponse({
            intentId: 'intent:yappc:project-1:corr-1',
            status: 'failed',
          }),
        ) as unknown as typeof fetch,
      }),
    ).resolves.toMatchObject({ status: 'failed' });
  });

  it('fails closed when platform mode has no Data Cloud evidence endpoint', async () => {
    await expect(
      exportProductUnitIntentFromYappcArtifacts(
        { ...baseRequest, providerMode: 'platform' },
        { fetchImpl: vi.fn() as unknown as typeof fetch },
      ),
    ).rejects.toThrow('requires a Data Cloud evidence endpoint');
  });

  it('surfaces backend and response-shape failures', async () => {
    await expect(
      exportProductUnitIntentFromYappcArtifacts(baseRequest, {
        fetchImpl: vi.fn(async () => new Response('nope', { status: 503 })) as unknown as typeof fetch,
      }),
    ).rejects.toThrow('ProductUnitIntent export failed');

    await expect(
      exportProductUnitIntentFromYappcArtifacts(baseRequest, {
        fetchImpl: vi.fn(async () => jsonResponse({ ok: true })) as unknown as typeof fetch,
      }),
    ).rejects.toThrow('unexpected shape');

    await expect(
      exportProductUnitIntentFromYappcArtifacts(baseRequest, {
        fetchImpl: vi.fn(async () => jsonResponse(null)) as unknown as typeof fetch,
      }),
    ).rejects.toThrow('unexpected shape');
  });

  it('surfaces Data Cloud evidence persistence failures before intent handoff', async () => {
    await expect(
      exportProductUnitIntentFromYappcArtifacts(
        { ...baseRequest, providerMode: 'platform' },
        {
          dataCloudEvidenceEndpoint: '/datacloud/evidence',
          fetchImpl: vi.fn(async () => new Response('provider down', { status: 502 })) as unknown as typeof fetch,
        },
      ),
    ).rejects.toThrow('Data Cloud evidence persistence failed');
  });

  it('requires Data Cloud evidence persistence responses to include evidence refs', async () => {
    await expect(
      exportProductUnitIntentFromYappcArtifacts(
        { ...baseRequest, providerMode: 'platform' },
        {
          dataCloudEvidenceEndpoint: '/datacloud/evidence',
          fetchImpl: vi.fn(async () => jsonResponse({ ok: true })) as unknown as typeof fetch,
        },
      ),
    ).rejects.toThrow('did not include evidence refs');
  });

  it('still reports failed responses when error bodies cannot be read', async () => {
    const unreadableResponse = {
      ok: false,
      status: 500,
      text: async () => {
        throw new Error('body unavailable');
      },
    } as unknown as Response;

    await expect(
      exportProductUnitIntentFromYappcArtifacts(
        { ...baseRequest, providerMode: 'platform' },
        {
          dataCloudEvidenceEndpoint: '/datacloud/evidence',
          fetchImpl: vi.fn(async () => unreadableResponse) as unknown as typeof fetch,
        },
      ),
    ).rejects.toThrow('Data Cloud evidence persistence failed (HTTP 500)');

    await expect(
      exportProductUnitIntentFromYappcArtifacts(baseRequest, {
        fetchImpl: vi.fn(async () => unreadableResponse) as unknown as typeof fetch,
      }),
    ).rejects.toThrow('ProductUnitIntent export failed (HTTP 500)');
  });

  it('uses global fetch when no custom fetch implementation is supplied', async () => {
    const originalFetch = globalThis.fetch;
    const fetchMock = vi.fn(async () =>
      jsonResponse({ intentId: 'intent:yappc:project-1:corr-1', status: 'queued' }),
    );
    globalThis.fetch = fetchMock as unknown as typeof fetch;

    try {
      await expect(exportProductUnitIntentFromYappcArtifacts(baseRequest)).resolves.toEqual({
        intentId: 'intent:yappc:project-1:corr-1',
        status: 'queued',
      });
      expect(fetchMock).toHaveBeenCalledTimes(1);
    } finally {
      globalThis.fetch = originalFetch;
    }
  });

  it('rejects missing scope and empty artifact lists before network calls', () => {
    expect(() =>
      buildProductUnitIntentFromYappcArtifacts({
        ...baseRequest,
        scope: { ...baseRequest.scope, projectId: '' },
      }),
    ).toThrow('scope.projectId');

    expect(() =>
      buildProductUnitIntentFromYappcArtifacts({
        ...baseRequest,
        artifacts: [],
      }),
    ).toThrow('at least one page artifact');
  });
});
