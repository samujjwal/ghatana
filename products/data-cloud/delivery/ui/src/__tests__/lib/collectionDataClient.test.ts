import { beforeEach, describe, expect, it, vi } from 'vitest';
import { TEST_TENANT_ID } from '@/__tests__/test-utils/tenants';

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

import { collectionDataClient } from '../../lib/api/collection-data-client';

describe('collection-data client', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('parses create, get, and update record responses', async () => {
    const record = {
      id: 'record-1',
      collectionId: 'orders',
      tenantId: TEST_TENANT_ID,
      data: { id: '1', total: 42 },
      createdAt: '2026-04-15T13:00:00Z',
      updatedAt: '2026-04-15T13:01:00Z',
      createdBy: 'builder',
      updatedBy: 'builder',
      version: 1,
    };
    mockApiClient.post.mockResolvedValueOnce(record);
    mockApiClient.get.mockResolvedValueOnce(record);
    mockApiClient.put.mockResolvedValueOnce({ ...record, version: 2, updatedBy: 'editor' });

    const created = await collectionDataClient.createRecord(TEST_TENANT_ID, 'orders', { data: { id: '1', total: 42 } });
    const loaded = await collectionDataClient.getRecord(TEST_TENANT_ID, 'orders', 'record-1');
    const updated = await collectionDataClient.updateRecord(TEST_TENANT_ID, 'orders', 'record-1', { data: { id: '1', total: 43 } });

    expect(created.version).toBe(1);
    expect(loaded.id).toBe('record-1');
    expect(updated.version).toBe(2);
    expect(updated.updatedBy).toBe('editor');
  });

  it('parses list and bulk responses', async () => {
    mockApiClient.get.mockResolvedValueOnce({
      items: [
        {
          id: 'record-1',
          collectionId: 'orders',
          tenantId: TEST_TENANT_ID,
          data: { id: '1' },
          createdAt: '2026-04-15T13:00:00Z',
          updatedAt: '2026-04-15T13:00:00Z',
          createdBy: 'builder',
          updatedBy: 'builder',
          version: 1,
        },
      ],
      total: 1,
      offset: 0,
      limit: 20,
    });
    mockApiClient.post.mockResolvedValueOnce({
      successful: [
        {
          id: 'record-2',
          collectionId: 'orders',
          tenantId: TEST_TENANT_ID,
          data: { id: '2' },
          createdAt: '2026-04-15T13:02:00Z',
          updatedAt: '2026-04-15T13:02:00Z',
          createdBy: 'builder',
          updatedBy: 'builder',
          version: 1,
        },
      ],
      failed: [{ index: 1, error: 'duplicate id' }],
    });
    mockApiClient.post.mockResolvedValueOnce({ deleted: 2 });

    const listed = await collectionDataClient.listRecords(TEST_TENANT_ID, 'orders', { limit: 20, offset: 0, search: '1' });
    const bulkCreated = await collectionDataClient.bulkCreateRecords(TEST_TENANT_ID, 'orders', { records: [{ id: '2' }] });
    const deletedCount = await collectionDataClient.bulkDeleteRecords(TEST_TENANT_ID, 'orders', ['record-1', 'record-2']);

    expect(listed.total).toBe(1);
    expect(bulkCreated.successful).toHaveLength(1);
    expect(bulkCreated.failed[0]?.error).toBe('duplicate id');
    expect(deletedCount).toBe(2);
  });
});