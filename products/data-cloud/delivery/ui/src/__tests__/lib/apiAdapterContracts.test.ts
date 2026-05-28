import { beforeEach, describe, expect, it, vi } from 'vitest';

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

import { collectionsApi } from '../../lib/api/collections';
import { workflowsApi } from '../../lib/api/workflows';

describe('frontend adapter contracts', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('collectionsApi', () => {
    it('maps canonical entity-list payloads into the collection UI read model', async () => {
      mockApiClient.get.mockResolvedValue({
        entities: [
          {
            id: 'col-1',
            collection: 'dc_collections',
            data: {
              name: 'Customers',
              description: 'Canonical collection entity',
              schemaType: 'entity',
              status: 'active',
              entityCount: 42,
              schema: { fields: [{ name: 'id', type: 'string', required: true }] },
              tags: ['crm', 'gold'],
              createdBy: 'contract-runner',
            },
            version: 3,
            createdAt: '2026-04-14T10:00:00Z',
            updatedAt: '2026-04-14T10:05:00Z',
          },
        ],
        count: 1,
        tenantId: 'tenant-a',
        timestamp: '2026-04-14T10:06:00Z',
      });

      const response = await collectionsApi.list({ page: 2, pageSize: 25, search: 'Customers' });

      expect(mockApiClient.get).toHaveBeenCalledWith('/entities/dc_collections', {
        params: { limit: 25, offset: 25, search: 'Customers' },
      });
      expect(response).toMatchObject({
        items: [
          {
            id: 'col-1',
            name: 'Customers',
            description: 'Canonical collection entity',
            schemaType: 'entity',
            status: 'active',
            isActive: true,
            entityCount: 42,
            schema: { fields: [{ name: 'id', type: 'string', required: true }] },
            tags: ['crm', 'gold'],
            createdAt: '2026-04-14T10:00:00Z',
            updatedAt: '2026-04-14T10:05:00Z',
            createdBy: 'contract-runner',
            lifecycleStatus: 'UNKNOWN',
            operationalStatus: 'unknown',
            owner: 'contract-runner',
          },
        ],
        total: 1,
        page: 2,
        pageSize: 25,
        hasMore: false,
      });
    });

    it('maps canonical single-entity payloads and preserves fallback defaults', async () => {
      mockApiClient.get.mockResolvedValue({
        id: 'col-2',
        collection: 'dc_collections',
        data: {
          name: 'Signals',
          tags: ['ops'],
        },
      });

      const collection = await collectionsApi.get('col-2');

      expect(collection).toMatchObject({
        id: 'col-2',
        name: 'Signals',
        description: '',
        schemaType: 'entity',
        status: 'draft',
        isActive: false,
        entityCount: 0,
        schema: { fields: [] },
        tags: ['ops'],
        createdBy: 'unknown',
        lifecycleStatus: 'UNKNOWN',
        operationalStatus: 'unknown',
        owner: 'unknown',
      });
      expect(collection.createdAt).toEqual(expect.any(String));
      expect(collection.updatedAt).toEqual(expect.any(String));
    });

    it('create performs backend read-after-write and returns canonical backend fields', async () => {
      mockApiClient.post.mockResolvedValue({
        id: 'col-3',
        collection: 'dc_collections',
        createdAt: '2026-04-14T10:00:00Z',
        timestamp: '2026-04-14T10:00:01Z',
      });
      mockApiClient.get.mockResolvedValue({
        id: 'col-3',
        collection: 'dc_collections',
        data: {
          name: 'Orders',
          description: 'Canonical backend entity after create',
          schemaType: 'entity',
          status: 'active',
          entityCount: 7,
          schema: { fields: [{ name: 'id', type: 'string', required: true }] },
          tags: ['core'],
          createdBy: 'backend-owner',
          lifecycleStatus: 'PUBLISHED',
          operationalStatus: 'healthy',
          owner: 'data-platform',
          qualityScore: 0.91,
        },
        createdAt: '2026-04-14T10:00:00Z',
        updatedAt: '2026-04-14T10:01:00Z',
      });

      const created = await collectionsApi.create({
        name: 'Orders',
        description: 'Create request payload',
        schemaType: 'entity',
        schema: { fields: [{ name: 'id', type: 'string', required: true }] },
        tags: ['core'],
      });

      expect(mockApiClient.post).toHaveBeenCalledWith('/entities/dc_collections', {
        name: 'Orders',
        description: 'Create request payload',
        schemaType: 'entity',
        schema: { fields: [{ name: 'id', type: 'string', required: true }] },
        tags: ['core'],
      });
      expect(mockApiClient.get).toHaveBeenCalledWith('/entities/dc_collections/col-3');
      expect(created).toMatchObject({
        id: 'col-3',
        name: 'Orders',
        status: 'active',
        lifecycleStatus: 'PUBLISHED',
        operationalStatus: 'healthy',
        owner: 'data-platform',
        createdBy: 'backend-owner',
        qualityScore: 0.91,
      });
    });

    it('maps backend collection registry stats fields without UI-only fabrication', async () => {
      mockApiClient.get.mockResolvedValue({
        entities: [
          {
            id: 'col-4',
            collection: 'dc_collections',
            data: {
              name: 'Telemetry',
              description: 'Registry-backed collection',
              schemaType: 'timeseries',
              status: 'active',
              entityCount: 1200,
              schema: { fields: [{ name: 'ts', type: 'timestamp' }] },
              tags: ['ops'],
              createdBy: 'platform',
              lifecycleStatus: 'PUBLISHED',
              operationalStatus: 'healthy',
              owner: 'platform-ops',
              qualityScore: 0.88,
              qualityMetrics: {
                completeness: 0.92,
                timeliness: '0.86',
              },
              storageSizeBytes: '4096',
            },
            createdAt: '2026-04-14T11:00:00Z',
            updatedAt: '2026-04-14T11:30:00Z',
          },
        ],
        count: 1,
        tenantId: 'tenant-a',
        timestamp: '2026-04-14T11:31:00Z',
      });

      const response = await collectionsApi.list({ page: 1, pageSize: 10 });

      expect(response.items[0]).toMatchObject({
        id: 'col-4',
        lifecycleStatus: 'PUBLISHED',
        operationalStatus: 'healthy',
        qualityScore: 0.88,
        qualityMetrics: {
          completeness: 0.92,
          timeliness: 0.86,
        },
        storageSizeBytes: 4096,
      });
    });

    it('rejects invalid enum metadata values at the contract boundary', async () => {
      mockApiClient.get.mockResolvedValue({
        entities: [
          {
            id: 'col-4b',
            collection: 'dc_collections',
            data: {
              name: 'Telemetry Legacy',
              schemaType: 'legacy-entity',
              status: 'legacy-status',
              lifecycleStatus: 'LEGACY',
              operationalStatus: 'broken',
              qualityScore: 'NaN',
              qualityMetrics: {
                completeness: '0.95',
                timeliness: 'n/a',
              },
            },
          },
        ],
      });

      await expect(collectionsApi.list({ page: 1, pageSize: 10 })).rejects.toThrowError(/Invalid option/);
    });

    it('getStats reflects backend collection metadata values', async () => {
      mockApiClient.get.mockResolvedValue({
        id: 'col-5',
        collection: 'dc_collections',
        data: {
          name: 'Logs',
          schema: { fields: [] },
          entityCount: 512,
          storageSizeBytes: 2048,
        },
        createdAt: '2026-04-14T10:00:00Z',
        updatedAt: '2026-04-14T12:00:00Z',
      });

      const stats = await collectionsApi.getStats('col-5');

      expect(stats).toEqual({
        entityCount: 512,
        storageSize: 2048,
        lastUpdated: '2026-04-14T12:00:00Z',
      });
    });
  });

  describe('workflowsApi', () => {
    it('maps canonical pipeline-list payloads into the workflow UI read model', async () => {
      mockApiClient.get.mockResolvedValue({
        tenantId: 'tenant-a',
        pipelines: [
          {
            id: 'wf-1',
            tenantId: 'tenant-a',
            name: 'Daily Sync',
            description: 'Launcher pipeline registry payload',
            status: 'active',
            nodes: [{ id: 'extract', type: 'source', label: 'Extract', position: { x: 0, y: 0 }, data: {} }],
            edges: [{ id: 'edge-1', source: 'extract', target: 'load', label: 'flows-to' }],
            schedule: '0 0 * * *',
            tags: ['daily', 'sync'],
            createdAt: '2026-04-14T08:00:00Z',
            updatedAt: '2026-04-14T09:00:00Z',
            createdBy: 'contract-runner',
            lastExecutedAt: '2026-04-14T09:30:00Z',
          },
        ],
        count: 1,
        timestamp: '2026-04-14T09:31:00Z',
      });

      const response = await workflowsApi.list({ page: 1, pageSize: 10, status: 'active' });

      expect(mockApiClient.get).toHaveBeenCalledWith('/action/pipelines', {
        params: { limit: 10, status: 'active' },
      });
      expect(response).toEqual({
        items: [
          {
            id: 'wf-1',
            name: 'Daily Sync',
            description: 'Launcher pipeline registry payload',
            status: 'active',
            nodes: [{ id: 'extract', type: 'source', label: 'Extract', position: { x: 0, y: 0 }, data: {} }],
            edges: [{ id: 'edge-1', source: 'extract', target: 'load', label: 'flows-to' }],
            schedule: '0 0 * * *',
            tags: ['daily', 'sync'],
            createdAt: '2026-04-14T08:00:00Z',
            updatedAt: '2026-04-14T09:00:00Z',
            createdBy: 'contract-runner',
            lastExecutedAt: '2026-04-14T09:30:00Z',
          },
        ],
        total: 1,
        page: 1,
        pageSize: 10,
        hasMore: false,
      });
    });

    it('maps canonical pipeline payloads returned from create and update routes', async () => {
      mockApiClient.post.mockResolvedValue({
        id: 'wf-2',
        tenantId: 'tenant-a',
        name: 'New Workflow',
        description: 'Saved pipeline',
        status: 'draft',
        nodes: [],
        edges: [],
        tags: ['draft'],
        createdAt: '2026-04-14T10:00:00Z',
        updatedAt: '2026-04-14T10:00:00Z',
        createdBy: 'builder',
      });
      mockApiClient.put.mockResolvedValue({
        id: 'wf-2',
        tenantId: 'tenant-a',
        name: 'New Workflow',
        description: 'Updated pipeline',
        status: 'paused',
        nodes: [],
        edges: [],
        tags: ['draft', 'paused'],
        createdAt: '2026-04-14T10:00:00Z',
        updatedAt: '2026-04-14T10:30:00Z',
        createdBy: 'builder',
      });

      const created = await workflowsApi.create({
        name: 'New Workflow',
        description: 'Saved pipeline',
        nodes: [],
        edges: [],
        tags: ['draft'],
      });
      const updated = await workflowsApi.update('wf-2', { status: 'paused', description: 'Updated pipeline' });

      expect(created.status).toBe('draft');
      expect(created.createdBy).toBe('builder');
      expect(updated.status).toBe('paused');
      expect(updated.description).toBe('Updated pipeline');
      expect(mockApiClient.post).toHaveBeenCalledWith('/action/pipelines', {
        name: 'New Workflow',
        description: 'Saved pipeline',
        nodes: [],
        edges: [],
        tags: ['draft'],
      });
      expect(mockApiClient.put).toHaveBeenCalledWith('/action/pipelines/wf-2', {
        status: 'paused',
        description: 'Updated pipeline',
      });
    });

    it('routes workflow execution through the launcher api', async () => {
      mockApiClient.post.mockResolvedValueOnce({
        executionId: 'exec-1',
        workflowId: 'wf-1',
        status: 'completed',
        startedAt: '2026-04-16T10:00:00Z',
      });

      const execution = await workflowsApi.execute('wf-1', { input: { dryRun: true } });

      expect(mockApiClient.post).toHaveBeenCalledWith('/action/pipelines/wf-1/execute', {
        input: { dryRun: true },
      });
      expect(execution).toMatchObject({
        id: 'exec-1',
        workflowId: 'wf-1',
        status: 'completed',
      });
    });
  });
});