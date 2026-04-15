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

import {
  WORKFLOW_CLIENT_BOUNDARY_MESSAGE,
  workflowClient,
} from '../../lib/api/workflow-client';

describe('workflowClient', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('maps canonical pipeline list payloads into workflow definitions', async () => {
    mockApiClient.get.mockResolvedValueOnce({
      tenantId: 'tenant-a',
      pipelines: [
        {
          id: 'wf-1',
          tenantId: 'tenant-a',
          collectionId: 'orders',
          name: 'Orders Workflow',
          description: 'Canonical pipeline payload',
          status: 'active',
          nodes: [],
          edges: [],
          tags: ['sales'],
          createdAt: '2026-04-15T12:00:00Z',
          updatedAt: '2026-04-15T12:05:00Z',
          createdBy: 'builder',
          updatedBy: 'builder',
          version: 3,
          variables: { region: 'us-east-1' },
          triggers: ['manual'],
        },
      ],
      count: 1,
      timestamp: '2026-04-15T12:05:00Z',
    });

    const response = await workflowClient.listWorkflows('orders', 0, 25);

    expect(mockApiClient.get).toHaveBeenCalledWith('/pipelines', {
      params: { collectionId: 'orders', limit: 25 },
      headers: expect.any(Object),
    });
    expect(response).toMatchObject({
      total: 1,
      page: 0,
      pageSize: 25,
      workflows: [
        {
          id: 'wf-1',
          tenantId: 'tenant-a',
          collectionId: 'orders',
          status: 'PUBLISHED',
          active: true,
          version: 3,
        },
      ],
    });
  });

  it('maps canonical pipeline detail payloads into workflow definitions', async () => {
    mockApiClient.get.mockResolvedValueOnce({
      id: 'wf-2',
      tenantId: 'tenant-a',
      collectionId: 'returns',
      name: 'Returns Workflow',
      description: 'Pipeline detail',
      status: 'draft',
      nodes: [],
      edges: [],
      createdAt: '2026-04-15T12:10:00Z',
      updatedAt: '2026-04-15T12:11:00Z',
      createdBy: 'builder',
    });

    const response = await workflowClient.getWorkflow('wf-2');

    expect(mockApiClient.get).toHaveBeenCalledWith('/pipelines/wf-2', {
      headers: expect.any(Object),
    });
    expect(response).toMatchObject({
      id: 'wf-2',
      collectionId: 'returns',
      status: 'DRAFT',
      active: false,
    });
  });

  it('maps canonical create and update pipeline payloads into workflow definitions', async () => {
    mockApiClient.post.mockResolvedValueOnce({
      id: 'wf-3',
      tenantId: 'tenant-a',
      collectionId: 'orders',
      name: 'Created Workflow',
      description: 'Created from pipeline response',
      status: 'draft',
      nodes: [],
      edges: [],
      createdAt: '2026-04-15T12:12:00Z',
      updatedAt: '2026-04-15T12:12:00Z',
      createdBy: 'builder',
    });
    mockApiClient.put.mockResolvedValueOnce({
      id: 'wf-3',
      tenantId: 'tenant-a',
      collectionId: 'orders',
      name: 'Updated Workflow',
      description: 'Updated from pipeline response',
      status: 'active',
      nodes: [],
      edges: [],
      createdAt: '2026-04-15T12:12:00Z',
      updatedAt: '2026-04-15T12:14:00Z',
      createdBy: 'builder',
      updatedBy: 'reviewer',
    });

    const created = await workflowClient.createWorkflow({
      name: 'Created Workflow',
      description: 'Created from UI request',
      collectionId: 'orders',
      nodes: [],
      edges: [],
    });
    const updated = await workflowClient.updateWorkflow('wf-3', {
      name: 'Updated Workflow',
      isPublished: true,
    });

    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/pipelines',
      {
        name: 'Created Workflow',
        description: 'Created from UI request',
        collectionId: 'orders',
        nodes: [],
        edges: [],
      },
      expect.objectContaining({ headers: expect.any(Object) }),
    );
    expect(mockApiClient.put).toHaveBeenCalledWith(
      '/pipelines/wf-3',
      { name: 'Updated Workflow', isPublished: true },
      expect.objectContaining({ headers: expect.any(Object) }),
    );
    expect(created).toMatchObject({
      id: 'wf-3',
      collectionId: 'orders',
      status: 'DRAFT',
    });
    expect(updated).toMatchObject({
      id: 'wf-3',
      status: 'PUBLISHED',
      active: true,
      updatedBy: 'reviewer',
    });
  });

  it('still executes workflows via the canonical pipeline execute route', async () => {
    mockApiClient.post.mockResolvedValueOnce({
      executionId: 'exec-1',
      workflowId: 'wf-1',
      status: 'RUNNING',
    });

    const response = await workflowClient.executeWorkflow('wf-1');

    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/pipelines/wf-1/execute',
      {},
      expect.objectContaining({ headers: expect.any(Object) }),
    );
    expect(response.executionId).toBe('exec-1');
  });

  it('fails explicitly for unsupported workflow convenience endpoints', async () => {
    await expect(workflowClient.getTemplates()).rejects.toThrow(WORKFLOW_CLIENT_BOUNDARY_MESSAGE);
    await expect(workflowClient.getSuggestions('orders')).rejects.toThrow(WORKFLOW_CLIENT_BOUNDARY_MESSAGE);
    await expect(workflowClient.getExecutionStatus('exec-1')).rejects.toThrow(WORKFLOW_CLIENT_BOUNDARY_MESSAGE);
    await expect(workflowClient.cancelExecution('exec-1')).rejects.toThrow(WORKFLOW_CLIENT_BOUNDARY_MESSAGE);
  });
});