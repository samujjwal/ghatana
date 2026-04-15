import { beforeEach, describe, expect, it, vi } from 'vitest';

const { mockApiClient, mockCollectionsApi } = vi.hoisted(() => ({
  mockApiClient: {
    get: vi.fn(),
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

import { qualityService } from '@/api/quality.service';

describe('qualityService contract mapping', () => {
  beforeEach(() => {
    vi.clearAllMocks();

    mockCollectionsApi.list.mockResolvedValue({
      items: [
        {
          id: 'orders',
          name: 'Orders',
          description: 'Canonical collection metadata',
          schemaType: 'entity',
          status: 'active',
          isActive: true,
          entityCount: 120,
          schema: {
            fields: [
              { id: 'field-1', name: 'customerEmail', type: 'string', required: true },
              { id: 'field-2', name: 'orderTotal', type: 'number', required: true },
            ],
          },
          tags: ['sales'],
          createdAt: '2026-04-15T07:00:00Z',
          updatedAt: '2026-04-15T07:05:00Z',
          createdBy: 'contract-runner',
        },
      ],
      total: 1,
      page: 1,
      pageSize: 50,
      hasMore: false,
    });

    mockApiClient.get.mockResolvedValue({
      data: {
        globalFields: ['email'],
        tenantFields: ['customer'],
        effectiveCount: 2,
      },
      meta: {
        tenantId: 'tenant-a',
        requestId: 'req-1',
        timestamp: '2026-04-15T07:06:00Z',
        apiVersion: 'v1',
      },
    });
  });

  it('derives quality metrics from canonical collection metadata', async () => {
    const metrics = await qualityService.getQualityMetrics();

    expect(mockCollectionsApi.list).toHaveBeenCalledWith({ pageSize: 50 });
    expect(metrics).toHaveLength(1);
    expect(metrics[0]).toMatchObject({
      datasetId: 'orders',
      datasetName: 'Orders',
      lastChecked: '2026-04-15T07:05:00Z',
    });
  });

  it('uses the canonical pii-fields envelope to derive dataset detections', async () => {
    const detections = await qualityService.getPIIDetections('orders');

    expect(mockApiClient.get).toHaveBeenCalledWith('/governance/privacy/pii-fields');
    expect(detections).toEqual([
      {
        datasetId: 'orders',
        fieldName: 'customerEmail',
        piiType: 'EMAIL',
        confidence: 0.82,
        sampleCount: 120,
        masked: false,
        detectedAt: '2026-04-15T07:05:00Z',
      },
    ]);
  });

  it('fails explicitly for unsupported validation-rule and masking operations', async () => {
    await expect(qualityService.maskPII('orders', ['customerEmail'])).rejects.toThrow(/Bulk field masking is not exposed/i);
    await expect(qualityService.createValidationRule({ name: 'Required Email' })).rejects.toThrow(/Validation rule creation is not exposed/i);
    await expect(qualityService.updateValidationRule('rule-1', { enabled: false })).rejects.toThrow(/Validation rule updates are not exposed/i);
    await expect(qualityService.deleteValidationRule('rule-1')).rejects.toThrow(/Validation rule deletion is not exposed/i);
  });
});