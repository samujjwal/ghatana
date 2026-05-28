import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockApiClient } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

const { mockCollectionsApi } = vi.hoisted(() => ({
  mockCollectionsApi: {
    list: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    delete: vi.fn(),
    getSchema: vi.fn(),
    updateSchema: vi.fn(),
    getStats: vi.fn(),
  },
}));

vi.mock('../../lib/api/client', () => ({
  apiClient: mockApiClient,
}));

vi.mock('../../lib/api/collections', () => ({
  collectionsApi: mockCollectionsApi,
  default: mockCollectionsApi,
}));

import {
  executeAnalyticsQuery,
  explainAnalyticsQuery,
  executeFederatedQuery,
  evaluateQueryPolicy,
  useCollectionEntityCounts,
  useAnalyticsAiSuggestions,
} from '../../api/analytics.service';
import SessionBootstrap from '../../lib/auth/session';
import { renderHook, waitFor } from '@testing-library/react';
import { TestWrapper } from '../test-utils/wrapper';
import type { Collection } from '../../lib/api/collections';

function makeCollection(overrides: Partial<Collection> = {}): Collection {
  return {
    id: 'col-1',
    name: 'events',
    description: 'Events collection',
    schemaType: 'event',
    status: 'active',
    isActive: true,
    entityCount: 42,
    schema: { fields: [] },
    tags: [],
    createdAt: '2026-05-28T00:00:00Z',
    updatedAt: '2026-05-28T00:00:00Z',
    createdBy: 'system',
    lifecycleStatus: 'PUBLISHED',
    operationalStatus: 'healthy',
    owner: 'system',
    ...overrides,
  };
}

describe('analytics.service', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    SessionBootstrap.setTenantId('tenant-a');
  });

  it('uses backend collection registry counts for analytics summary stats', async () => {
    mockCollectionsApi.list.mockResolvedValue({
      items: [
        makeCollection({ name: 'events', entityCount: 120 }),
        makeCollection({ id: 'col-2', name: 'users', entityCount: 35 }),
      ],
      total: 2,
      page: 1,
      pageSize: 50,
      hasMore: false,
    });

    const { result } = renderHook(() => useCollectionEntityCounts(['events', 'users']), {
      wrapper: TestWrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual([
      { collection: 'events', count: 120, executionTimeMs: 0 },
      { collection: 'users', count: 35, executionTimeMs: 0 },
    ]);
    expect(mockApiClient.post).not.toHaveBeenCalledWith(
      '/analytics/query',
      expect.anything(),
      expect.anything(),
    );
  });

  it('returns unavailable counts when registry metadata is missing instead of fabricating zero', async () => {
    mockCollectionsApi.list.mockResolvedValue({
      items: [makeCollection({ name: 'events', entityCount: 120 })],
      total: 1,
      page: 1,
      pageSize: 50,
      hasMore: false,
    });

    const { result } = renderHook(() => useCollectionEntityCounts(['events', 'orders']), {
      wrapper: TestWrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual([
      { collection: 'events', count: 120, executionTimeMs: 0 },
      { collection: 'orders', count: null, executionTimeMs: 0 },
    ]);
  });

  it('executes direct analytics queries against the canonical route', async () => {
    mockApiClient.post.mockResolvedValue({
      queryId: 'query-1',
      queryType: 'ANALYTICS',
      rowCount: 1,
      columnCount: 1,
      rows: [{ total: 42 }],
      executionTimeMs: 18,
      optimized: true,
      timestamp: '2026-04-14T13:00:00Z',
    });

    const result = await executeAnalyticsQuery('SELECT COUNT(*) AS total FROM events', { limit: 10 });

    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/analytics/query',
      {
        query: 'SELECT COUNT(*) AS total FROM events',
        parameters: { limit: 10 },
      },
      { headers: { 'X-Tenant-ID': 'tenant-a' } },
    );
    expect(result.queryId).toBe('query-1');
    expect(result.rows[0]?.total).toBe(42);
  });

  it('executes federated queries against the canonical route', async () => {
    mockApiClient.post.mockResolvedValue({
      queryId: 'query-fed-1',
      queryType: 'FEDERATED_FALLBACK',
      rowCount: 1,
      columnCount: 1,
      rows: [{ region: 'global' }],
      executionTimeMs: 32,
      optimized: true,
      timestamp: '2026-04-14T13:01:00Z',
      warning: 'Trino not configured — query executed via local analytics engine.',
    });

    const result = await executeFederatedQuery('SELECT region FROM global_orders');

    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/queries/federated',
      {
        sql: 'SELECT region FROM global_orders',
        parameters: {},
      },
      { headers: { 'X-Tenant-ID': 'tenant-a' } },
    );
    expect(result.queryType).toBe('FEDERATED_FALLBACK');
  });

  it('explains analytics queries against the canonical explain route', async () => {
    mockApiClient.post.mockResolvedValue({
      queryId: 'query-plan-1',
      queryType: 'AGGREGATE',
      dataSources: ['products'],
      estimatedCost: 128,
      optimized: true,
      explain: true,
      timestamp: '2026-04-14T13:02:00Z',
    });

    const result = await explainAnalyticsQuery('SELECT COUNT(*) FROM products WHERE created_at > NOW() - INTERVAL 7 DAY');

    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/analytics/explain',
      {
        query: 'SELECT COUNT(*) FROM products WHERE created_at > NOW() - INTERVAL 7 DAY',
        parameters: {},
      },
      { headers: { 'X-Tenant-ID': 'tenant-a' } },
    );
    expect(result).toMatchObject({
      queryId: 'query-plan-1',
      queryType: 'AGGREGATE',
      dataSources: ['products'],
      explain: true,
    });
  });

  it('maps canonical analytics suggestions from data.queries', async () => {
    mockApiClient.post.mockResolvedValue({
      data: {
        queries: [
          {
            name: 'Cache repeated revenue queries',
            template: 'SELECT day, SUM(total) FROM revenue GROUP BY day',
            explanation: 'Frequent revenue lookups can be served faster from a cached aggregate.',
          },
        ],
      },
      ai: {
        confidence: 0.88,
        fallback: false,
      },
    });

    const { result } = renderHook(() => useAnalyticsAiSuggestions(), {
      wrapper: TestWrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data).toEqual([
      {
        key: 'analytics-0',
        type: 'optimization',
        title: 'Cache repeated revenue queries',
        description: 'Frequent revenue lookups can be served faster from a cached aggregate.',
        confidence: 0.88,
        reasons: ['SELECT day, SUM(total) FROM revenue GROUP BY day'],
        fallback: false,
      },
    ]);
  });

  it('falls back deterministically when the analytics suggest endpoint fails', async () => {
    mockApiClient.post.mockRejectedValue(new Error('offline'));

    const { result } = renderHook(() => useAnalyticsAiSuggestions(), {
      wrapper: TestWrapper,
    });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));

    expect(result.current.data?.every((item) => item.fallback)).toBe(true);
    expect(result.current.data?.[0]?.key).toBe('fallback-0');
  });

  it('uses backend policy evaluation when policy endpoint is available', async () => {
    mockApiClient.post.mockResolvedValue({
      verdict: 'review',
      confidence: 0.92,
      reasons: ['Cross-source join detected'],
      requiresApproval: true,
    });

    const result = await evaluateQueryPolicy('SELECT * FROM events JOIN users ON users.id = events.user_id');

    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/analytics/policy-evaluate',
      { query: 'SELECT * FROM events JOIN users ON users.id = events.user_id' },
      { headers: { 'X-Tenant-ID': 'tenant-a' } },
    );
    expect(result).toEqual({
      verdict: 'review',
      confidence: 0.92,
      reasons: ['Cross-source join detected'],
      requiresApproval: true,
      source: 'policy-engine',
    });
  });

  it('falls back to deterministic deny for destructive query patterns when policy endpoint fails', async () => {
    mockApiClient.post.mockRejectedValue(new Error('policy endpoint unavailable'));

    const result = await evaluateQueryPolicy('DELETE FROM events WHERE id = 1');

    expect(result.verdict).toBe('deny');
    expect(result.source).toBe('heuristic-fallback');
    expect(result.reasons[0]).toContain('Potentially destructive');
  });
});