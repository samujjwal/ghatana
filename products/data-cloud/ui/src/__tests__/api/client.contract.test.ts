import { beforeEach, describe, expect, it, vi } from 'vitest';

import { DataCloudApiClient } from '@/api/client';
import { apiClient } from '@/lib/api/client';
import SessionBootstrap from '@/lib/auth/session';
import { TokenStorage } from '@/lib/auth/tokenStorage';
import {
  DATASET_CATALOG_BOUNDARY_MESSAGE,
  DATASET_DETAIL_BOUNDARY_MESSAGE,
  GLOBAL_SEARCH_BOUNDARY_MESSAGE,
  QUERY_VALIDATION_BOUNDARY_MESSAGE,
} from '@/lib/runtime-boundaries';

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
    await expect(client.getDatasets('orders')).rejects.toThrow(DATASET_CATALOG_BOUNDARY_MESSAGE);
    await expect(client.getDataset('orders', 'dataset-1')).rejects.toThrow(DATASET_DETAIL_BOUNDARY_MESSAGE);
    await expect(client.validateQuery('select 1')).rejects.toThrow(QUERY_VALIDATION_BOUNDARY_MESSAGE);
    await expect(client.search('orders')).rejects.toThrow(GLOBAL_SEARCH_BOUNDARY_MESSAGE);
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

describe('shared UI apiClient tenant propagation', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal('fetch', fetchMock);
    sessionStorage.clear();
    localStorage.clear();
  });

  it('forwards the bootstrapped tenant header on canonical entity requests', async () => {
    SessionBootstrap.setTenantId('tenant-contract');
    fetchMock.mockResolvedValueOnce(createJsonResponse({
      body: {
        entities: [],
        count: 0,
      },
    }));

    await apiClient.get('/entities/dc_collections', {
      params: { limit: 5 },
    });

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = new Headers(init.headers as HeadersInit);
    expect(headers.get('X-Tenant-ID')).toBe('tenant-contract');
  });

  it('surfaces canonical launcher error envelopes with status and nested error details', async () => {
    fetchMock.mockResolvedValueOnce(createJsonResponse({
      ok: false,
      status: 403,
      statusText: 'Forbidden',
      body: {
        error: {
          code: 'INVALID_CONFIRMATION_TOKEN',
          message: 'Confirmation token is invalid for this purge request',
          details: {
            confirmationToken: 'mismatch',
          },
        },
        meta: {
          requestId: 'req-123',
        },
      },
    }));

    await expect(apiClient.get('/governance/purge')).rejects.toMatchObject({
      code: 'INVALID_CONFIRMATION_TOKEN',
      message: 'Confirmation token is invalid for this purge request',
      status: 403,
      details: {
        confirmationToken: 'mismatch',
      },
    });
  });

  it('keeps supporting legacy top-level error bodies during transition', async () => {
    fetchMock.mockResolvedValueOnce(createJsonResponse({
      ok: false,
      status: 400,
      statusText: 'Bad Request',
      body: {
        code: 'MISSING_COLLECTION',
        message: 'Collection is required',
      },
    }));

    await expect(apiClient.get('/entities')).rejects.toMatchObject({
      code: 'MISSING_COLLECTION',
      message: 'Collection is required',
      status: 400,
    });
  });

  it('defaults browser requests to credentialed cookie sessions without injecting bearer headers', async () => {
    SessionBootstrap.setTenantId('tenant-cookie');
    TokenStorage.enableCookieSession();
    fetchMock.mockResolvedValueOnce(createJsonResponse({
      body: {
        entities: [],
        count: 0,
      },
    }));

    await apiClient.get('/entities/dc_collections');

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    const headers = new Headers(init.headers as HeadersInit);
    expect(init.credentials).toBe('include');
    expect(headers.get('Authorization')).toBeNull();
    expect(headers.get('X-Tenant-ID')).toBe('tenant-cookie');
  });

  it('falls back to canonical auth error codes for 401 responses without explicit error codes', async () => {
    fetchMock.mockResolvedValueOnce(createJsonResponse({
      ok: false,
      status: 401,
      statusText: 'Unauthorized',
      body: {
        message: 'Authentication required',
      },
    }));

    await expect(apiClient.get('/entities')).rejects.toMatchObject({
      code: 'AUTH_REQUIRED',
      message: 'Authentication required',
      status: 401,
    });
  });
});