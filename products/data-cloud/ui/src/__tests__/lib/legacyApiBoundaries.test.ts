import { describe, expect, it, vi, beforeEach } from 'vitest';

const { mockApiClient } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('../../lib/api/client', () => ({
  apiClient: mockApiClient,
}));

vi.mock('../../lib/api/collections', () => ({
  collectionsApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
  },
}));

vi.mock('../../lib/api/workflows', () => ({
  workflowsApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    execute: vi.fn(),
    getExecutions: vi.fn(),
  },
}));

vi.mock('../../lib/api/collection-data-client', () => ({
  collectionDataClient: {
    setBaseURL: vi.fn(),
    setTenantId: vi.fn(),
    listRecords: vi.fn(),
    createRecord: vi.fn(),
    updateRecord: vi.fn(),
    deleteRecord: vi.fn(),
  },
}));

import {
  convertNLToSQL,
  getSchemaSuggestions,
  detectAnomalies,
  getEnrichmentSuggestions,
  getPipelineOptimisationHints,
  getQueryRecommendations,
} from '../../lib/api/ai';
import { dataCloudApi } from '../../lib/api/data-cloud-api';

describe('legacy API boundaries', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockApiClient.post.mockResolvedValue({ ok: true });
    mockApiClient.get.mockResolvedValue([]);
  });

  it('routes supported AI helpers to canonical OpenAPI paths', async () => {
    await convertNLToSQL('tenant-a', { query: 'orders by revenue', collectionName: 'orders' });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/analytics/suggest',
      { query: 'orders by revenue', collectionName: 'orders' },
      { params: { tenantId: 'tenant-a' } },
    );

    await getSchemaSuggestions('tenant-a', {
      collectionName: 'orders',
      currentSchema: { id: 'string' },
      sampleData: [{ id: '1' }],
    });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/entities/orders/suggest',
      { currentSchema: { id: 'string' }, sampleData: [{ id: '1' }] },
      { params: { tenantId: 'tenant-a' } },
    );

    mockApiClient.post.mockResolvedValueOnce([
      {
        id: 'anom-1',
        type: 'spike',
        severity: 'warning',
        metric: 'count',
        timestamp: '2026-04-15T10:00:00Z',
        value: 120,
        expectedValue: 80,
        deviation: 40,
        description: 'Entity count spiked above the expected baseline.',
        suggestedAction: 'Inspect recent ingestion jobs.',
      },
    ]);
    await detectAnomalies('tenant-a', { collectionName: 'orders', metrics: ['count'] });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/entities/orders/anomalies',
      { collectionName: 'orders', metrics: ['count'] },
      { params: { tenantId: 'tenant-a' } },
    );

    mockApiClient.post.mockResolvedValueOnce({
      pipelineId: 'wf-1',
      hints: [
        {
          type: 'performance',
          title: 'Reduce repeated enrichment lookups',
          description: 'Cache repeated upstream calls to reduce end-to-end latency.',
          confidence: 0.92,
          impact: 'high',
          fallback: false,
        },
      ],
      generatedAt: '2026-04-14T10:35:00Z',
    });
    await getPipelineOptimisationHints('wf-1');
    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/pipelines/wf-1/optimise-hint',
      {},
    );
  });

  it('fails explicitly for unsupported legacy AI helpers', async () => {
    await expect(
      getEnrichmentSuggestions('tenant-a', { collectionName: 'orders', entityId: '1' }),
    ).rejects.toThrow(/not exposed by the current Data Cloud launcher API/i);

    await expect(
      getQueryRecommendations('tenant-a', 'orders', 'select \*'),
    ).rejects.toThrow(/not exposed by the current Data Cloud launcher API/i);
  });

  it('uses canonical validation and search routes in the facade and rejects unsupported ones', async () => {
    mockApiClient.post.mockResolvedValue({
      valid: true,
      violations: [],
      score: 1,
      suggestions: [],
    });
    await dataCloudApi.validateEntity('orders', { id: '1' });
    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/entities/orders/validate',
      { data: { id: '1' } },
      { params: { tenantId: 'default' } },
    );

    mockApiClient.get.mockResolvedValue([
      {
        entityId: 'entity-1',
        collectionId: 'orders',
        score: 0.98,
        highlights: { description: ['matching order id'] },
        data: { id: '1', total: 42 },
      },
    ]);
    await dataCloudApi.search('abc', { collectionId: 'orders', limit: 5 });
    expect(mockApiClient.get).toHaveBeenCalledWith(
      '/entities/orders/search',
      { params: { q: 'abc', tenantId: 'default', limit: 5 } },
    );

    await expect(dataCloudApi.getExecutionById('exec-1')).rejects.toThrow(/Execution-by-ID lookup is not exposed/i);
    await expect(dataCloudApi.suggestSchema([{ id: '1' }])).rejects.toThrow(/Collection-agnostic schema suggestion is not exposed/i);
    await expect(dataCloudApi.search('abc')).rejects.toThrow(/Cross-collection search is not exposed/i);
  });
});