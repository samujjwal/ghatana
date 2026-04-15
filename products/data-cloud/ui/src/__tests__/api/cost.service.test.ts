import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockApiClient, mockCollectionsApi } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
    post: vi.fn(),
  },
  mockCollectionsApi: {
    list: vi.fn(),
  },
}));

vi.mock('@/lib/api/client', () => ({
  apiClient: mockApiClient,
}));

vi.mock('@/lib/api/collections', () => ({
  collectionsApi: mockCollectionsApi,
}));

import { costService, migrateCollection } from '@/api/cost.service';

describe('costService contract mapping', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockCollectionsApi.list.mockResolvedValue({
      items: [
        {
          id: 'orders',
          name: 'Orders',
          description: 'Orders collection',
          schemaType: 'entity',
          status: 'active',
          isActive: true,
          entityCount: 100,
          schema: { fields: [{ name: 'id', type: 'string', required: true }] },
          tags: ['sales'],
          createdAt: '2026-04-15T08:00:00Z',
          updatedAt: '2026-04-15T08:10:00Z',
          createdBy: 'contract-runner',
        },
      ],
      total: 1,
      page: 1,
      pageSize: 50,
      hasMore: false,
    });

    mockApiClient.get.mockResolvedValue({
      collectionId: 'orders',
      tenantId: 'tenant-a',
      totalSizeGb: 12,
      totalCostDccPerDay: 24,
      currency: 'DCC',
      tiers: [
        { tier: 'HOT', sizeGb: 12, costDccPerDay: 24, backend: 'postgres' },
      ],
      note: 'Derived from canonical launcher cost report',
    });

    mockApiClient.post.mockResolvedValue({
      collection: 'orders',
      targetTier: 'WARM',
      status: 'SCHEDULED',
      eventsMigrated: 100,
    });
  });

  it('derives aggregate cost analysis from canonical collection cost reports', async () => {
    const analysis = await costService.getCostAnalysis('30d');

    expect(mockCollectionsApi.list).toHaveBeenCalledWith({ pageSize: 50 });
    expect(mockApiClient.get).toHaveBeenCalledWith('/collections/orders/cost-report');
    expect(analysis).toMatchObject({
      total: 24,
      currency: 'DCC',
      period: '30d',
      byDataset: [
        {
          datasetId: 'orders',
          datasetName: 'Orders',
          cost: 24,
          percentage: 100,
        },
      ],
    });
  });

  it('derives hotness metrics from the canonical cost analysis view', async () => {
    const metrics = await costService.getHotnessMetrics();

    expect(metrics).toHaveLength(1);
    expect(metrics[0]).toMatchObject({
      datasetId: 'orders',
      tier: 'HOT',
      predictedTier: 'HOT',
    });
  });

  it('uses the canonical collection migrate route', async () => {
    const result = await migrateCollection('orders', 'WARM');

    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/collections/orders/migrate',
      {},
      { params: { targetTier: 'WARM' } },
    );
    expect(result).toEqual({
      collection: 'orders',
      targetTier: 'WARM',
      status: 'SCHEDULED',
      eventsMigrated: 100,
    });
  });
});