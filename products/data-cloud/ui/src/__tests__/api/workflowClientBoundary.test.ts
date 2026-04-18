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
  WORKFLOW_CONTEXT_BOUNDARY_MESSAGE,
  WORKFLOW_CLIENT_BOUNDARY_MESSAGE,
  workflowClient,
} from '../../lib/api/workflow-client';
import SessionBootstrap from '../../lib/auth/session';
import { TEST_TENANT_ID } from '@/__tests__/test-utils/tenants';

describe('workflowClient', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    SessionBootstrap.setTenantId(TEST_TENANT_ID);
  });

  it('maps canonical pipeline list payloads into workflow definitions', async () => {
    mockApiClient.get.mockResolvedValueOnce({
      tenantId: TEST_TENANT_ID,
      pipelines: [
        {
          id: 'wf-1',
          tenantId: TEST_TENANT_ID,
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
          tenantId: TEST_TENANT_ID,
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
      tenantId: TEST_TENANT_ID,
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
      tenantId: TEST_TENANT_ID,
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
      tenantId: TEST_TENANT_ID,
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

  it('executes workflows through the launcher api', async () => {
    mockApiClient.post.mockResolvedValueOnce({
      executionId: 'exec-1',
      workflowId: 'wf-1',
      status: 'COMPLETED',
    });

    const response = await workflowClient.executeWorkflow('wf-1');

    expect(mockApiClient.post).toHaveBeenCalledWith(
      '/pipelines/wf-1/execute',
      {},
      expect.objectContaining({ headers: expect.any(Object) }),
    );
    expect(response).toEqual({
      executionId: 'exec-1',
      workflowId: 'wf-1',
      status: 'COMPLETED',
    });
  });

  it('keeps unsupported convenience endpoints explicit while using launcher execution detail routes', async () => {
    mockApiClient.get.mockResolvedValueOnce({
      id: 'exec-1',
      pipelineId: 'wf-1',
      status: 'completed',
      startTime: '2026-04-16T10:00:00Z',
      endTime: '2026-04-16T10:00:01Z',
      completedNodes: 2,
      totalNodes: 2,
      nodes: [
        { id: 'node-1', name: 'Start', status: 'completed', startTime: '2026-04-16T10:00:00Z', endTime: '2026-04-16T10:00:00Z' },
        { id: 'node-2', name: 'End', status: 'completed', startTime: '2026-04-16T10:00:00Z', endTime: '2026-04-16T10:00:01Z' },
      ],
    });

    await expect(workflowClient.getTemplates()).rejects.toThrow(WORKFLOW_CLIENT_BOUNDARY_MESSAGE);
    await expect(workflowClient.getSuggestions('orders')).rejects.toThrow(WORKFLOW_CLIENT_BOUNDARY_MESSAGE);

    const execution = await workflowClient.getExecutionStatus('exec-1');
    expect(mockApiClient.get).toHaveBeenCalledWith('/executions/exec-1', {
      headers: expect.any(Object),
    });
    expect(execution).toMatchObject({
      id: 'exec-1',
      workflowId: 'wf-1',
      progress: 100,
    });

    mockApiClient.post.mockResolvedValueOnce({ ok: true });
    await expect(workflowClient.cancelExecution('exec-1')).resolves.toBeUndefined();
    expect(mockApiClient.post).toHaveBeenCalledWith('/executions/exec-1/cancel', {}, {
      headers: expect.any(Object),
    });
  });

  it('fails explicitly when workflow payloads do not include collection context', async () => {
    mockApiClient.get.mockResolvedValueOnce({
      id: 'wf-missing',
      tenantId: TEST_TENANT_ID,
      name: 'Missing Collection Workflow',
      status: 'draft',
      nodes: [],
      edges: [],
      createdAt: '2026-04-15T12:10:00Z',
      updatedAt: '2026-04-15T12:11:00Z',
      createdBy: 'builder',
    });

    await expect(workflowClient.getWorkflow('wf-missing')).rejects.toThrow(WORKFLOW_CONTEXT_BOUNDARY_MESSAGE);
  });
});