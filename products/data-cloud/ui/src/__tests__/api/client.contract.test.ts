import { beforeEach, describe, expect, it, vi } from 'vitest';

import { DataCloudApiClient } from '@/api/client';

interface MockResponseInit {
  ok?: boolean;
  status?: number;
  statusText?: string;
  body?: unknown;
}

function createJsonResponse(init: MockResponseInit = {}): Response {
  const body = init.body;
  return {
    ok: init.ok ?? true,
    status: init.status ?? 200,
    statusText: init.statusText ?? 'OK',
    headers: new Headers({ 'content-type': 'application/json' }),
    json: vi.fn().mockResolvedValue(body),
    text: vi.fn().mockResolvedValue(typeof body === 'string' ? body : JSON.stringify(body ?? {})),
    blob: vi.fn(),
  } as unknown as Response;
}

describe('DataCloudApiClient contract mapping', () => {
  let client: DataCloudApiClient;
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    client = new DataCloudApiClient('http://localhost:8080');
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  it('maps canonical dc_collections entity payloads into collection results', async () => {
    fetchMock.mockResolvedValueOnce(createJsonResponse({
      body: {
        entities: [
          {
            id: 'col-1',
            data: {
              name: 'Customers',
              description: 'Canonical entity-backed collection',
              schemaType: 'entity',
              status: 'active',
              entityCount: 42,
              schema: { fields: [{ name: 'id', type: 'string', required: true }] },
              tags: ['crm'],
              createdBy: 'contract-runner',
            },
            createdAt: '2026-04-15T07:00:00Z',
            updatedAt: '2026-04-15T07:05:00Z',
          },
        ],
        count: 1,
      },
    }));

    const collections = await client.getCollections({ page: 2, pageSize: 25, query: 'Customers' });

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/entities/dc_collections?limit=25&offset=25&search=Customers',
      expect.objectContaining({ method: 'GET' }),
    );
    expect(collections).toEqual([
      {
        id: 'col-1',
        name: 'Customers',
        description: 'Canonical entity-backed collection',
        schemaType: 'entity',
        status: 'active',
        isActive: true,
        entityCount: 42,
        schema: { fields: [{ name: 'id', type: 'string', required: true }] },
        tags: ['crm'],
        createdAt: '2026-04-15T07:00:00Z',
        updatedAt: '2026-04-15T07:05:00Z',
        createdBy: 'contract-runner',
      },
    ]);
  });

  it('maps canonical analytics query responses into the legacy tabular shape', async () => {
    fetchMock.mockResolvedValueOnce(createJsonResponse({
      body: {
        queryId: 'q-1',
        queryType: 'direct',
        rowCount: 2,
        columnCount: 2,
        rows: [
          { id: '1', total: 10 },
          { id: '2', total: 20 },
        ],
        executionTimeMs: 18,
        optimized: true,
        timestamp: '2026-04-15T07:10:00Z',
      },
    }));

    const result = await client.executeQuery('SELECT id, total FROM metrics', 10);

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/analytics/query',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(result).toEqual({
      columns: ['id', 'total'],
      rows: [
        ['1', 10],
        ['2', 20],
      ],
      rowCount: 2,
      executionTime: 18,
    });
  });

  it('fails explicitly for stale unsupported dataset, validation, and search helpers', async () => {
    await expect(client.getDatasets('orders')).rejects.toThrow(/dataset catalog routes are not exposed/i);
    await expect(client.getDataset('orders', 'dataset-1')).rejects.toThrow(/dataset detail routes are not exposed/i);
    await expect(client.validateQuery('select 1')).rejects.toThrow(/Standalone query validation is not exposed/i);
    await expect(client.search('orders')).rejects.toThrow(/Global cross-catalog search is not exposed/i);
  });

  it('maps canonical feature-store list and detail payloads into typed feature models', async () => {
    fetchMock
      .mockResolvedValueOnce(createJsonResponse({
        body: [
          {
            id: 'feature-1',
            name: 'engagement_score',
            description: 'Computed engagement score',
            dataType: 'FLOAT',
            version: '1.0.0',
            tags: ['ml', 'customer'],
            createdAt: '2026-04-15T08:00:00Z',
            updatedAt: '2026-04-15T08:10:00Z',
          },
        ],
      }))
      .mockResolvedValueOnce(createJsonResponse({
        body: {
          id: 'feature-1',
          name: 'engagement_score',
          description: 'Computed engagement score',
          dataType: 'FLOAT',
          version: '1.0.0',
          tags: ['ml', 'customer'],
          createdAt: '2026-04-15T08:00:00Z',
          updatedAt: '2026-04-15T08:10:00Z',
        },
      }));

    const features = await client.getFeatures();
    const feature = await client.getFeature('feature-1');

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      'http://localhost:8080/api/v1/features',
      expect.objectContaining({ method: 'GET' }),
    );
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      'http://localhost:8080/api/v1/features/feature-1',
      expect.objectContaining({ method: 'GET' }),
    );
    expect(features).toHaveLength(1);
    expect(feature).toMatchObject({
      id: 'feature-1',
      name: 'engagement_score',
      dataType: 'FLOAT',
    });
  });

  it('registers features through the canonical feature-store route', async () => {
    fetchMock.mockResolvedValueOnce(createJsonResponse({
      body: {
        id: 'feature-2',
        name: 'churn_risk',
        description: 'Predicted churn risk score',
        dataType: 'FLOAT',
        version: '2.1.0',
        tags: ['ml', 'risk'],
        createdAt: '2026-04-15T08:20:00Z',
        updatedAt: '2026-04-15T08:20:00Z',
      },
    }));

    const created = await client.registerFeature({
      name: 'churn_risk',
      description: 'Predicted churn risk score',
      dataType: 'FLOAT',
      version: '2.1.0',
      tags: ['ml', 'risk'],
    });

    expect(fetchMock).toHaveBeenCalledWith(
      'http://localhost:8080/api/v1/features',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({
          name: 'churn_risk',
          description: 'Predicted churn risk score',
          dataType: 'FLOAT',
          version: '2.1.0',
          tags: ['ml', 'risk'],
        }),
      }),
    );
    expect(created).toMatchObject({ id: 'feature-2', name: 'churn_risk' });
  });
});