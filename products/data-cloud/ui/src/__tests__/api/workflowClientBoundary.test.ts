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